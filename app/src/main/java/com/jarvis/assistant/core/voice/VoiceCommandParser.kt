package com.jarvis.assistant.core.voice

import java.util.Locale

enum class TtsLanguageAvailability(val label: String) {
    INITIALIZING("Initializing TTS engine..."),
    READY("Voice pack installed & ready"),
    MISSING_DATA("Language voice data not downloaded on device"),
    NOT_SUPPORTED("Not supported by current Android TTS engine")
}

data class VoiceDiagnostics(
    val speechRecognitionAvailable: Boolean = false,
    val onDeviceRecognitionAvailable: Boolean = false,
    val ttsReady: Boolean = false,
    val activeLanguageAvailability: TtsLanguageAvailability = TtsLanguageAvailability.INITIALIZING
)

object VoiceCommandParser {

    private val stopSpeechPhrases = setOf(
        "stop speaking",
        "stop talking",
        "stop voice",
        "be quiet",
        "quiet",
        "shut up",
        "silence",
        "cancel speech",
        "mute jarvis",
        "থামো",
        "চুপ করো",
        "কথা বলা বন্ধ করো",
        "কথা বন্ধ করো",
        "বলা বন্ধ করো"
    )

    fun isStopSpeakingCommand(text: String): Boolean {
        val normalized = text
            .trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("[.!?,।]+$"), "")
            .replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return false
        return normalized in stopSpeechPhrases ||
            normalized.startsWith("jarvis stop speaking") ||
            normalized.startsWith("hey jarvis stop speaking") ||
            normalized.startsWith("jarvis stop talking")
    }
}

object SpeechTextSanitizer {

    fun sanitizeForSpeech(rawText: String): String {
        if (rawText.isBlank()) return ""
        return rawText
            .replace(Regex("```[a-zA-Z0-9_-]*\\n?"), " ")
            .replace("```", " ")
            .replace(Regex("\\*\\*|__|~~|`"), "")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s+"), "")
            .replace(Regex("(?m)^\\s*[•\\-*]\\s+"), "")
            .replace(Regex("https?://\\S+"), "link")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun chunkForTts(text: String, maxChunkLength: Int = 1_200): List<String> {
        val clean = sanitizeForSpeech(text)
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= maxChunkLength) return listOf(clean)

        val chunks = mutableListOf<String>()
        val sentences = clean.split(Regex("(?<=[.!?।])\\s+"))
        val current = StringBuilder()

        for (sentence in sentences) {
            if (sentence.length > maxChunkLength) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                var index = 0
                while (index < sentence.length) {
                    val end = minOf(index + maxChunkLength, sentence.length)
                    chunks.add(sentence.substring(index, end).trim())
                    index = end
                }
            } else if (current.length + sentence.length + 1 > maxChunkLength) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks.filter { it.isNotEmpty() }
    }
}
