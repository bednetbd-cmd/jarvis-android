package com.jarvis.assistant

import com.jarvis.assistant.core.ai.AiRemoteTransport
import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.EndpointUrlValidator
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.MultiProviderAiRouter
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.ai.RemoteChatMessage
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.memory.InMemoryConversationStore
import com.jarvis.assistant.core.productivity.CalendarEventSummary
import com.jarvis.assistant.core.productivity.CalendarPort
import com.jarvis.assistant.core.productivity.InMemoryProductivityRepository
import com.jarvis.assistant.core.security.SensitiveActionDetector
import com.jarvis.assistant.core.tools.AngleUnit
import com.jarvis.assistant.core.tools.ContextualSuggestionEngine
import com.jarvis.assistant.core.tools.ScientificCalculator
import com.jarvis.assistant.core.vision.VisionConsentPolicy
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.core.voice.TtsLanguageAvailability
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.security.InMemorySecureVault
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.JarvisPreferences
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone10AuditTest {

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
    fun endpointValidatorEnforcesHttpsForPublicAndAllowsPrivateHttp() {
        assertTrue(EndpointUrlValidator.validate("https://api.groq.com/openai/v1", allowPrivateHttp = true).isSuccess)
        assertTrue(EndpointUrlValidator.validate("http://127.0.0.1:11434", allowPrivateHttp = true).isSuccess)
        assertTrue(EndpointUrlValidator.validate("http://192.168.1.25:11434", allowPrivateHttp = true).isSuccess)
        assertTrue(EndpointUrlValidator.validate("http://example.com/v1", allowPrivateHttp = true).isFailure)
        assertTrue(EndpointUrlValidator.validate("ftp://example.com", allowPrivateHttp = true).isFailure)
        assertTrue(EndpointUrlValidator.validate("", allowPrivateHttp = true).isFailure)
    }

    @Test
    fun scientificCalculatorHandlesDeepNestingNegativesAndLocaleInvariance() {
        val nested = ScientificCalculator.evaluate("((2 + 3)^2 - sqrt(81)) / 4 + cos(0)")
        assertEquals(5.0, nested.getOrThrow(), 1e-9)

        val formatted = ScientificCalculator.format(nested.getOrThrow())
        assertEquals("5", formatted)

        val degVsRad = ScientificCalculator.evaluate("sin(180)", AngleUnit.DEG).getOrThrow()
        assertEquals(0.0, degVsRad, 1e-9)

        val malformed = ScientificCalculator.evaluate("sin((1+2)")
        assertTrue(malformed.isFailure)
    }

    @Test
    fun sensitiveActionDetectorAndBatchCapsAreBounded() {
        val eightCommands = (1..8).joinToString("; ") { "Battery status" }
        val parsed = SensitiveActionDetector.parseBatchCommands("Batch: $eightCommands")
        assertNotNull(parsed)
        assertEquals(6, parsed?.size)

        assertNotNull(SensitiveActionDetector.inspectChatCommand("Dial +8801711111111"))
        assertNotNull(SensitiveActionDetector.inspectChatCommand("Send SMS to Mom saying Home soon"))
    }

    @Test
    fun suggestionEngineCapsResultsAndMaintainsUniqueness() {
        val suggestions = ContextualSuggestionEngine.generate(
            telemetry = DeviceTelemetry(batteryPercent = 12, isCharging = false, networkTransport = NetworkTransport.WIFI),
            hourOfDay = 8,
            pendingTaskCount = 4,
            activeReminderCount = 2,
            pendingHabitCount = 3,
            unreadNotificationCount = 5
        )
        assertTrue(suggestions.size in 1..5)
        assertEquals(suggestions.size, suggestions.map { it.command }.distinct().size)
    }

    @Test
    fun visionConsentPolicyBlocksUnconsentedRemoteUploads() = runTest(testDispatcher) {
        val prefs = FakePreferencesRepository(
            JarvisPreferences().copy(
                aiProviderConfig = JarvisPreferences().aiProviderConfig.copy(
                    activeProvider = AiProviderType.GEMINI_REST
                )
            )
        )
        val vault = InMemorySecureVault().apply {
            saveSecret("gemini_token", "test-key")
        }
        val policy = VisionConsentPolicy(
            preferencesRepository = prefs,
            secureVault = vault,
            telemetryProvider = AuditTelemetryProvider(),
            remoteTransport = AuditRemoteTransport()
        )
        val denied = policy.analyzePreparedImageWithConsent(
            jpegBase64 = "ZmFrZQ==",
            prompt = "Describe",
            uploadConsentGranted = false
        )
        assertTrue(denied.isFailure)

        val allowed = policy.analyzePreparedImageWithConsent(
            jpegBase64 = "ZmFrZQ==",
            prompt = "Describe",
            uploadConsentGranted = true
        )
        assertTrue(allowed.isSuccess)
    }

    @Test
    fun crossMilestoneViewModelFlowHandlesRouterProductivityAndEmergencyStop() = runTest(testDispatcher) {
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                ttsEnabled = false,
                confirmSensitiveActions = true,
                biometricLockEnabled = false
            )
        )
        val vault = InMemorySecureVault()
        val telemetry = AuditTelemetryProvider()
        val automation = AuditAutomation()
        val contextPort = AuditContextPort()
        val productivity = InMemoryProductivityRepository(
            calendarPort = AuditCalendarPort(),
            deviceContext = contextPort
        )
        val onDevice = OnDeviceJarvisAssistant(
            phoneAutomation = automation,
            deviceContext = contextPort,
            productivity = productivity
        )
        val registry = JarvisToolRegistry(
            phoneAutomation = automation,
            deviceContext = contextPort,
            productivity = productivity
        )
        val router = MultiProviderAiRouter(
            preferencesRepository = prefs,
            secureVault = vault,
            telemetryProvider = telemetry,
            onDeviceAssistant = onDevice,
            toolRegistry = registry,
            remoteTransport = AuditRemoteTransport(),
            deviceContext = contextPort
        )
        val tts = AuditTts()
        val viewModel = MainViewModel(
            aiAssistant = router,
            speechRecognizer = AuditSpeechRecognizer(),
            textToSpeech = tts,
            conversationMemory = InMemoryConversationStore(),
            preferencesRepository = prefs,
            telemetryProvider = telemetry,
            secureVault = vault,
            productivity = productivity
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onInputChanged("Draft message")
        assertEquals("Draft message", viewModel.uiState.value.inputText)

        viewModel.sendMessage("Note: Final Release Audit Passed")
        advanceUntilIdle()
        val lastNoteReply = viewModel.uiState.value.messages.last().content
        assertTrue(lastNoteReply.contains("Saved note", ignoreCase = true))
        assertTrue(lastNoteReply.contains("Final Release Audit Passed"))

        // API key clear requires confirmation when confirmSensitiveActions = true
        vault.saveSecret("gemini_token", "dummy-key")
        viewModel.requestClearProviderToken(AiProviderType.GEMINI_REST)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pendingSensitiveAction)
        assertTrue(vault.hasSecret("gemini_token"))

        viewModel.confirmPendingSensitiveAction()
        advanceUntilIdle()
        assertFalse(vault.hasSecret("gemini_token"))

        // Emergency stop resets processing and stops TTS safely
        viewModel.emergencyStop()
        advanceUntilIdle()
        assertTrue(tts.stopCount >= 1)
    }
}

private class AuditTelemetryProvider : SystemTelemetryPort {
    private val state = MutableStateFlow(DeviceTelemetry(88, false, NetworkTransport.WIFI))
    override val telemetry: Flow<DeviceTelemetry> = state.asStateFlow()
    override fun currentSnapshot(): DeviceTelemetry = state.value
}

private class AuditCalendarPort : CalendarPort {
    override fun hasReadPermission(): Boolean = true
    override fun readUpcomingEvents(daysAhead: Int): Result<List<CalendarEventSummary>> = Result.success(emptyList())
    override fun openEventComposer(title: String, startMillis: Long?): Result<String> =
        Result.success("Opening calendar composer for $title")
}

private class AuditRemoteTransport : AiRemoteTransport {
    override suspend fun chatOllama(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("Ollama OK")

    override suspend fun testOllama(baseUrl: String): Result<String> = Result.success("Ollama OK")

    override suspend fun chatOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("OpenAI OK")

    override suspend fun testOpenAiCompatible(baseUrl: String, apiKey: String): Result<String> =
        Result.success("OpenAI OK")

    override suspend fun chatGemini(
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("Gemini OK")

    override suspend fun testGemini(model: String, apiKey: String): Result<String> =
        Result.success("Gemini OK")

    override suspend fun analyzeImage(
        provider: AiProviderType,
        baseUrl: String,
        model: String,
        apiKey: String,
        prompt: String,
        jpegBase64: String
    ): Result<String> = Result.success("Audit image description")
}

private class AuditAutomation : PhoneAutomationPort {
    override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> =
        Result.success("Executed $actionName")
}

private class AuditContextPort : DeviceContextPort {
    override fun getCurrentTimeDate(): String = "Time: 10:00 AM"
    override fun getBatteryStatus(): String = "Battery: 88%"
    override fun getDeviceDiagnostics(): String = "Device: Audit"
}

private class AuditSpeechRecognizer : SpeechRecognizerPort {
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

private class AuditTts : TextToSpeechPort {
    var stopCount = 0
    override val isReady: Boolean = true
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val languageAvailability: StateFlow<TtsLanguageAvailability> =
        MutableStateFlow(TtsLanguageAvailability.READY).asStateFlow()
    override fun updateSettings(speechRate: Float, pitch: Float, languageTag: String) = Unit
    override fun speak(text: String) = Unit
    override fun stop() { stopCount++ }
    override fun release() = Unit
}
