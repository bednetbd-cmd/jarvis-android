package com.jarvis.assistant

import android.app.Application
import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.ai.UnimplementedAiAssistant
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.automation.UnimplementedPhoneAutomation
import com.jarvis.assistant.core.memory.ConversationMemoryPort
import com.jarvis.assistant.core.memory.InMemoryConversationStore
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.core.voice.UnimplementedSpeechRecognizer
import com.jarvis.assistant.core.voice.UnimplementedTextToSpeech
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender

/**
 * Lightweight dependency container for the foundation step.
 * Keeps module wiring explicit so real AI, speech, memory, and automation
 * implementations can be swapped in cleanly in later steps.
 */
class AppContainer {
    val aiAssistant: AiAssistantPort by lazy { UnimplementedAiAssistant() }
    val speechRecognizer: SpeechRecognizerPort by lazy { UnimplementedSpeechRecognizer() }
    val textToSpeech: TextToSpeechPort by lazy { UnimplementedTextToSpeech() }
    val phoneAutomation: PhoneAutomationPort by lazy { UnimplementedPhoneAutomation() }
    val conversationMemory: ConversationMemoryPort by lazy {
        InMemoryConversationStore(
            initialMessages = listOf(
                ChatMessage(
                    sender = MessageSender.SYSTEM_NOTICE,
                    content = "JARVIS foundation initialized. Text input and local chat display are ready. AI reasoning and voice input are not implemented yet."
                )
            )
        )
    }
}

class JarvisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
