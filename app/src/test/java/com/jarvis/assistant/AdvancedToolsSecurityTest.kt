package com.jarvis.assistant

import androidx.fragment.app.FragmentActivity
import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.ai.ParsedToolCall
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.memory.InMemoryConversationStore
import com.jarvis.assistant.core.security.BiometricAuthPort
import com.jarvis.assistant.core.security.BiometricCapability
import com.jarvis.assistant.core.security.SensitiveActionDetector
import com.jarvis.assistant.core.security.SensitiveActionType
import com.jarvis.assistant.core.tools.AngleUnit
import com.jarvis.assistant.core.tools.ClipboardPort
import com.jarvis.assistant.core.tools.ClipboardSnapshot
import com.jarvis.assistant.core.tools.ContextualSuggestionEngine
import com.jarvis.assistant.core.tools.ScientificCalculator
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.core.voice.TtsLanguageAvailability
import com.jarvis.assistant.data.device.SystemTelemetryPort
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
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedToolsSecurityTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scientificCalculatorEvaluatesTrigLogsPowersFactorialsAndErrors() {
        val degSin = ScientificCalculator.evaluate("sin(30)", AngleUnit.DEG)
        assertEquals(0.5, degSin.getOrThrow(), 1e-9)

        val radSin = ScientificCalculator.evaluate("sin(pi / 2 rad)")
        assertEquals(1.0, radSin.getOrThrow(), 1e-9)

        val combo = ScientificCalculator.evaluate("sqrt(144) + 5!")
        assertEquals(132.0, combo.getOrThrow(), 1e-9)

        val rightAssocPow = ScientificCalculator.evaluate("2^3^2")
        assertEquals(512.0, rightAssocPow.getOrThrow(), 1e-9)

        val logs = ScientificCalculator.evaluate("log(1000) + ln(e)")
        assertEquals(4.0, logs.getOrThrow(), 1e-9)

        assertTrue(ScientificCalculator.evaluate("10 / 0").isFailure)
        assertTrue(ScientificCalculator.evaluate("sqrt(-9)").isFailure)
        assertTrue(ScientificCalculator.evaluate("(-2)!").isFailure)
        assertTrue(ScientificCalculator.evaluate("25!").isFailure)
    }

    @Test
    fun contextualSuggestionEngineAdaptsToBatteryNetworkAndTasks() {
        val offlineLowBattery = ContextualSuggestionEngine.generate(
            telemetry = DeviceTelemetry(batteryPercent = 15, isCharging = false, networkTransport = NetworkTransport.OFFLINE),
            hourOfDay = 8,
            pendingTaskCount = 3,
            activeReminderCount = 1,
            pendingHabitCount = 2,
            unreadNotificationCount = 0
        )
        val commands = offlineLowBattery.map { it.command }
        assertTrue(commands.contains("Device info"))
        assertTrue(commands.contains("Daily briefing"))
        assertTrue(commands.contains("My tasks"))
        assertFalse(commands.contains("Latest news"))

        val onlineEvening = ContextualSuggestionEngine.generate(
            telemetry = DeviceTelemetry(batteryPercent = 90, isCharging = false, networkTransport = NetworkTransport.WIFI),
            hourOfDay = 19,
            pendingTaskCount = 0,
            activeReminderCount = 0,
            pendingHabitCount = 0,
            unreadNotificationCount = 2
        )
        assertTrue(onlineEvening.any { it.command == "Daily briefing" })
        assertTrue(onlineEvening.any { it.command == "Read notifications" })
    }

    @Test
    fun sensitiveActionDetectorAndBatchParserValidateInputs() {
        val callAction = SensitiveActionDetector.inspectChatCommand("Call 01700000000")
        assertNotNull(callAction)
        assertEquals(SensitiveActionType.PHONE_CALL, callAction?.type)

        val smsAction = SensitiveActionDetector.inspectChatCommand("Text Alex saying On my way")
        assertNotNull(smsAction)
        assertEquals(SensitiveActionType.SMS_DRAFT, smsAction?.type)

        assertNull(SensitiveActionDetector.inspectChatCommand("Weather in Dhaka"))

        val steps = SensitiveActionDetector.parseBatchCommands("Batch: What time is it?; Calculate 5!; Battery status")
        assertEquals(listOf("What time is it?", "Calculate 5!", "Battery status"), steps)
    }

    @Test
    fun assistantAndToolRegistryExecuteScientificMathClipboardAndBatch() = runTest(testDispatcher) {
        val clipboard = FakeClipboardPort("Kotlin Jetpack Compose makes native Android UI clean and fast. Room stores data locally. Security matters.")
        val automation = RecordingAdvAutomation()
        val ctx = FakeAdvContext()
        val assistant = OnDeviceJarvisAssistant(
            phoneAutomation = automation,
            deviceContext = ctx,
            clipboardPort = clipboard
        )

        val sciReply = assistant.generateReply("Calculate sin(30) + sqrt(144)", emptyList())
        assertTrue(sciReply.isSuccess)
        assertTrue(sciReply.getOrNull().orEmpty().contains("12.5"))

        val clipSummary = assistant.generateReply("Summarize clipboard", emptyList())
        assertTrue(clipSummary.isSuccess)
        assertTrue(clipSummary.getOrNull().orEmpty().contains("Kotlin Jetpack Compose"))

        val batchReply = assistant.generateReply(
            "Batch: What time is it?; Batch: loop; Calculate 5!; Battery status",
            emptyList()
        )
        assertTrue(batchReply.isSuccess)
        val batchText = batchReply.getOrNull().orEmpty()
        assertTrue(batchText.contains("Batch Execution (2 steps)") || batchText.contains("Batch Execution (3 steps)"))
        assertTrue(batchText.contains("120"))
        assertTrue(batchText.contains("92%"))

        val registry = JarvisToolRegistry(automation, ctx, clipboardPort = clipboard)
        val mathTool = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_CALCULATE_MATH,
                arguments = mapOf("expression" to "cos(0) + 4!")
            )
        )
        assertTrue(mathTool.isSuccess)
        assertTrue(mathTool.getOrNull().orEmpty().contains("25"))

        val clipTool = registry.executeTool(
            ParsedToolCall(name = JarvisToolRegistry.TOOL_CLIPBOARD_READ, arguments = emptyMap())
        )
        assertTrue(clipTool.isSuccess)
        assertTrue(clipTool.getOrNull().orEmpty().contains("Jetpack Compose"))
    }

    @Test
    fun viewModelEnforcesSensitiveConfirmationAndBiometricLock() = runTest(testDispatcher) {
        val automation = RecordingAdvAutomation()
        val assistant = OnDeviceJarvisAssistant(automation, FakeAdvContext())
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                biometricLockEnabled = true,
                confirmSensitiveActions = true,
                ttsEnabled = false
            )
        )
        val memory = InMemoryConversationStore()
        val biometric = FakeBiometricPort(BiometricCapability.AVAILABLE)

        val viewModel = MainViewModel(
            aiAssistant = assistant,
            speechRecognizer = DummySpeechRecognizer(),
            textToSpeech = DummyTts(),
            conversationMemory = memory,
            preferencesRepository = prefs,
            telemetryProvider = AdvTelemetryProvider(),
            biometricAuth = biometric
        )

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // Boots locked when biometricLockEnabled = true and capability is AVAILABLE
        assertTrue(viewModel.uiState.value.isAppLocked)
        viewModel.unlockApp()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAppLocked)

        // Sensitive call command awaits confirmation
        viewModel.sendMessage("Call +8801700000000")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pendingSensitiveAction)
        assertEquals(0, automation.executedActions.size)

        // Dismiss cancels execution
        viewModel.dismissPendingSensitiveAction()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingSensitiveAction)
        assertEquals(0, automation.executedActions.size)

        // Confirm executes the dial action
        viewModel.sendMessage("Call +8801700000000")
        advanceUntilIdle()
        viewModel.confirmPendingSensitiveAction()
        advanceUntilIdle()
        assertEquals(1, automation.executedActions.size)

        // History wipe confirmation
        viewModel.requestClearHistory()
        advanceUntilIdle()
        assertEquals(SensitiveActionType.CLEAR_MEMORY, viewModel.uiState.value.pendingSensitiveAction?.type)
        assertTrue(memory.messages.first().isNotEmpty())
        viewModel.confirmPendingSensitiveAction()
        advanceUntilIdle()
        assertTrue(memory.messages.first().isEmpty())
    }
}

private class FakeClipboardPort(private var text: String) : ClipboardPort {
    override fun readText(): ClipboardSnapshot =
        if (text.isBlank()) ClipboardSnapshot("", false, "Empty")
        else ClipboardSnapshot(text, true, "OK")

    override fun writeText(label: String, text: String): Result<String> {
        this.text = text
        return Result.success("Copied")
    }
}

private class FakeAdvContext : DeviceContextPort {
    override fun getCurrentTimeDate(): String = "Current time: 11:00 AM."
    override fun getBatteryStatus(): String = "Battery is at 92%."
    override fun getDeviceDiagnostics(): String = "Device: Pixel"
}

private class RecordingAdvAutomation : PhoneAutomationPort {
    val executedActions = mutableListOf<String>()
    override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> {
        executedActions.add(actionName)
        return Result.success("Executed $actionName")
    }
}

private class FakeBiometricPort(private val capability: BiometricCapability) : BiometricAuthPort {
    override fun checkCapability(): BiometricCapability = capability
    override fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSuccess()
    }
}

private class DummySpeechRecognizer : SpeechRecognizerPort {
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

private class DummyTts : TextToSpeechPort {
    override val isReady: Boolean = true
    override val isSpeaking: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val languageAvailability: StateFlow<TtsLanguageAvailability> =
        MutableStateFlow(TtsLanguageAvailability.READY).asStateFlow()
    override fun updateSettings(speechRate: Float, pitch: Float, languageTag: String) = Unit
    override fun speak(text: String) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

private class AdvTelemetryProvider : SystemTelemetryPort {
    private val snapshot = DeviceTelemetry(92, true, NetworkTransport.WIFI)
    override val telemetry: Flow<DeviceTelemetry> = MutableStateFlow(snapshot).asStateFlow()
    override fun currentSnapshot(): DeviceTelemetry = snapshot
}
