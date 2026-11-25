package com.jarvis.assistant.core.voice

import java.util.Locale

enum class VoiceMonitorMode(
    val displayName: String,
    val badge: String
) {
    OFF("Off", "OFF"),
    WAKE_WORD("Wake Word (\"Hey JARVIS\")", "HEY JARVIS"),
    CONTINUOUS("Continuous Conversation", "HANDS-FREE")
}

data class VoiceMonitorStatus(
    val isServiceRunning: Boolean = false,
    val mode: VoiceMonitorMode = VoiceMonitorMode.OFF,
    val awaitingCommand: Boolean = false,
    val statusText: String = "Hands-free voice standby"
)

sealed interface WakeMatchResult {
    data object NoMatch : WakeMatchResult
    data object WakeOnly : WakeMatchResult
    data class WakeWithCommand(val command: String) : WakeMatchResult
}

object WakeWordMatcher {

    private val wakePrefixes = listOf(
        "hey jarvis",
        "hi jarvis",
        "hello jarvis",
        "ok jarvis",
        "okay jarvis",
        "jarvis",
        "হেই জার্ভিস",
        "হ্যালো জার্ভিস",
        "জার্ভিস"
    )

    private val exitPhrases = setOf(
        "exit voice mode",
        "stop voice mode",
        "disable voice mode",
        "stop wake word",
        "stop continuous mode",
        "stop continuous voice",
        "turn off hands free",
        "ভয়েস মোড বন্ধ করো",
        "হ্যান্ডস ফ্রি বন্ধ করো"
    )

    fun match(transcript: String): WakeMatchResult {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return WakeMatchResult.NoMatch

        val lower = trimmed.lowercase(Locale.getDefault())
        for (prefix in wakePrefixes) {
            if (lower == prefix || lower.matches(Regex("^${Regex.escape(prefix)}[,.:!?।\\s]*$"))) {
                return WakeMatchResult.WakeOnly
            }
            if (lower.startsWith(prefix)) {
                val remainder = trimmed.substring(prefix.length)
                    .trimStart(',', '.', ':', ';', '!', '?', '।', ' ', '\t')
                    .trim()
                val nextCharIsBoundary = trimmed.length == prefix.length ||
                    !trimmed[prefix.length].isLetterOrDigit()
                if (nextCharIsBoundary) {
                    return if (remainder.isEmpty()) {
                        WakeMatchResult.WakeOnly
                    } else {
                        WakeMatchResult.WakeWithCommand(remainder)
                    }
                }
            }
        }
        return WakeMatchResult.NoMatch
    }

    fun isExitVoiceModeCommand(transcript: String): Boolean {
        val normalized = transcript
            .trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[.!?,।]+$"), "")
            .replace(Regex("\\s+"), " ")
        return normalized in exitPhrases
    }
}
