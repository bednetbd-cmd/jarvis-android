package com.jarvis.assistant

import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.ai.ParsedToolCall
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.device.DeviceControlPort
import com.jarvis.assistant.core.device.MediaAction
import com.jarvis.assistant.core.device.VolumeAdjustment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceControlTest {

    @Test
    fun onDeviceAssistantDispatchesFlashlightVolumeBrightnessMediaStorageAndSms() = runTest {
        val fakeAutomation = FakeAutomation()
        val fakeDeviceControl = FakeDeviceControl()
        val fakeContext = FakeContextPort()
        val assistant = OnDeviceJarvisAssistant(fakeAutomation, fakeContext, fakeDeviceControl)

        val flashOn = assistant.generateReply("Flashlight on", emptyList())
        assertTrue(flashOn.isSuccess)
        assertEquals(true, fakeDeviceControl.lastFlashlightState)

        val vol = assistant.generateReply("Set volume 75%", emptyList())
        assertTrue(vol.isSuccess)
        assertEquals(VolumeAdjustment.SET_PERCENT, fakeDeviceControl.lastVolumeAdjustment)
        assertEquals(75, fakeDeviceControl.lastVolumePercent)

        val bright = assistant.generateReply("Set brightness 65%", emptyList())
        assertTrue(bright.isSuccess)
        assertEquals(65, fakeDeviceControl.lastBrightnessPercent)

        val media = assistant.generateReply("Next track", emptyList())
        assertTrue(media.isSuccess)
        assertEquals(MediaAction.NEXT, fakeDeviceControl.lastMediaAction)

        val storage = assistant.generateReply("Storage info", emptyList())
        assertTrue(storage.getOrNull().orEmpty().contains("42.50 GB free"))

        val sms = assistant.generateReply("Text Alice saying I am on my way", emptyList())
        assertTrue(sms.isSuccess)
        assertEquals("Alice", fakeDeviceControl.lastSmsRecipient)
        assertEquals("I am on my way", fakeDeviceControl.lastSmsBody)

        val callContact = assistant.generateReply("Call Alice", emptyList())
        assertTrue(callContact.isSuccess)
        assertEquals(AndroidPhoneAutomation.ACTION_DIAL, fakeAutomation.lastAction)
        assertEquals("+15551234", fakeAutomation.lastParams["number"])
    }

    @Test
    fun toolRegistryExecutesMilestone5ToolsAndPropagatesFailuresHonestly() = runTest {
        val fakeAutomation = FakeAutomation()
        val fakeDeviceControl = FakeDeviceControl(failBrightnessPermission = true)
        val registry = JarvisToolRegistry(fakeAutomation, FakeContextPort(), fakeDeviceControl)

        val appResult = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_LAUNCH_APP,
                arguments = mapOf("app_name" to "Calculator")
            )
        )
        assertTrue(appResult.isSuccess)
        assertEquals("Calculator", fakeDeviceControl.lastLaunchedApp)

        val brightnessFail = registry.executeTool(
            ParsedToolCall(
                name = JarvisToolRegistry.TOOL_SET_BRIGHTNESS,
                arguments = mapOf("percent" to "80")
            )
        )
        assertTrue(brightnessFail.isFailure)
        assertTrue(brightnessFail.exceptionOrNull()?.message.orEmpty().contains("permission", ignoreCase = true))
    }
}

private class FakeContextPort : DeviceContextPort {
    override fun getCurrentTimeDate(): String = "Current time: 2:00 PM."
    override fun getBatteryStatus(): String = "Battery is at 92%."
    override fun getDeviceDiagnostics(): String = "Device: Test\nBattery is at 92%."
}

private class FakeAutomation : PhoneAutomationPort {
    var lastAction: String? = null
    var lastParams: Map<String, String> = emptyMap()

    override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> {
        lastAction = actionName
        lastParams = params
        return Result.success("Executed $actionName")
    }
}

private class FakeDeviceControl(
    private val failBrightnessPermission: Boolean = false
) : DeviceControlPort {
    var lastFlashlightState: Boolean? = null
    var lastLaunchedApp: String? = null
    var lastVolumeAdjustment: VolumeAdjustment? = null
    var lastVolumePercent: Int? = null
    var lastBrightnessPercent: Int? = null
    var lastMediaAction: MediaAction? = null
    var lastSmsRecipient: String? = null
    var lastSmsBody: String? = null

    override fun setFlashlight(enabled: Boolean?): Result<String> {
        lastFlashlightState = enabled ?: !(lastFlashlightState ?: false)
        return Result.success("Flashlight updated")
    }

    override fun launchApp(appNameQuery: String): Result<String> {
        lastLaunchedApp = appNameQuery
        return Result.success("Opening $appNameQuery.")
    }

    override fun listInstalledApps(limit: Int): Result<String> =
        Result.success("Calculator, Camera, Settings")

    override fun adjustVolume(adjustment: VolumeAdjustment, percent: Int?): Result<String> {
        lastVolumeAdjustment = adjustment
        lastVolumePercent = percent
        return Result.success("Volume adjusted")
    }

    override fun setBrightnessPercent(percent: Int): Result<String> {
        if (failBrightnessPermission) {
            return Result.failure(SecurityException("Modify System Settings permission is required."))
        }
        lastBrightnessPercent = percent
        return Result.success("Brightness set to $percent%.")
    }

    override fun getStorageSummary(): String =
        "Internal Storage: 42.50 GB free of 128.00 GB total (85.50 GB used)."

    override fun controlMedia(action: MediaAction): Result<String> {
        lastMediaAction = action
        return Result.success("Media controlled")
    }

    override fun readRecentNotifications(): Result<String> =
        Result.success("Recent notifications:\n• [14:00] Messages: Hey")

    override fun openNotificationListenerSettings(): Result<String> =
        Result.success("Opening Notification Access settings.")

    override fun findContacts(query: String): Result<List<Pair<String, String>>> =
        Result.success(listOf("Alice" to "+15551234"))

    override fun draftSms(recipientOrName: String, body: String): Result<String> {
        lastSmsRecipient = recipientOrName
        lastSmsBody = body
        return Result.success("Opened SMS composer for $recipientOrName")
    }

    override fun shareText(text: String): Result<String> =
        Result.success("Opened Android share sheet.")
}
