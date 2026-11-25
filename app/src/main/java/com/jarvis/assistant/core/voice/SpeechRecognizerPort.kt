package com.jarvis.assistant.core.voice

/**
 * Contract for future Android built-in SpeechRecognizer integration.
 * Currently unimplemented in the foundation phase.
 */
interface SpeechRecognizerPort {
    val isAvailable: Boolean
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
}

class UnimplementedSpeechRecognizer : SpeechRecognizerPort {
    override val isAvailable: Boolean = false

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onError("Voice input is not implemented yet. Speech recognition will be added in a later step.")
    }

    override fun stopListening() = Unit
}
