package com.jarvis.assistant

import android.app.Application
import android.content.Context
import com.jarvis.assistant.core.ai.AiAssistantPort
import com.jarvis.assistant.core.ai.AiRemoteTransport
import com.jarvis.assistant.core.ai.AndroidDeviceContextReader
import com.jarvis.assistant.core.ai.DeviceContextPort
import com.jarvis.assistant.core.ai.HttpRemoteAiTransport
import com.jarvis.assistant.core.ai.JarvisToolRegistry
import com.jarvis.assistant.core.ai.MultiProviderAiRouter
import com.jarvis.assistant.core.ai.OnDeviceJarvisAssistant
import com.jarvis.assistant.core.automation.AndroidPhoneAutomation
import com.jarvis.assistant.core.automation.PhoneAutomationPort
import com.jarvis.assistant.core.device.AndroidDeviceController
import com.jarvis.assistant.core.device.DeviceControlPort
import com.jarvis.assistant.core.memory.ConversationMemoryPort
import com.jarvis.assistant.core.memory.FileConversationStore
import com.jarvis.assistant.core.productivity.AndroidCalendarAdapter
import com.jarvis.assistant.core.productivity.CalendarPort
import com.jarvis.assistant.core.productivity.ProductivityPort
import com.jarvis.assistant.core.productivity.ReminderSchedulerPort
import com.jarvis.assistant.core.productivity.RoomProductivityRepository
import com.jarvis.assistant.core.productivity.WorkManagerReminderScheduler
import com.jarvis.assistant.core.security.AndroidBiometricAuthenticator
import com.jarvis.assistant.core.security.BiometricAuthPort
import com.jarvis.assistant.core.tools.AndroidClipboardAdapter
import com.jarvis.assistant.core.tools.AndroidWorkspaceDocumentsManager
import com.jarvis.assistant.core.tools.ClipboardPort
import com.jarvis.assistant.core.tools.WorkspaceDocumentsPort
import com.jarvis.assistant.core.voice.AndroidSpeechRecognizer
import com.jarvis.assistant.core.voice.AndroidTextToSpeech
import com.jarvis.assistant.core.voice.AndroidVoiceMonitorController
import com.jarvis.assistant.core.voice.SpeechRecognizerPort
import com.jarvis.assistant.core.voice.TextToSpeechPort
import com.jarvis.assistant.core.voice.VoiceMonitorPort
import com.jarvis.assistant.data.local.JarvisDatabase
import com.jarvis.assistant.core.vision.AndroidVisionAnalyzer
import com.jarvis.assistant.core.vision.VisionAnalysisPort
import com.jarvis.assistant.core.vision.VisionConsentPolicy
import com.jarvis.assistant.core.web.AndroidWebHttpTransport
import com.jarvis.assistant.core.web.FreeWebIntelligenceService
import com.jarvis.assistant.core.web.WebHttpTransport
import com.jarvis.assistant.core.web.WebIntelligencePort
import com.jarvis.assistant.data.device.AndroidSystemTelemetryProvider
import com.jarvis.assistant.data.device.SystemTelemetryPort
import com.jarvis.assistant.data.preferences.DataStoreJarvisPreferencesRepository
import com.jarvis.assistant.data.preferences.JarvisPreferencesPort
import com.jarvis.assistant.data.security.AndroidKeystoreVault
import com.jarvis.assistant.data.security.SecureVaultPort
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val preferencesRepository: JarvisPreferencesPort by lazy {
        DataStoreJarvisPreferencesRepository(appContext)
    }

    val secureVault: SecureVaultPort by lazy {
        AndroidKeystoreVault(appContext)
    }

    val telemetryProvider: SystemTelemetryPort by lazy {
        AndroidSystemTelemetryProvider(appContext)
    }

    val phoneAutomation: PhoneAutomationPort by lazy {
        AndroidPhoneAutomation(appContext)
    }

    val deviceControl: DeviceControlPort by lazy {
        AndroidDeviceController(appContext)
    }

    val deviceContext: DeviceContextPort by lazy {
        AndroidDeviceContextReader(appContext)
    }

    val webHttpTransport: WebHttpTransport by lazy {
        AndroidWebHttpTransport()
    }

    val webIntelligence: WebIntelligencePort by lazy {
        FreeWebIntelligenceService(appContext, webHttpTransport, telemetryProvider)
    }

    val database: JarvisDatabase by lazy {
        JarvisDatabase.getInstance(appContext)
    }

    val reminderScheduler: ReminderSchedulerPort by lazy {
        WorkManagerReminderScheduler(appContext)
    }

    val calendarPort: CalendarPort by lazy {
        AndroidCalendarAdapter(appContext)
    }

    val productivityRepository: ProductivityPort by lazy {
        RoomProductivityRepository(
            dao = database.productivityDao(),
            reminderScheduler = reminderScheduler,
            calendarPort = calendarPort,
            deviceContext = deviceContext,
            deviceControl = deviceControl
        )
    }

    val clipboardPort: ClipboardPort by lazy {
        AndroidClipboardAdapter(appContext)
    }

    val workspaceDocuments: WorkspaceDocumentsPort by lazy {
        AndroidWorkspaceDocumentsManager(appContext)
    }

    val biometricAuth: BiometricAuthPort by lazy {
        AndroidBiometricAuthenticator(appContext)
    }

    val toolRegistry: JarvisToolRegistry by lazy {
        JarvisToolRegistry(phoneAutomation, deviceContext, deviceControl, webIntelligence, productivityRepository, clipboardPort)
    }

    val remoteAiTransport: AiRemoteTransport by lazy {
        HttpRemoteAiTransport()
    }

    val onDeviceAssistant: OnDeviceJarvisAssistant by lazy {
        OnDeviceJarvisAssistant(phoneAutomation, deviceContext, deviceControl, webIntelligence, productivityRepository, clipboardPort)
    }

    val aiAssistant: AiAssistantPort by lazy {
        MultiProviderAiRouter(
            preferencesRepository = preferencesRepository,
            secureVault = secureVault,
            telemetryProvider = telemetryProvider,
            onDeviceAssistant = onDeviceAssistant,
            toolRegistry = toolRegistry,
            remoteTransport = remoteAiTransport,
            deviceContext = deviceContext
        )
    }

    val speechRecognizer: SpeechRecognizerPort by lazy {
        AndroidSpeechRecognizer(appContext)
    }

    val textToSpeech: TextToSpeechPort by lazy {
        AndroidTextToSpeech(appContext)
    }

    val voiceMonitor: VoiceMonitorPort by lazy {
        AndroidVoiceMonitorController(appContext)
    }

    val visionConsentPolicy: VisionConsentPolicy by lazy {
        VisionConsentPolicy(
            preferencesRepository = preferencesRepository,
            secureVault = secureVault,
            telemetryProvider = telemetryProvider,
            remoteTransport = remoteAiTransport
        )
    }

    val visionAnalyzer: VisionAnalysisPort by lazy {
        AndroidVisionAnalyzer(appContext, visionConsentPolicy)
    }

    val conversationMemory: ConversationMemoryPort by lazy {
        FileConversationStore(
            context = appContext,
            defaultMessages = listOf(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    content = "JARVIS online. Ask a question, tap the mic to speak, or type \"help\" for built-in voice, math, diagnostics, and phone commands. You can also connect a free Gemini, Groq/OpenAI-compatible, or local Ollama model in Settings."
                )
            ),
            secureVault = secureVault
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
