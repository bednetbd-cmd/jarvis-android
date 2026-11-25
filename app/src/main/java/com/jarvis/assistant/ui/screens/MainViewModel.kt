package com.jarvis.assistant.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.AppContainer
import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.memory.ConversationMemoryPort
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.domain.model.AssistantVisualState
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val aiAssistant: AiAssistantPort,
    private val speechRecognizer: SpeechRecognizerPort,
    private val textToSpeech: TextToSpeechPort,
    private val conversationMemory: ConversationMemoryPort
) : ViewModel() {

    private val inputText = MutableStateFlow("")
    private val isProcessing = MutableStateFlow(false)
    private val ttsEnabled = MutableStateFlow(true)

    private val _noticeEvents = Channel<String>(Channel.BUFFERED)
    val noticeEvents = _noticeEvents.receiveAsFlow()

    private val visualStateFlow = combine(
        speechRecognizer.isListening,
        textToSpeech.isSpeaking,
        isProcessing
    ) { listening, speaking, processing ->
        when {
            listening -> AssistantVisualState.LISTENING
            processing -> AssistantVisualState.THINKING
            speaking -> AssistantVisualState.SPEAKING
            else -> AssistantVisualState.STANDBY
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        visualStateFlow,
        conversationMemory.messages,
        inputText,
        ttsEnabled,
        speechRecognizer.isListening
    ) { visualState, messages, draft, speakReplies, listening ->
        MainUiState(
            visualState = visualState,
            messages = messages,
            inputText = draft,
            isListening = listening,
            isTtsEnabled = speakReplies,
            isAiConnected = aiAssistant.isConfigured,
            isVoiceAvailable = speechRecognizer.isAvailable,
            aiEngineLabel = aiAssistant.engineLabel
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            isAiConnected = aiAssistant.isConfigured,
            isVoiceAvailable = speechRecognizer.isAvailable,
            aiEngineLabel = aiAssistant.engineLabel
        )
    )

    fun onInputChanged(value: String) {
        inputText.value = value
    }

    fun toggleTts() {
        val next = !ttsEnabled.value
        ttsEnabled.value = next
        if (!next) {
            textToSpeech.stop()
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        ttsEnabled.value = enabled
        if (!enabled) {
            textToSpeech.stop()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            textToSpeech.stop()
            conversationMemory.clear()
            _noticeEvents.send("Conversation history cleared.")
        }
    }

    fun showNotice(message: String) {
        viewModelScope.launch {
            _noticeEvents.send(message)
        }
    }

    fun sendMessage(overrideText: String? = null) {
        val trimmed = (overrideText ?: inputText.value).trim()
        if (trimmed.isEmpty()) return

        if (overrideText == null) {
            inputText.value = ""
        }

        viewModelScope.launch {
            isProcessing.value = true
            try {
                val userMessage = ChatMessage(
                    sender = MessageSender.USER,
                    content = trimmed
                )
                conversationMemory.append(userMessage)

                val historySnapshot = conversationMemory.messages.first()
                val replyResult = aiAssistant.generateReply(trimmed, historySnapshot)

                replyResult.onSuccess { replyText ->
                    conversationMemory.append(
                        ChatMessage(
                            sender = MessageSender.ASSISTANT,
                            content = replyText
                        )
                    )
                    if (ttsEnabled.value) {
                        textToSpeech.speak(replyText)
                    }
                }.onFailure { error ->
                    conversationMemory.append(
                        ChatMessage(
                            sender = MessageSender.SYSTEM_NOTICE,
                            content = error.message ?: "Unable to process request."
                        )
                    )
                }
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun onMicrophoneClicked() {
        if (speechRecognizer.isListening.value) {
            speechRecognizer.stopListening()
            return
        }

        textToSpeech.stop()
        speechRecognizer.startListening(
            onResult = { recognizedText ->
                sendMessage(overrideText = recognizedText)
            },
            onError = { reason ->
                showNotice(reason)
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.release()
        textToSpeech.release()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        aiAssistant = container.aiAssistant,
                        speechRecognizer = container.speechRecognizer,
                        textToSpeech = container.textToSpeech,
                        conversationMemory = container.conversationMemory
                    ) as T
                }
            }
        }
    }
}
