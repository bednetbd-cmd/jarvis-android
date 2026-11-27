package com.jarvis.assistant.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.AppContainer
import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.device.NotificationBufferStore
import com.jarvis.assistant.core.memory.ConversationMemoryPort
import com.jarvis.assistant.core.productivity.ProductivityPort
import com.jarvis.assistant.core.security.BiometricAuthPort
import com.jarvis.assistant.core.security.BiometricCapability
import com.jarvis.assistant.core.security.PendingSensitiveAction
import com.jarvis.assistant.core.security.SensitiveActionDetector
import com.jarvis.assistant.core.security.SensitiveActionType
import com.jarvis.assistant.core.tools.ClipboardPort
import com.jarvis.assistant.core.tools.ContextualSuggestionEngine
import com.jarvis.assistant.core.tools.WorkspaceDocumentsPort
import com.jarvis.assistant.core.voice.InMemoryVoiceMonitorController
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.core.voice.VoiceCommandParser
import com.jarvis.assistant.core.voice.VoiceDiagnostics
import com.jarvis.assistant.core.voice.VoiceMonitorMode
import com.jarvis.assistant.core.voice.VoiceMonitorPort
import com.jarvis.assistant.core.voice.VoiceMonitorStatus
import com.jarvis.assistant.core.vision.VisionAnalysisPort
import com.jarvis.assistant.core.vision.VisionScanMode
import com.jarvis.assistant.core.vision.VisionScanOutput
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.preferences.JarvisPreferencesPort
import com.jarvis.assistant.data.security.AndroidKeystoreVault
import com.jarvis.assistant.data.security.InMemorySecureVault
import com.jarvis.assistant.data.security.SecureVaultPort
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.AssistantPersonality
import com.jarvis.assistant.domain.model.AssistantVisualState
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.JarvisAccentTheme
import com.jarvis.assistant.domain.model.JarvisPreferences
import com.jarvis.assistant.domain.model.MessageSender
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class AiTestMeta(
    val isTesting: Boolean = false,
    val status: String? = null,
    val success: Boolean? = null
)

private data class TokenPresence(
    val hasOpenAiToken: Boolean = false,
    val hasGeminiToken: Boolean = false
)

private data class VoiceUiMeta(
    val visualState: AssistantVisualState,
    val isListening: Boolean,
    val isSpeaking: Boolean,
    val rmsLevel: Float,
    val partialTranscript: String,
    val diagnostics: VoiceDiagnostics,
    val monitorStatus: VoiceMonitorStatus
)

class MainViewModel(
    private val aiAssistant: AiAssistantPort,
    private val speechRecognizer: SpeechRecognizerPort,
    private val textToSpeech: TextToSpeechPort,
    private val conversationMemory: ConversationMemoryPort,
    private val preferencesRepository: JarvisPreferencesPort,
    private val telemetryProvider: SystemTelemetryPort,
    private val secureVault: SecureVaultPort = InMemorySecureVault(),
    private val voiceMonitor: VoiceMonitorPort = InMemoryVoiceMonitorController(),
    val visionAnalyzer: VisionAnalysisPort? = null,
    val productivity: ProductivityPort? = null,
    val clipboardPort: ClipboardPort? = null,
    val workspaceDocs: WorkspaceDocumentsPort? = null,
    val biometricAuth: BiometricAuthPort? = null
) : ViewModel() {

    private val inputText = MutableStateFlow("")
    private val memorySearchQuery = MutableStateFlow("")
    private val isProcessing = MutableStateFlow(false)
    private val aiTestMeta = MutableStateFlow(AiTestMeta())
    private val tokenPresence = MutableStateFlow(readTokenPresence())
    private val isAppLocked = MutableStateFlow(false)
    private val pendingSensitiveAction = MutableStateFlow<PendingSensitiveAction?>(null)
    private var activeProcessingJob: Job? = null
    private var bootLockEvaluated = false
    private val cachedBiometricCapability: BiometricCapability by lazy {
        biometricAuth?.checkCapability() ?: BiometricCapability.UNAVAILABLE
    }

    private val _noticeEvents = Channel<String>(Channel.BUFFERED)
    val noticeEvents = _noticeEvents.receiveAsFlow()

    private val _openVisionEvents = Channel<VisionScanMode>(Channel.BUFFERED)
    val openVisionEvents = _openVisionEvents.receiveAsFlow()

    private val _openWorkbenchEvents = Channel<WorkbenchTab>(Channel.BUFFERED)
    val openWorkbenchEvents = _openWorkbenchEvents.receiveAsFlow()

    private val preferencesFlow = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = JarvisPreferences()
    )

    private val effectivePreferencesFlow = combine(
        preferencesFlow,
        tokenPresence
    ) { prefs, tokens ->
        prefs.copy(
            aiProviderConfig = prefs.aiProviderConfig.copy(
                hasOpenAiToken = tokens.hasOpenAiToken,
                hasGeminiToken = tokens.hasGeminiToken
            )
        )
    }

    private val telemetryFlow = telemetryProvider.telemetry.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = telemetryProvider.currentSnapshot()
    )

    private val visualStateFlow = combine(
        speechRecognizer.isListening,
        textToSpeech.isSpeaking,
        isProcessing,
        voiceMonitor.status
    ) { listening, speaking, processing, monitor ->
        when {
            listening || monitor.awaitingCommand -> AssistantVisualState.LISTENING
            processing -> AssistantVisualState.THINKING
            speaking -> AssistantVisualState.SPEAKING
            else -> AssistantVisualState.STANDBY
        }
    }

    private val voiceSignalsFlow = combine(
        speechRecognizer.partialTranscript,
        textToSpeech.languageAvailability,
        voiceMonitor.status
    ) { partial, langAvail, monitor ->
        Triple(partial, langAvail, monitor)
    }

    private val voiceMetaFlow = combine(
        visualStateFlow,
        speechRecognizer.isListening,
        textToSpeech.isSpeaking,
        speechRecognizer.rmsLevel,
        voiceSignalsFlow
    ) { visualState, listening, speaking, rms, signals ->
        VoiceUiMeta(
            visualState = visualState,
            isListening = listening,
            isSpeaking = speaking,
            rmsLevel = rms,
            partialTranscript = signals.first,
            diagnostics = VoiceDiagnostics(
                speechRecognitionAvailable = speechRecognizer.isAvailable,
                onDeviceRecognitionAvailable = speechRecognizer.isOnDeviceAvailable,
                ttsReady = textToSpeech.isReady,
                activeLanguageAvailability = signals.second
            ),
            monitorStatus = signals.third
        )
    }

    private val productivityCountsFlow = if (productivity != null) {
        combine(
            productivity.tasks,
            productivity.reminders,
            productivity.habits
        ) { tasks, reminders, habits ->
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            Triple(
                tasks.count { !it.isDone },
                reminders.count { !it.isCompleted },
                habits.count { it.lastCompletedDay != today }
            )
        }
    } else {
        flowOf(Triple(0, 0, 0))
    }

    private val suggestionsFlow = combine(
        telemetryFlow,
        productivityCountsFlow
    ) { telemetry, counts ->
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val unreadCount = runCatching { NotificationBufferStore.recent().take(4).size }.getOrDefault(0)
        ContextualSuggestionEngine.generate(
            telemetry = telemetry,
            hourOfDay = hour,
            pendingTaskCount = counts.first,
            activeReminderCount = counts.second,
            pendingHabitCount = counts.third,
            unreadNotificationCount = unreadCount
        )
    }

    private val securityMetaFlow = combine(
        isAppLocked,
        pendingSensitiveAction,
        suggestionsFlow
    ) { locked, pendingAction, suggestions ->
        Triple(locked, pendingAction, suggestions)
    }

    private val configAndTelemetryFlow = combine(
        effectivePreferencesFlow,
        telemetryFlow,
        aiTestMeta,
        securityMetaFlow
    ) { prefs, telemetry, testState, secMeta ->
        listOf(prefs, telemetry, testState, secMeta)
    }

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<MainUiState> = combine(
        voiceMetaFlow,
        conversationMemory.messages,
        inputText,
        memorySearchQuery,
        configAndTelemetryFlow
    ) { voiceMeta, messages, draft, searchQuery, packed ->
        val prefs = packed[0] as JarvisPreferences
        val telemetry = packed[1] as com.jarvis.assistant.domain.model.DeviceTelemetry
        val testState = packed[2] as AiTestMeta
        val secMeta = packed[3] as Triple<Boolean, PendingSensitiveAction?, List<com.jarvis.assistant.core.tools.ContextualSuggestion>>

        val activeProvider = prefs.aiProviderConfig.activeProvider
        val hasRequiredCredentials = when (activeProvider) {
            AiProviderType.ON_DEVICE, AiProviderType.LOCAL_OLLAMA -> true
            AiProviderType.OPENAI_COMPATIBLE ->
                prefs.aiProviderConfig.hasOpenAiToken ||
                    prefs.aiProviderConfig.openAiBaseUrl.contains("127.0.0.1") ||
                    prefs.aiProviderConfig.openAiBaseUrl.contains("localhost")
            AiProviderType.GEMINI_REST -> prefs.aiProviderConfig.hasGeminiToken
        }

        MainUiState(
            visualState = voiceMeta.visualState,
            messages = messages,
            inputText = draft,
            memorySearchQuery = searchQuery,
            isListening = voiceMeta.isListening,
            isSpeaking = voiceMeta.isSpeaking,
            rmsLevel = voiceMeta.rmsLevel,
            partialTranscript = voiceMeta.partialTranscript,
            preferences = prefs,
            telemetry = telemetry,
            isAiConnected = aiAssistant.isConfigured && hasRequiredCredentials,
            isVoiceAvailable = speechRecognizer.isAvailable,
            voiceDiagnostics = voiceMeta.diagnostics,
            voiceMonitorStatus = voiceMeta.monitorStatus,
            aiEngineLabel = activeProvider.displayName,
            isTestingAi = testState.isTesting,
            aiTestStatus = testState.status,
            aiTestSuccess = testState.success,
            isAppLocked = secMeta.first,
            biometricCapability = cachedBiometricCapability,
            pendingSensitiveAction = secMeta.second,
            contextualSuggestions = secMeta.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            preferences = preferencesFlow.value,
            telemetry = telemetryFlow.value,
            isAiConnected = aiAssistant.isConfigured,
            isVoiceAvailable = speechRecognizer.isAvailable,
            aiEngineLabel = preferencesFlow.value.aiProviderConfig.activeProvider.displayName,
            biometricCapability = cachedBiometricCapability
        )
    )

    init {
        viewModelScope.launch {
            preferencesFlow.collect { prefs ->
                textToSpeech.updateSettings(
                    speechRate = prefs.speechRate,
                    pitch = prefs.speechPitch,
                    languageTag = prefs.speechLanguageTag
                )
                conversationMemory.setEncryptionEnabled(prefs.encryptConversationHistory)
                if (!bootLockEvaluated) {
                    bootLockEvaluated = true
                    if (prefs.biometricLockEnabled &&
                        biometricAuth?.checkCapability() == BiometricCapability.AVAILABLE
                    ) {
                        isAppLocked.value = true
                    }
                }
            }
        }
    }

    private fun readTokenPresence(): TokenPresence {
        return TokenPresence(
            hasOpenAiToken = secureVault.hasSecret(AndroidKeystoreVault.KEY_OPENAI_TOKEN),
            hasGeminiToken = secureVault.hasSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN)
        )
    }

    fun onInputChanged(value: String) {
        inputText.value = value
    }

    fun onMemorySearchChanged(query: String) {
        memorySearchQuery.value = query
    }

    fun toggleTts() {
        setTtsEnabled(!preferencesFlow.value.ttsEnabled)
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTtsEnabled(enabled)
        }
        if (!enabled) {
            textToSpeech.stop()
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            preferencesRepository.setSpeechRate(rate)
        }
    }

    fun setSpeechPitch(pitch: Float) {
        viewModelScope.launch {
            preferencesRepository.setSpeechPitch(pitch)
        }
    }

    fun setSpeechLanguageTag(tag: String) {
        viewModelScope.launch {
            preferencesRepository.setSpeechLanguageTag(tag)
        }
    }

    fun setPreferOfflineVoice(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPreferOfflineVoice(enabled)
        }
    }

    fun setVoiceMonitorMode(mode: VoiceMonitorMode) {
        if (mode == VoiceMonitorMode.OFF) {
            voiceMonitor.stopMonitoring()
            showNotice("Hands-free voice mode stopped.")
            return
        }

        if (!speechRecognizer.isAvailable) {
            showNotice("Speech recognition service is unavailable on this device.")
            return
        }

        textToSpeech.stop()
        speechRecognizer.stopListening()
        voiceMonitor.startMode(mode)
            .onSuccess {
                showNotice("${mode.displayName} active.")
            }
            .onFailure { error ->
                showNotice(error.message ?: "Unable to start hands-free voice monitor.")
            }
    }

    fun setAccentTheme(theme: JarvisAccentTheme) {
        viewModelScope.launch {
            preferencesRepository.setAccentTheme(theme)
        }
    }

    fun setShowWaveform(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowWaveform(enabled)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        if (enabled && biometricAuth != null && biometricAuth.checkCapability() != BiometricCapability.AVAILABLE) {
            showNotice(biometricAuth.checkCapability().label)
            return
        }
        viewModelScope.launch {
            preferencesRepository.setBiometricLockEnabled(enabled)
            showNotice(
                if (enabled) "Biometric lock enabled for app startup." else "Biometric lock disabled."
            )
        }
    }

    fun setConfirmSensitiveActions(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setConfirmSensitiveActions(enabled)
        }
    }

    fun setEncryptConversationHistory(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setEncryptConversationHistory(enabled)
            conversationMemory.setEncryptionEnabled(enabled)
            showNotice(
                if (enabled) "Private history encrypted with Android Keystore AES-256-GCM."
                else "Local history encryption disabled."
            )
        }
    }

    fun lockAppNow() {
        val cap = biometricAuth?.checkCapability() ?: BiometricCapability.UNAVAILABLE
        if (cap != BiometricCapability.AVAILABLE) {
            showNotice(cap.label)
            return
        }
        isAppLocked.value = true
    }

    fun unlockApp() {
        isAppLocked.value = false
        showNotice("JARVIS Vault unlocked.")
    }

    fun setActiveAiProvider(provider: AiProviderType) {
        aiTestMeta.value = AiTestMeta()
        viewModelScope.launch {
            preferencesRepository.setActiveAiProvider(provider)
        }
    }

    fun setAssistantPersonality(personality: AssistantPersonality) {
        viewModelScope.launch {
            preferencesRepository.setAssistantPersonality(personality)
        }
    }

    fun saveOllamaConfig(baseUrl: String, model: String) {
        viewModelScope.launch {
            preferencesRepository.setOllamaConfig(baseUrl, model)
            aiTestMeta.value = AiTestMeta()
            _noticeEvents.send("Ollama endpoint settings saved.")
        }
    }

    fun saveOpenAiConfig(baseUrl: String, model: String) {
        viewModelScope.launch {
            preferencesRepository.setOpenAiConfig(baseUrl, model)
            aiTestMeta.value = AiTestMeta()
            _noticeEvents.send("OpenAI-compatible endpoint settings saved.")
        }
    }

    fun saveGeminiModel(model: String) {
        viewModelScope.launch {
            preferencesRepository.setGeminiModel(model)
            aiTestMeta.value = AiTestMeta()
            _noticeEvents.send("Gemini model updated.")
        }
    }

    fun saveProviderToken(provider: AiProviderType, token: String) {
        val key = when (provider) {
            AiProviderType.OPENAI_COMPATIBLE -> AndroidKeystoreVault.KEY_OPENAI_TOKEN
            AiProviderType.GEMINI_REST -> AndroidKeystoreVault.KEY_GEMINI_TOKEN
            else -> return
        }
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            showNotice("Enter a valid API key before saving.")
            return
        }
        runCatching {
            secureVault.saveSecret(key, trimmed)
        }.onSuccess {
            tokenPresence.value = readTokenPresence()
            aiTestMeta.value = AiTestMeta()
            showNotice("API key encrypted and saved in Android Keystore.")
        }.onFailure { error ->
            showNotice(error.message ?: "Unable to save key in Android Keystore.")
        }
    }

    fun clearProviderToken(provider: AiProviderType) {
        val key = when (provider) {
            AiProviderType.OPENAI_COMPATIBLE -> AndroidKeystoreVault.KEY_OPENAI_TOKEN
            AiProviderType.GEMINI_REST -> AndroidKeystoreVault.KEY_GEMINI_TOKEN
            else -> return
        }
        secureVault.clearSecret(key)
        tokenPresence.value = readTokenPresence()
        aiTestMeta.value = AiTestMeta()
        showNotice("Stored API key cleared.")
    }

    fun testAiProviderConnection() {
        viewModelScope.launch {
            aiTestMeta.value = AiTestMeta(isTesting = true, status = "Testing connection...", success = null)
            val result = aiAssistant.testActiveConnection()
            result.onSuccess { message ->
                aiTestMeta.value = AiTestMeta(isTesting = false, status = message, success = true)
            }.onFailure { error ->
                aiTestMeta.value = AiTestMeta(
                    isTesting = false,
                    status = error.message ?: "Connection check failed.",
                    success = false
                )
            }
        }
    }

    fun speakTestSample() {
        if (!textToSpeech.isReady) {
            showNotice("Android Text-to-Speech engine is still initializing or unavailable.")
            return
        }
        val prefs = preferencesFlow.value
        textToSpeech.updateSettings(prefs.speechRate, prefs.speechPitch, prefs.speechLanguageTag)
        val sample = if (prefs.speechLanguageTag.startsWith("bn", ignoreCase = true)) {
            "জার্ভিস ভয়েস সিস্টেম প্রস্তুত এবং সক্রিয় আছে।"
        } else {
            "JARVIS voice systems online. Speech rate and pitch calibrated."
        }
        textToSpeech.speak(sample)
    }

    fun stopSpeaking() {
        textToSpeech.stop()
        showNotice("Speech stopped.")
    }

    fun clearHistory() {
        viewModelScope.launch {
            textToSpeech.stop()
            conversationMemory.clear()
            _noticeEvents.send("Conversation history cleared.")
        }
    }

    fun requestClearHistory() {
        if (preferencesFlow.value.confirmSensitiveActions) {
            pendingSensitiveAction.value = PendingSensitiveAction(
                type = SensitiveActionType.CLEAR_MEMORY,
                title = "Confirm History Wipe",
                description = "Delete all saved JARVIS conversation messages from private storage? This cannot be undone."
            )
        } else {
            clearHistory()
        }
    }

    fun requestClearProviderToken(provider: AiProviderType) {
        if (preferencesFlow.value.confirmSensitiveActions) {
            pendingSensitiveAction.value = PendingSensitiveAction(
                type = SensitiveActionType.CLEAR_API_KEY,
                title = "Confirm API Key Removal",
                description = "Remove the encrypted ${provider.displayName} API key from Android Keystore?",
                providerType = provider
            )
        } else {
            clearProviderToken(provider)
        }
    }

    fun confirmPendingSensitiveAction() {
        val pending = pendingSensitiveAction.value ?: return
        pendingSensitiveAction.value = null
        when (pending.type) {
            SensitiveActionType.PHONE_CALL, SensitiveActionType.SMS_DRAFT -> {
                pending.commandPayload?.let { sendMessage(overrideText = it, skipSensitiveCheck = true) }
            }
            SensitiveActionType.CLEAR_MEMORY -> clearHistory()
            SensitiveActionType.CLEAR_API_KEY -> {
                pending.providerType?.let { clearProviderToken(it) }
            }
        }
    }

    fun dismissPendingSensitiveAction() {
        pendingSensitiveAction.value = null
    }

    fun emergencyStop() {
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        voiceMonitor.stopMonitoring()
        speechRecognizer.stopListening()
        textToSpeech.stop()
        isProcessing.value = false
        showNotice("JARVIS voice, monitor & processing stopped.")
    }

    fun showNotice(message: String) {
        viewModelScope.launch {
            _noticeEvents.send(message)
        }
    }

    fun requestOpenWorkbench(tab: WorkbenchTab = WorkbenchTab.CALCULATOR) {
        viewModelScope.launch {
            _openWorkbenchEvents.send(tab)
        }
    }

    fun requestOpenVision(mode: VisionScanMode = VisionScanMode.OCR) {
        viewModelScope.launch {
            _openVisionEvents.send(mode)
        }
    }

    fun sendVisionOutputToChat(output: VisionScanOutput) {
        viewModelScope.launch {
            conversationMemory.append(
                ChatMessage(
                    sender = MessageSender.USER,
                    content = "[Vision · ${output.mode.label}]"
                )
            )
            conversationMemory.append(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    content = output.summary
                )
            )
            if (preferencesFlow.value.ttsEnabled) {
                textToSpeech.speak(output.summary)
            }
        }
    }

    fun sendMessage(overrideText: String? = null, skipSensitiveCheck: Boolean = false) {
        val trimmed = (overrideText ?: inputText.value).trim()
        if (trimmed.isEmpty()) return

        if (overrideText == null) {
            inputText.value = ""
        }

        if (VoiceCommandParser.isStopSpeakingCommand(trimmed)) {
            textToSpeech.stop()
            speechRecognizer.stopListening()
            viewModelScope.launch {
                conversationMemory.append(ChatMessage(sender = MessageSender.USER, content = trimmed))
                conversationMemory.append(ChatMessage(sender = MessageSender.SYSTEM_NOTICE, content = "Speech interrupted."))
            }
            return
        }

        detectVisionNavigationIntent(trimmed)?.let { mode ->
            viewModelScope.launch {
                conversationMemory.append(ChatMessage(sender = MessageSender.USER, content = trimmed))
                conversationMemory.append(
                    ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        content = "Opening JARVIS Vision Scanner in ${mode.label} mode."
                    )
                )
                _openVisionEvents.send(mode)
            }
            return
        }

        detectWorkbenchNavigationIntent(trimmed)?.let { tab ->
            viewModelScope.launch {
                conversationMemory.append(ChatMessage(sender = MessageSender.USER, content = trimmed))
                conversationMemory.append(
                    ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        content = "Opening JARVIS Workbench (${tab.label})."
                    )
                )
                _openWorkbenchEvents.send(tab)
            }
            return
        }

        if (!skipSensitiveCheck && preferencesFlow.value.confirmSensitiveActions) {
            SensitiveActionDetector.inspectChatCommand(trimmed)?.let { action ->
                pendingSensitiveAction.value = action
                return
            }
        }

        textToSpeech.stop()

        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            isProcessing.value = true
            try {
                val userMessage = ChatMessage(
                    sender = MessageSender.USER,
                    content = trimmed
                )
                conversationMemory.append(userMessage)

                val historySnapshot = conversationMemory.messages.first()
                val replyResult = aiAssistant.generateReply(trimmed, historySnapshot)

                replyResult.onSuccess { replyText ->
                    conversationMemory.append(
                        ChatMessage(
                            sender = MessageSender.ASSISTANT,
                            content = replyText
                        )
                    )
                    if (preferencesFlow.value.ttsEnabled) {
                        textToSpeech.speak(replyText)
                    }
                }.onFailure { error ->
                    conversationMemory.append(
                        ChatMessage(
                            sender = MessageSender.SYSTEM_NOTICE,
                            content = error.message ?: "Unable to process request."
                        )
                    )
                }
            } catch (_: CancellationException) {
                conversationMemory.append(
                    ChatMessage(
                        sender = MessageSender.SYSTEM_NOTICE,
                        content = "Operation aborted by user."
                    )
                )
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun onMicrophoneClicked() {
        if (voiceMonitor.status.value.isServiceRunning) {
            voiceMonitor.stopMonitoring()
            showNotice("Hands-free monitor paused.")
            return
        }

        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            return
        }

        textToSpeech.stop()
        val prefs = preferencesFlow.value
        val preferOffline = prefs.preferOfflineVoice || !telemetryProvider.currentSnapshot().isOnline
        speechRecognizer.startListening(
            languageTag = prefs.speechLanguageTag,
            preferOffline = preferOffline,
            onResult = { recognizedText ->
                sendMessage(overrideText = recognizedText)
            },
            onError = { reason ->
                showNotice(reason)
            }
        )
    }

    private fun detectVisionNavigationIntent(text: String): VisionScanMode? {
        val lower = text.trim().lowercase()
        return when {
            lower in setOf("open scanner", "open vision", "vision scanner", "scan document", "document scanner", "scan text", "ocr", "read document") ->
                VisionScanMode.OCR
            lower in setOf("scan qr", "scan qr code", "qr scanner", "scan barcode", "barcode scanner") ->
                VisionScanMode.QR_BARCODE
            lower in setOf("identify object", "identify objects", "object scanner", "scan objects") ->
                VisionScanMode.OBJECT_LABEL
            lower in setOf("analyze screenshot", "describe image", "describe screenshot", "scan screenshot") ->
                VisionScanMode.AI_DESCRIBE
            else -> null
        }
    }

    private fun detectWorkbenchNavigationIntent(text: String): WorkbenchTab? {
        val lower = text.trim().lowercase(Locale.US)
        return when {
            lower in setOf("open calculator", "scientific calculator", "open scientific calculator") ->
                WorkbenchTab.CALCULATOR
            lower in setOf("open files", "file manager", "search files", "open file manager") ->
                WorkbenchTab.FILES
            lower in setOf("open pdf", "pdf reader", "read pdf", "summarize pdf") ->
                WorkbenchTab.PDF
            lower in setOf("open clipboard", "clipboard assistant", "open workbench") ->
                WorkbenchTab.CLIPBOARD_BATCH
            else -> null
        }
    }

    fun addNote(title: String, content: String, tag: String = "General") {
        val prod = productivity ?: return
        viewModelScope.launch {
            prod.addNote(title, content, tag)
                .onSuccess { showNotice(it) }
                .onFailure { showNotice(it.message ?: "Unable to save note.") }
        }
    }

    fun deleteNote(id: Long) {
        val prod = productivity ?: return
        viewModelScope.launch { prod.deleteNote(id) }
    }

    fun addTask(title: String, category: String) {
        val prod = productivity ?: return
        viewModelScope.launch {
            prod.addTask(title, category)
                .onSuccess { showNotice(it) }
                .onFailure { showNotice(it.message ?: "Unable to add task.") }
        }
    }

    fun toggleTask(id: Long, isDone: Boolean) {
        val prod = productivity ?: return
        viewModelScope.launch { prod.toggleTask(id, isDone) }
    }

    fun deleteTask(id: Long) {
        val prod = productivity ?: return
        viewModelScope.launch { prod.deleteTask(id) }
    }

    fun scheduleReminder(title: String, delayMinutes: Long) {
        val prod = productivity ?: return
        viewModelScope.launch {
            prod.scheduleReminder(title, delayMinutes)
                .onSuccess { showNotice(it) }
                .onFailure { showNotice(it.message ?: "Unable to schedule reminder.") }
        }
    }

    fun deleteReminder(id: Long) {
        val prod = productivity ?: return
        viewModelScope.launch { prod.deleteReminder(id) }
    }

    fun addHabit(title: String) {
        val prod = productivity ?: return
        viewModelScope.launch {
            prod.addHabit(title)
                .onSuccess { showNotice(it) }
                .onFailure { showNotice(it.message ?: "Unable to add habit.") }
        }
    }

    fun checkInHabit(id: Long) {
        val prod = productivity ?: return
        viewModelScope.launch {
            prod.checkInHabit(id)
                .onSuccess { showNotice(it) }
                .onFailure { showNotice(it.message ?: "Unable to check in habit.") }
        }
    }

    fun deleteHabit(id: Long) {
        val prod = productivity ?: return
        viewModelScope.launch { prod.deleteHabit(id) }
    }

    fun addRoutine(title: String, triggerTimeLabel: String, commandSequence: String) {
        val prod = productivity ?: return
        viewModelScope.launch {
            prod.addRoutine(title, triggerTimeLabel, commandSequence)
                .onSuccess { showNotice(it) }
                .onFailure { showNotice(it.message ?: "Unable to save routine.") }
        }
    }

    fun deleteRoutine(id: Long) {
        val prod = productivity ?: return
        viewModelScope.launch { prod.deleteRoutine(id) }
    }

    fun openCalendarComposer(title: String) {
        val prod = productivity ?: return
        prod.openCalendarEventComposer(title)
            .onSuccess { showNotice(it) }
            .onFailure { showNotice(it.message ?: "Unable to open Calendar app.") }
    }

    override fun onCleared() {
        super.onCleared()
        if (!voiceMonitor.status.value.isServiceRunning) {
            speechRecognizer.release()
            textToSpeech.release()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        aiAssistant = container.aiAssistant,
                        speechRecognizer = container.speechRecognizer,
                        textToSpeech = container.textToSpeech,
                        conversationMemory = container.conversationMemory,
                        preferencesRepository = container.preferencesRepository,
                        telemetryProvider = container.telemetryProvider,
                        secureVault = container.secureVault,
                        voiceMonitor = container.voiceMonitor,
                        visionAnalyzer = container.visionAnalyzer,
                        productivity = container.productivityRepository,
                        clipboardPort = container.clipboardPort,
                        workspaceDocs = container.workspaceDocuments,
                        biometricAuth = container.biometricAuth
                    ) as T
                }
            }
        }
    }
}
