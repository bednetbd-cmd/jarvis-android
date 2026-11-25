package com.jarvis.assistant.core.voice

/**
 * Contract for future Android built-in TextToSpeech integration.
 * Currently unimplemented in the foundation phase.
 */
interface TextToSpeechPort {
    val isReady: Boolean
    fun speak(text: String)
    fun stop()
    fun release()
}

class UnimplementedTextToSpeech : TextToSpeechPort {
    override val isReady: Boolean = false
    override fun speak(text: String) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
