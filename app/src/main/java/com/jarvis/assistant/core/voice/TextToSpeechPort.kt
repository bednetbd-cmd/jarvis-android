package com.jarvis.assistant.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

interface TextToSpeechPort {
    val isReady: Boolean
    val isSpeaking: StateFlow<Boolean>
    fun speak(text: String)
    fun stop()
    fun release()
}

class AndroidTextToSpeech(
    context: Context
) : TextToSpeechPort {

    private var tts: TextToSpeech? = null
    @Volatile
    private var initialized = false

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    override val isReady: Boolean
        get() = initialized

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                val langResult = engine?.setLanguage(Locale.getDefault())
                initialized = langResult != TextToSpeech.LANG_MISSING_DATA &&
                    langResult != TextToSpeech.LANG_NOT_SUPPORTED
                engine?.setSpeechRate(1.02f)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                initialized = false
            }
        }
    }

    override fun speak(text: String) {
        if (!initialized || text.isBlank()) return
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        _isSpeaking.value = false
        tts?.stop()
    }

    override fun release() {
        _isSpeaking.value = false
        initialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
