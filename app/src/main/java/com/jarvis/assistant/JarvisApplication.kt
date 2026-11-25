package com.jarvis.assistant

import android.app.Application
import android.content.Context
import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.memory.ConversationMemoryPort
import com.jarvis.assistant.core.memory.FileConversationStore
import com.jarvis.assistant.core.voice.AndroidSpeechRecognizer
import com.jarvis.assistant.core.voice.AndroidTextToSpeech
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val phoneAutomation: PhoneAutomationPort by lazy {
        AndroidPhoneAutomation(appContext)
    }

    val aiAssistant: AiAssistantPort by lazy {
        OnDeviceJarvisAssistant(appContext, phoneAutomation)
    }

    val speechRecognizer: SpeechRecognizerPort by lazy {
        AndroidSpeechRecognizer(appContext)
    }

    val textToSpeech: TextToSpeechPort by lazy {
        AndroidTextToSpeech(appContext)
    }

    val conversationMemory: ConversationMemoryPort by lazy {
        FileConversationStore(
            context = appContext,
            defaultMessages = listOf(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    content = "JARVIS online. Ask a question, tap the mic to speak, or type \"help\" for built-in voice, math, diagnostics, and phone commands."
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
        container = AppContainer(this)
    }
}
