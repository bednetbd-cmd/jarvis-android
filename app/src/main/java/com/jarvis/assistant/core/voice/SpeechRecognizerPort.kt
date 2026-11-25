package com.jarvis.assistant.core.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

interface SpeechRecognizerPort {
    val isAvailable: Boolean
    val isOnDeviceAvailable: Boolean
    val isListening: StateFlow<Boolean>
    val rmsLevel: StateFlow<Float>
    val partialTranscript: StateFlow<String>

    fun startListening(
        languageTag: String = Locale.getDefault().toLanguageTag(),
        preferOffline: Boolean = false,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    )

    fun stopListening()
    fun release()
}

class AndroidSpeechRecognizer(
    context: Context
) : SpeechRecognizerPort {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    override val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    override val isOnDeviceAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    override fun startListening(
        languageTag: String,
        preferOffline: Boolean,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                resetListeningState()
                onError("Speech recognition service is not available on this device.")
                return@post
            }

            if (_isListening.value) {
                stopInternal()
                return@post
            }

            runCatching {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _partialTranscript.value = ""
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        resetListeningState()
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_CLIENT -> "Voice recognition canceled."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is required for voice input."
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                if (preferOffline) {
                                    "Offline speech pack for $languageTag is unavailable without network. Download offline language data in Android settings."
                                } else {
                                    "Network unavailable for speech recognition. Try enabling Prefer Offline Voice in Settings if an offline language pack is installed."
                                }
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Didn't catch that. Please try speaking again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Speech recognizer is busy. Try again in a moment."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "No speech detected."
                            ERROR_LANGUAGE_NOT_SUPPORTED,
                            ERROR_LANGUAGE_UNAVAILABLE ->
                                "Speech recognition pack for $languageTag is missing or unsupported on this device."
                            else -> "Voice recognition failed (code $error)."
                        }
                        if (error != SpeechRecognizer.ERROR_CLIENT) {
                            onError(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        resetListeningState()
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcript = matches?.firstOrNull()?.trim().orEmpty()
                        if (transcript.isNotEmpty()) {
                            onResult(transcript)
                        } else {
                            onError("Didn't catch that. Please try speaking again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotEmpty()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    if (preferOffline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                _partialTranscript.value = ""
                _isListening.value = true
                recognizer.startListening(intent)
            }.onFailure { throwable ->
                resetListeningState()
                onError(throwable.message ?: "Unable to start speech recognition.")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post { stopInternal() }
    }

    override fun release() {
        mainHandler.post {
            resetListeningState()
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun stopInternal() {
        resetListeningState()
        runCatching {
            speechRecognizer?.stopListening()
        }
    }

    private fun resetListeningState() {
        _isListening.value = false
        _rmsLevel.value = 0f
        _partialTranscript.value = ""
    }

    private companion object {
        // Android API 31+ SpeechRecognizer constants defined explicitly for minSdk 26 compatibility
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
