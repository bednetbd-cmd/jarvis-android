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
    val languageAvailability: StateFlow<TtsLanguageAvailability>

    fun updateSettings(speechRate: Float, pitch: Float, languageTag: String)
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
    @Volatile
    private var currentRate = 1.02f
    @Volatile
    private var currentPitch = 1.0f
    @Volatile
    private var currentLocale: Locale = Locale.getDefault()
    @Volatile
    private var activeFinalUtteranceId: String? = null

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _languageAvailability = MutableStateFlow(TtsLanguageAvailability.INITIALIZING)
    override val languageAvailability: StateFlow<TtsLanguageAvailability> =
        _languageAvailability.asStateFlow()

    override val isReady: Boolean
        get() = initialized

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                initialized = true
                val engine = tts
                engine?.setSpeechRate(currentRate)
                engine?.setPitch(currentPitch)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == null || utteranceId == activeFinalUtteranceId) {
                            _isSpeaking.value = false
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == null || utteranceId == activeFinalUtteranceId) {
                            _isSpeaking.value = false
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == null || utteranceId == activeFinalUtteranceId) {
                            _isSpeaking.value = false
                        }
                    }
                })
                applyLocale(currentLocale)
            } else {
                initialized = false
                _languageAvailability.value = TtsLanguageAvailability.NOT_SUPPORTED
            }
        }
    }

    override fun updateSettings(speechRate: Float, pitch: Float, languageTag: String) {
        currentRate = speechRate.coerceIn(0.6f, 1.8f)
        currentPitch = pitch.coerceIn(0.7f, 1.5f)
        val parsedLocale = Locale.forLanguageTag(languageTag)
        if (parsedLocale.language.isNotBlank()) {
            currentLocale = parsedLocale
        }
        val engine = tts ?: return
        if (initialized) {
            engine.setSpeechRate(currentRate)
            engine.setPitch(currentPitch)
            applyLocale(currentLocale)
        }
    }

    override fun speak(text: String) {
        if (!initialized || text.isBlank()) return
        val engine = tts ?: return
        val chunks = SpeechTextSanitizer.chunkForTts(text)
        if (chunks.isEmpty()) return

        val utteranceIds = chunks.map { UUID.randomUUID().toString() }
        activeFinalUtteranceId = utteranceIds.last()
        _isSpeaking.value = true

        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, queueMode, null, utteranceIds[index])
        }
    }

    override fun stop() {
        activeFinalUtteranceId = null
        _isSpeaking.value = false
        tts?.stop()
    }

    override fun release() {
        activeFinalUtteranceId = null
        _isSpeaking.value = false
        initialized = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun applyLocale(targetLocale: Locale) {
        val engine = tts ?: return
        val availability = engine.isLanguageAvailable(targetLocale)
        when (availability) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                engine.setLanguage(targetLocale)
                _languageAvailability.value = TtsLanguageAvailability.READY
            }
            TextToSpeech.LANG_MISSING_DATA -> {
                engine.setLanguage(Locale.getDefault())
                _languageAvailability.value = TtsLanguageAvailability.MISSING_DATA
            }
            else -> {
                engine.setLanguage(Locale.getDefault())
                _languageAvailability.value = TtsLanguageAvailability.NOT_SUPPORTED
            }
        }
    }
}
