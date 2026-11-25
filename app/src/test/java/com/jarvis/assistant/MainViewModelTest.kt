package com.jarvis.assistant

import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.memory.InMemoryConversationStore
import com.jarvis.assistant.core.voice.InMemoryVoiceMonitorController
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.core.voice.TtsLanguageAvailability
import com.jarvis.assistant.core.voice.VoiceMonitorMode
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.preferences.JarvisPreferencesPort
import com.jarvis.assistant.data.security.InMemorySecureVault
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.AssistantPersonality
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.JarvisAccentTheme
import com.jarvis.assistant.domain.model.JarvisPreferences
import com.jarvis.assistant.domain.model.MessageSender
import com.jarvis.assistant.domain.model.NetworkTransport
import com.jarvis.assistant.ui.screens.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessageAppendsUserAndAssistantRepliesAndSpeaksWhenTtsEnabled() = runTest(testDispatcher) {
        val fakeTts = FakeTextToSpeech()
        val fakePrefs = FakePreferencesRepository()
        val memory = InMemoryConversationStore()
        val viewModel = MainViewModel(
            aiAssistant = FakeAiAssistant(),
            speechRecognizer = FakeSpeechRecognizer(),
            textToSpeech = fakeTts,
            conversationMemory = memory,
            preferencesRepository = fakePrefs,
            telemetryProvider = FakeTelemetryProvider(),
            secureVault = InMemorySecureVault()
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onInputChanged("Calculate 6 * 7")
        viewModel.sendMessage()
        advanceUntilIdle()

        val messages = viewModel.uiState.value.messages
        assertEquals(2, messages.size)
        assertEquals(MessageSender.USER, messages[0].sender)
        assertEquals("Calculate 6 * 7", messages[0].content)
        assertEquals(MessageSender.ASSISTANT, messages[1].sender)
        assertEquals("Reply to: Calculate 6 * 7", messages[1].content)
        assertEquals(listOf("Reply to: Calculate 6 * 7"), fakeTts.spokenLines)
    }

    @Test
    fun memorySearchFiltersMessagesAndThemeUpdatesPersist() = runTest(testDispatcher) {
        val fakePrefs = FakePreferencesRepository()
        val memory = InMemoryConversationStore(
            listOf(
                ChatMessage(sender = MessageSender.USER, content = "Open Wi-Fi"),
                ChatMessage(sender = MessageSender.ASSISTANT, content = "Opening Wi-Fi settings.")
            )
        )
        val viewModel = MainViewModel(
            aiAssistant = FakeAiAssistant(),
            speechRecognizer = FakeSpeechRecognizer(),
            textToSpeech = FakeTextToSpeech(),
            conversationMemory = memory,
            preferencesRepository = fakePrefs,
            telemetryProvider = FakeTelemetryProvider(),
            secureVault = InMemorySecureVault()
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setAccentTheme(JarvisAccentTheme.STARK_GOLD)
        viewModel.onMemorySearchChanged("Opening")
        advanceUntilIdle()

        assertEquals(JarvisAccentTheme.STARK_GOLD, viewModel.uiState.value.preferences.accentTheme)
        assertEquals(1, viewModel.uiState.value.filteredMessages.size)
        assertTrue(viewModel.uiState.value.filteredMessages.first().content.contains("Opening"))
    }

    @Test
    fun savingAndClearingProviderTokensAndTestingConnectionUpdatesUiState() = runTest(testDispatcher) {
        val fakePrefs = FakePreferencesRepository()
        val vault = InMemorySecureVault()
        val viewModel = MainViewModel(
            aiAssistant = FakeAiAssistant(),
            speechRecognizer = FakeSpeechRecognizer(),
            textToSpeech = FakeTextToSpeech(),
            conversationMemory = InMemoryConversationStore(),
            preferencesRepository = fakePrefs,
            telemetryProvider = FakeTelemetryProvider(),
            secureVault = vault
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setActiveAiProvider(AiProviderType.GEMINI_REST)
        viewModel.saveProviderToken(AiProviderType.GEMINI_REST, "free-tier-token-123")
        viewModel.testAiProviderConnection()
        advanceUntilIdle()

        assertEquals(AiProviderType.GEMINI_REST, viewModel.uiState.value.preferences.aiProviderConfig.activeProvider)
        assertTrue(viewModel.uiState.value.preferences.aiProviderConfig.hasGeminiToken)
        assertTrue(viewModel.uiState.value.isAiConnected)
        assertEquals(true, viewModel.uiState.value.aiTestSuccess)

        viewModel.clearProviderToken(AiProviderType.GEMINI_REST)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.preferences.aiProviderConfig.hasGeminiToken)
        assertFalse(viewModel.uiState.value.isAiConnected)
    }

    @Test
    fun stopSpeakingCommandInterruptsTtsWithoutCallingAiEngine() = runTest(testDispatcher) {
        val fakeTts = FakeTextToSpeech()
        val fakeAi = FakeAiAssistant()
        val viewModel = MainViewModel(
            aiAssistant = fakeAi,
            speechRecognizer = FakeSpeechRecognizer(),
            textToSpeech = fakeTts,
            conversationMemory = InMemoryConversationStore(),
            preferencesRepository = FakePreferencesRepository(),
            telemetryProvider = FakeTelemetryProvider(),
            secureVault = InMemorySecureVault()
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.sendMessage("stop speaking")
        advanceUntilIdle()

        assertTrue(fakeTts.stopCalls >= 1)
        assertEquals(0, fakeAi.callCount)
        assertEquals("Speech interrupted.", viewModel.uiState.value.messages.last().content)
    }

    @Test
    fun voiceMonitorCanBeStartedAndStoppedViaEmergencyStop() = runTest(testDispatcher) {
        val fakeMonitor = InMemoryVoiceMonitorController()
        val viewModel = MainViewModel(
            aiAssistant = FakeAiAssistant(),
            speechRecognizer = FakeSpeechRecognizer(),
            textToSpeech = FakeTextToSpeech(),
            conversationMemory = InMemoryConversationStore(),
            preferencesRepository = FakePreferencesRepository(),
            telemetryProvider = FakeTelemetryProvider(),
            secureVault = InMemorySecureVault(),
            voiceMonitor = fakeMonitor
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setVoiceMonitorMode(VoiceMonitorMode.WAKE_WORD)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.voiceMonitorStatus.isServiceRunning)
        assertEquals(VoiceMonitorMode.WAKE_WORD, viewModel.uiState.value.voiceMonitorStatus.mode)

        viewModel.emergencyStop()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.voiceMonitorStatus.isServiceRunning)
        assertEquals(VoiceMonitorMode.OFF, viewModel.uiState.value.voiceMonitorStatus.mode)
    }
}

private class FakeAiAssistant : AiAssistantPort {
    var callCount = 0
    override val isConfigured: Boolean = true
    override val engineLabel: String = "Test Assistant"
    override suspend fun generateReply(prompt: String, history: List<ChatMessage>): Result<String> {
        callCount++
        return Result.success("Reply to: $prompt")
    }
    override suspend fun testActiveConnection(): Result<String> {
        return Result.success("Test connection OK")
    }
}

private class FakeSpeechRecognizer : SpeechRecognizerPort {
    override val isAvailable: Boolean = true
    override val isOnDeviceAvailable: Boolean = true
    override val isListening: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val rmsLevel: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()
    override val partialTranscript: StateFlow<String> = MutableStateFlow("").asStateFlow()
    override fun startListening(
        languageTag: String,
        preferOffline: Boolean,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) = Unit
    override fun stopListening() = Unit
    override fun release() = Unit
}

private class FakeTextToSpeech : TextToSpeechPort {
    val spokenLines = mutableListOf<String>()
    var stopCalls = 0
    override val isReady: Boolean = true
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val languageAvailability: StateFlow<TtsLanguageAvailability> =
        MutableStateFlow(TtsLanguageAvailability.READY).asStateFlow()
    override fun updateSettings(speechRate: Float, pitch: Float, languageTag: String) = Unit
    override fun speak(text: String) {
        spokenLines.add(text)
    }
    override fun stop() {
        stopCalls++
    }
    override fun release() = Unit
}

internal class FakePreferencesRepository(
    initial: JarvisPreferences = JarvisPreferences()
) : JarvisPreferencesPort {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<JarvisPreferences> = state.asStateFlow()
    override suspend fun setTtsEnabled(enabled: Boolean) { state.value = state.value.copy(ttsEnabled = enabled) }
    override suspend fun setSpeechRate(rate: Float) { state.value = state.value.copy(speechRate = rate) }
    override suspend fun setSpeechPitch(pitch: Float) { state.value = state.value.copy(speechPitch = pitch) }
    override suspend fun setSpeechLanguageTag(languageTag: String) { state.value = state.value.copy(speechLanguageTag = languageTag) }
    override suspend fun setPreferOfflineVoice(enabled: Boolean) { state.value = state.value.copy(preferOfflineVoice = enabled) }
    override suspend fun setAccentTheme(theme: JarvisAccentTheme) { state.value = state.value.copy(accentTheme = theme) }
    override suspend fun setShowWaveform(enabled: Boolean) { state.value = state.value.copy(showWaveform = enabled) }
    override suspend fun setBiometricLockEnabled(enabled: Boolean) { state.value = state.value.copy(biometricLockEnabled = enabled) }
    override suspend fun setConfirmSensitiveActions(enabled: Boolean) { state.value = state.value.copy(confirmSensitiveActions = enabled) }
    override suspend fun setEncryptConversationHistory(enabled: Boolean) { state.value = state.value.copy(encryptConversationHistory = enabled) }
    override suspend fun setActiveAiProvider(provider: AiProviderType) {
        state.value = state.value.copy(
            aiProviderConfig = state.value.aiProviderConfig.copy(activeProvider = provider)
        )
    }
    override suspend fun setAssistantPersonality(personality: AssistantPersonality) {
        state.value = state.value.copy(
            aiProviderConfig = state.value.aiProviderConfig.copy(personality = personality)
        )
    }
    override suspend fun setOllamaConfig(baseUrl: String, model: String) {
        state.value = state.value.copy(
            aiProviderConfig = state.value.aiProviderConfig.copy(
                ollamaBaseUrl = baseUrl.trim(),
                ollamaModel = model.trim()
            )
        )
    }
    override suspend fun setOpenAiConfig(baseUrl: String, model: String) {
        state.value = state.value.copy(
            aiProviderConfig = state.value.aiProviderConfig.copy(
                openAiBaseUrl = baseUrl.trim(),
                openAiModel = model.trim()
            )
        )
    }
    override suspend fun setGeminiModel(model: String) {
        state.value = state.value.copy(
            aiProviderConfig = state.value.aiProviderConfig.copy(geminiModel = model.trim())
        )
    }
}

private class FakeTelemetryProvider : SystemTelemetryPort {
    private val snapshot = DeviceTelemetry(85, true, NetworkTransport.WIFI)
    override val telemetry: Flow<DeviceTelemetry> = MutableStateFlow(snapshot).asStateFlow()
    override fun currentSnapshot(): DeviceTelemetry = snapshot
}
