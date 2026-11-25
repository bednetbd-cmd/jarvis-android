package com.jarvis.assistant

import com.jarvis.assistant.core.ai.AiRemoteTransport
import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.MultiProviderAiRouter
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.ai.RemoteChatMessage
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.security.AndroidKeystoreVault
import com.jarvis.assistant.data.security.InMemorySecureVault
import com.jarvis.assistant.domain.model.AiProviderConfig
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.JarvisPreferences
import com.jarvis.assistant.domain.model.MessageSender
import com.jarvis.assistant.domain.model.NetworkTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiProviderAiRouterTest {

    @Test
    fun failsHonestlyWhenCloudProviderSelectedWithoutApiKey() = runTest {
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(activeProvider = AiProviderType.GEMINI_REST)
            )
        )
        val router = buildRouter(
            prefs = prefs,
            telemetry = FixedTelemetry(DeviceTelemetry(80, true, NetworkTransport.WIFI)),
            transport = FakeRemoteTransport()
        )

        val result = router.generateReply("Explain quantum computing", emptyList())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Gemini API key", ignoreCase = true))
    }

    @Test
    fun failsHonestlyWhenDeviceIsOfflineForCloudProvider() = runTest {
        val vault = InMemorySecureVault().apply {
            saveSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN, "sample-key")
        }
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(activeProvider = AiProviderType.GEMINI_REST)
            )
        )
        val router = buildRouter(
            prefs = prefs,
            vault = vault,
            telemetry = FixedTelemetry(DeviceTelemetry(80, false, NetworkTransport.OFFLINE)),
            transport = FakeRemoteTransport()
        )

        val result = router.generateReply("Hello", emptyList())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("offline", ignoreCase = true))
    }

    @Test
    fun executesToolCallLoopAndPreservesMultiTurnHistory() = runTest {
        val vault = InMemorySecureVault().apply {
            saveSecret(AndroidKeystoreVault.KEY_GEMINI_TOKEN, "sample-key")
        }
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(activeProvider = AiProviderType.GEMINI_REST)
            )
        )
        val transport = FakeRemoteTransport(
            geminiReplies = ArrayDeque(
                listOf(
                    Result.success("[TOOL_CALL: {\"name\":\"get_battery_status\",\"args\":{}}]"),
                    Result.success("Your battery is currently at 88% and charging.")
                )
            )
        )
        val router = buildRouter(
            prefs = prefs,
            vault = vault,
            telemetry = FixedTelemetry(DeviceTelemetry(88, true, NetworkTransport.WIFI)),
            transport = transport
        )

        val history = listOf(
            ChatMessage(sender = MessageSender.USER, content = "Hi"),
            ChatMessage(sender = MessageSender.ASSISTANT, content = "At your service."),
            ChatMessage(sender = MessageSender.USER, content = "How is my battery?")
        )

        val result = router.generateReply("How is my battery?", history)
        assertTrue(result.isSuccess)
        assertEquals("Your battery is currently at 88% and charging.", result.getOrNull())
        assertEquals(2, transport.geminiCallCount)
        assertTrue(transport.lastMessages.any { it.content.contains("88%") })
    }

    @Test
    fun onDeviceAssistantSupportsOfflineSummarizationAndPlanning() = runTest {
        val prefs = FakePreferencesRepository(
            JarvisPreferences(
                aiProviderConfig = AiProviderConfig(activeProvider = AiProviderType.ON_DEVICE)
            )
        )
        val router = buildRouter(
            prefs = prefs,
            telemetry = FixedTelemetry(DeviceTelemetry(50, false, NetworkTransport.OFFLINE)),
            transport = FakeRemoteTransport()
        )

        val summary = router.generateReply(
            "Summarize: Kotlin is concise and safe. Jetpack Compose builds native UI declaratively. Material 3 keeps surfaces consistent across Android phones.",
            emptyList()
        )
        assertTrue(summary.isSuccess)
        assertTrue(summary.getOrNull().orEmpty().contains("Offline Summary"))

        val plan = router.generateReply("Plan: release v1", emptyList())
        assertTrue(plan.isSuccess)
        assertTrue(plan.getOrNull().orEmpty().contains("Action Plan"))
    }

    private fun buildRouter(
        prefs: FakePreferencesRepository,
        vault: InMemorySecureVault = InMemorySecureVault(),
        telemetry: SystemTelemetryPort,
        transport: AiRemoteTransport
    ): MultiProviderAiRouter {
        val deviceContext = object : DeviceContextPort {
            override fun getCurrentTimeDate(): String = "Current time: 12:00 PM."
            override fun getBatteryStatus(): String = "Battery is at 88% and currently charging."
            override fun getDeviceDiagnostics(): String = "Device: Pixel\nBattery is at 88%."
        }
        val automation = object : PhoneAutomationPort {
            override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> {
                return Result.success("Executed $actionName")
            }
        }
        return MultiProviderAiRouter(
            preferencesRepository = prefs,
            secureVault = vault,
            telemetryProvider = telemetry,
            onDeviceAssistant = OnDeviceJarvisAssistant(automation, deviceContext),
            toolRegistry = JarvisToolRegistry(automation, deviceContext),
            remoteTransport = transport,
            deviceContext = deviceContext
        )
    }
}

private class FixedTelemetry(
    private val snapshot: DeviceTelemetry
) : SystemTelemetryPort {
    override val telemetry: Flow<DeviceTelemetry> = MutableStateFlow(snapshot).asStateFlow()
    override fun currentSnapshot(): DeviceTelemetry = snapshot
}

private class FakeRemoteTransport(
    private val geminiReplies: ArrayDeque<Result<String>> = ArrayDeque()
) : AiRemoteTransport {
    var geminiCallCount = 0
    var lastMessages: List<RemoteChatMessage> = emptyList()

    override suspend fun chatOllama(
        baseUrl: String,
        model: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("Ollama reply")

    override suspend fun testOllama(baseUrl: String): Result<String> = Result.success("Ollama OK")

    override suspend fun chatOpenAiCompatible(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> = Result.success("OpenAI reply")

    override suspend fun testOpenAiCompatible(baseUrl: String, apiKey: String): Result<String> =
        Result.success("OpenAI OK")

    override suspend fun chatGemini(
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<RemoteChatMessage>
    ): Result<String> {
        geminiCallCount++
        lastMessages = messages
        return if (geminiReplies.isNotEmpty()) geminiReplies.removeFirst() else Result.success("Gemini reply")
    }

    override suspend fun testGemini(model: String, apiKey: String): Result<String> =
        Result.success("Gemini OK")
}
