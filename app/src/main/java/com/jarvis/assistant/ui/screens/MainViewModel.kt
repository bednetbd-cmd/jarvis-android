package com.jarvis.assistant.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.AppContainer
import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.memory.ConversationMemoryPort
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
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
    private val conversationMemory: ConversationMemoryPort
) : ViewModel() {

    private val inputText = MutableStateFlow("")
    private val visualState = MutableStateFlow(AssistantVisualState.STANDBY)

    private val _noticeEvents = Channel<String>(Channel.BUFFERED)
    val noticeEvents = _noticeEvents.receiveAsFlow()

    val uiState: StateFlow<MainUiState> = combine(
        visualState,
        conversationMemory.messages,
        inputText
    ) { state, messages, draft ->
        MainUiState(
            visualState = state,
            messages = messages,
            inputText = draft,
            isAiConnected = aiAssistant.isConfigured,
            isVoiceAvailable = speechRecognizer.isAvailable
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            isAiConnected = aiAssistant.isConfigured,
            isVoiceAvailable = speechRecognizer.isAvailable
        )
    )

    fun onInputChanged(value: String) {
        inputText.value = value
    }

    fun sendMessage() {
        val trimmed = inputText.value.trim()
        if (trimmed.isEmpty()) return

        inputText.value = ""

        viewModelScope.launch {
            val userMessage = ChatMessage(
                sender = MessageSender.USER,
                content = trimmed
            )
            conversationMemory.append(userMessage)

            val historySnapshot = conversationMemory.messages.first()
            val replyResult = aiAssistant.generateReply(trimmed, historySnapshot)

            replyResult.onFailure { error ->
                conversationMemory.append(
                    ChatMessage(
                        sender = MessageSender.SYSTEM_NOTICE,
                        content = error.message
                            ?: "Message recorded locally. AI engine is not connected yet."
                    )
                )
            }
        }
    }

    fun onMicrophoneClicked() {
        speechRecognizer.startListening(
            onResult = { recognizedText ->
                inputText.value = recognizedText
            },
            onError = { reason ->
                viewModelScope.launch {
                    _noticeEvents.send(reason)
                }
            }
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        aiAssistant = container.aiAssistant,
                        speechRecognizer = container.speechRecognizer,
                        conversationMemory = container.conversationMemory
                    ) as T
                }
            }
        }
    }
}
