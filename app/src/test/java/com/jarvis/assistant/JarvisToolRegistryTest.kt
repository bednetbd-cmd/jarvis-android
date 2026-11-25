package com.jarvis.assistant

import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.EndpointUrlValidator
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.ParsedToolCall
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisToolRegistryTest {

    private val recordingAutomation = RecordingPhoneAutomation()
    private val deviceContext = FakeDeviceContext()
    private val registry = JarvisToolRegistry(recordingAutomation, deviceContext)

    @Test
    fun parsesTaggedToolCallWithArguments() {
        val raw = "[TOOL_CALL: {\"name\":\"set_timer\",\"args\":{\"seconds\":300,\"label\":\"Tea\"}}]"
        val parsed = registry.parseToolCall(raw)
        assertNotNull(parsed)
        assertEquals("set_timer", parsed?.name)
        assertEquals("300", parsed?.arguments?.get("seconds"))
        assertEquals("Tea", parsed?.arguments?.get("label"))
    }

    @Test
    fun returnsNullForRegularAssistantRepliesAndUnknownTools() {
        assertNull(registry.parseToolCall("Hello! Here is your answer."))
        assertNull(registry.parseToolCall("[TOOL_CALL: {\"name\":\"delete_system\",\"args\":{}}]"))
    }

    @Test
    fun executesMathAndAutomationToolsCorrectly() = runTest {
        val mathResult = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_CALCULATE_MATH,
                arguments = mapOf("expression" to "(12 + 8) * 3")
            )
        )
        assertTrue(mathResult.isSuccess)
        assertEquals("(12 + 8) * 3 = 60", mathResult.getOrNull())

        val wifiResult = registry.executeTool(
            ParsedToolCall(name = JarvisToolRegistry.TOOL_OPEN_WIFI, arguments = emptyMap())
        )
        assertTrue(wifiResult.isSuccess)
        assertEquals(AndroidPhoneAutomation.ACTION_OPEN_WIFI, recordingAutomation.lastAction)
    }

    @Test
    fun urlValidatorBlocksCleartextPublicHttpAndAllowsPrivateLan() {
        assertTrue(EndpointUrlValidator.validate("https://api.groq.com/openai/v1", allowPrivateHttp = true).isSuccess)
        assertTrue(EndpointUrlValidator.validate("http://192.168.1.50:11434", allowPrivateHttp = true).isSuccess)
        assertTrue(EndpointUrlValidator.validate("http://127.0.0.1:11434", allowPrivateHttp = true).isSuccess)
        assertTrue(EndpointUrlValidator.validate("http://api.example.com/v1", allowPrivateHttp = true).isFailure)
    }
}

private class RecordingPhoneAutomation : PhoneAutomationPort {
    var lastAction: String? = null
    var lastParams: Map<String, String> = emptyMap()

    override suspend fun executeCommand(
        actionName: String,
        params: Map<String, String>
    ): Result<String> {
        lastAction = actionName
        lastParams = params
        return Result.success("Executed $actionName")
    }
}

private class FakeDeviceContext : DeviceContextPort {
    override fun getCurrentTimeDate(): String = "Current time: 10:00 AM. Today's date: Monday, May 4, 2026."
    override fun getBatteryStatus(): String = "Battery is at 90% and currently charging."
    override fun getDeviceDiagnostics(): String = "Device: Test Phone\nAndroid: 14 (API 34)\nBattery is at 90%."
}
