package com.jarvis.assistant.core.voice

import android.content.Context
import android.content.Intent
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
    val isListening: StateFlow<Boolean>
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
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

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _isListening.value = false
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
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_CLIENT -> "Voice recognition canceled."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission is required for voice input."
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Network unavailable for speech recognition."
                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Didn't catch that. Please try speaking again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                                "Speech recognizer is busy. Try again in a moment."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "No speech detected."
                            else -> "Voice recognition failed (code $error)."
                        }
                        if (error != SpeechRecognizer.ERROR_CLIENT) {
                            onError(message)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcript = matches?.firstOrNull()?.trim().orEmpty()
                        if (transcript.isNotEmpty()) {
                            onResult(transcript)
                        } else {
                            onError("Didn't catch that. Please try speaking again.")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                _isListening.value = true
                recognizer.startListening(intent)
            }.onFailure { throwable ->
                _isListening.value = false
                onError(throwable.message ?: "Unable to start speech recognition.")
            }
        }
    }

    override fun stopListening() {
        mainHandler.post { stopInternal() }
    }

    override fun release() {
        mainHandler.post {
            _isListening.value = false
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    private fun stopInternal() {
        _isListening.value = false
        runCatching {
            speechRecognizer?.stopListening()
        }
    }
}
