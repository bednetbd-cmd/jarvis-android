package com.jarvis.assistant

import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.voice.SpeechTextSanitizer
import com.jarvis.assistant.core.voice.VoiceCommandParser
import com.jarvis.assistant.core.voice.WakeMatchResult
import com.jarvis.assistant.core.voice.WakeWordMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSystemTest {

    @Test
    fun detectsEnglishAndBanglaStopSpeakingCommands() {
        assertTrue(VoiceCommandParser.isStopSpeakingCommand("stop speaking"))
        assertTrue(VoiceCommandParser.isStopSpeakingCommand("Hey JARVIS stop speaking."))
        assertTrue(VoiceCommandParser.isStopSpeakingCommand("থামো"))
        assertTrue(VoiceCommandParser.isStopSpeakingCommand("কথা বলা বন্ধ করো"))
        assertFalse(VoiceCommandParser.isStopSpeakingCommand("What time is it?"))
    }

    @Test
    fun sanitizesMarkdownAndChunksLongResponsesForTts() {
        val raw = "**Status:** Check `battery` at https://example.com/status.\n• All systems normal."
        val cleaned = SpeechTextSanitizer.sanitizeForSpeech(raw)
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("`"))
        assertTrue(cleaned.contains("link"))

        val longText = "Sentence one is clear. ".repeat(80)
        val chunks = SpeechTextSanitizer.chunkForTts(longText, maxChunkLength = 300)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 300 })
    }

    @Test
    fun onDeviceAssistantSupportsBanglaVoiceCommands() = runTest {
        var lastAction: String? = null
        val automation = object : PhoneAutomationPort {
            override suspend fun executeCommand(actionName: String, params: Map<String, String>): Result<String> {
                lastAction = actionName
                return Result.success("Executed $actionName")
            }
        }
        val contextPort = object : DeviceContextPort {
            override fun getCurrentTimeDate(): String = "Current time: 3:15 PM."
            override fun getBatteryStatus(): String = "Battery is at 77%."
            override fun getDeviceDiagnostics(): String = "Device OK"
        }
        val assistant = OnDeviceJarvisAssistant(automation, contextPort)

        val timeReply = assistant.generateReply("এখন সময় কত", emptyList())
        assertEquals("Current time: 3:15 PM.", timeReply.getOrNull())

        val wifiReply = assistant.generateReply("ওয়াইফাই খোলো", emptyList())
        assertTrue(wifiReply.isSuccess)
        assertEquals(AndroidPhoneAutomation.ACTION_OPEN_WIFI, lastAction)
    }

    @Test
    fun wakeWordMatcherRecognizesBareWakeInlineCommandBanglaAndExitPhrases() {
        assertEquals(WakeMatchResult.WakeOnly, WakeWordMatcher.match("Hey JARVIS"))
        assertEquals(WakeMatchResult.WakeOnly, WakeWordMatcher.match("হেই জার্ভিস"))

        val inline = WakeWordMatcher.match("Hey JARVIS, what time is it?")
        assertEquals(WakeMatchResult.WakeWithCommand("what time is it?"), inline)

        val banglaInline = WakeWordMatcher.match("হ্যালো জার্ভিস ব্যাটারি কত")
        assertEquals(WakeMatchResult.WakeWithCommand("ব্যাটারি কত"), banglaInline)

        assertEquals(WakeMatchResult.NoMatch, WakeWordMatcher.match("Just talking in the room"))
        assertTrue(WakeWordMatcher.isExitVoiceModeCommand("exit voice mode"))
        assertTrue(WakeWordMatcher.isExitVoiceModeCommand("ভয়েস মোড বন্ধ করো"))
        assertFalse(WakeWordMatcher.isExitVoiceModeCommand("Hey JARVIS"))
    }
}
