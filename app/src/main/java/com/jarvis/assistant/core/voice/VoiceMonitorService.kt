package com.jarvis.assistant.core.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApplication
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.domain.model.ChatMessage
import com.jarvis.assistant.domain.model.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class VoiceMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var currentMode: VoiceMonitorMode = VoiceMonitorMode.OFF
    private var awaitingFollowUpCommand: Boolean = false

    private val container by lazy { (application as JarvisApplication).container }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCleanly("Hands-free voice stopped from notification.")
            return START_NOT_STICKY
        }

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            stopCleanly("Microphone permission missing; voice monitor stopped.")
            return START_NOT_STICKY
        }

        val requestedMode = intent?.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { VoiceMonitorMode.valueOf(it) }.getOrNull() }
            ?: VoiceMonitorMode.WAKE_WORD

        if (requestedMode == VoiceMonitorMode.OFF) {
            stopCleanly("Hands-free voice mode turned off.")
            return START_NOT_STICKY
        }

        currentMode = requestedMode
        awaitingFollowUpCommand = false

        val statusLabel = statusLabelFor(requestedMode, awaitingFollowUpCommand)
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(statusLabel),
            fgType
        )

        container.voiceMonitor.updateStatus(
            isServiceRunning = true,
            mode = currentMode,
            awaitingCommand = false,
            statusText = statusLabel
        )

        startMonitorLoop()
        return START_NOT_STICKY
    }

    private fun startMonitorLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive && currentMode != VoiceMonitorMode.OFF) {
                // Never listen while JARVIS is speaking aloud
                while (isActive && container.textToSpeech.isSpeaking.value) {
                    delay(200)
                }
                if (!isActive || currentMode == VoiceMonitorMode.OFF) break

                val prefs = container.preferencesRepository.preferences.first()
                val preferOffline = prefs.preferOfflineVoice ||
                    !container.telemetryProvider.currentSnapshot().isOnline

                val outcome = listenOnce(
                    languageTag = prefs.speechLanguageTag,
                    preferOffline = preferOffline
                )

                when (outcome) {
                    is ListenOutcome.Transcript -> {
                        handleTranscript(outcome.text)
                    }
                    is ListenOutcome.Error -> {
                        if (outcome.fatal) {
                            container.conversationMemory.append(
                                ChatMessage(
                                    sender = MessageSender.SYSTEM_NOTICE,
                                    content = outcome.message
                                )
                            )
                            stopCleanly(outcome.message)
                            break
                        }
                        val backoffMs = if (outcome.message.contains("busy", ignoreCase = true)) {
                            1_200L
                        } else {
                            500L
                        }
                        delay(backoffMs)
                    }
                }
            }
        }
    }

    private suspend fun handleTranscript(transcript: String) {
        if (VoiceCommandParser.isStopSpeakingCommand(transcript)) {
            container.textToSpeech.stop()
            awaitingFollowUpCommand = false
            updateNotificationAndState()
            return
        }

        if (WakeWordMatcher.isExitVoiceModeCommand(transcript)) {
            container.textToSpeech.stop()
            container.conversationMemory.append(
                ChatMessage(sender = MessageSender.USER, content = transcript)
            )
            container.conversationMemory.append(
                ChatMessage(sender = MessageSender.SYSTEM_NOTICE, content = "Hands-free voice mode disabled.")
            )
            stopCleanly("Hands-free voice mode disabled.")
            return
        }

        if (currentMode == VoiceMonitorMode.CONTINUOUS || awaitingFollowUpCommand) {
            awaitingFollowUpCommand = false
            updateNotificationAndState()
            dispatchCommand(transcript)
            return
        }

        when (val match = WakeWordMatcher.match(transcript)) {
            is WakeMatchResult.NoMatch -> Unit
            is WakeMatchResult.WakeOnly -> {
                awaitingFollowUpCommand = true
                updateNotificationAndState()
                val prefs = container.preferencesRepository.preferences.first()
                if (prefs.ttsEnabled && container.textToSpeech.isReady) {
                    val prompt = if (prefs.speechLanguageTag.startsWith("bn", ignoreCase = true)) {
                        "জি, বলুন?"
                    } else {
                        "Yes? Listening."
                    }
                    container.textToSpeech.speak(prompt)
                }
            }
            is WakeMatchResult.WakeWithCommand -> {
                awaitingFollowUpCommand = false
                updateNotificationAndState()
                dispatchCommand(match.command)
            }
        }
    }

    private suspend fun dispatchCommand(commandText: String) {
        container.conversationMemory.append(
            ChatMessage(sender = MessageSender.USER, content = commandText)
        )
        val historySnapshot = container.conversationMemory.messages.first()
        val result = container.aiAssistant.generateReply(commandText, historySnapshot)

        result.onSuccess { reply ->
            container.conversationMemory.append(
                ChatMessage(sender = MessageSender.ASSISTANT, content = reply)
            )
            val prefs = container.preferencesRepository.preferences.first()
            if (prefs.ttsEnabled) {
                container.textToSpeech.speak(reply)
            }
        }.onFailure { error ->
            container.conversationMemory.append(
                ChatMessage(
                    sender = MessageSender.SYSTEM_NOTICE,
                    content = error.message ?: "Unable to process voice command."
                )
            )
        }
    }

    private suspend fun listenOnce(
        languageTag: String,
        preferOffline: Boolean
    ): ListenOutcome = suspendCancellableCoroutine { cont ->
        container.speechRecognizer.startListening(
            languageTag = languageTag,
            preferOffline = preferOffline,
            onResult = { text ->
                if (cont.isActive) cont.resume(ListenOutcome.Transcript(text))
            },
            onError = { message ->
                if (cont.isActive) {
                    val fatal = message.contains("permission", ignoreCase = true) ||
                        message.contains("not available on this device", ignoreCase = true)
                    cont.resume(ListenOutcome.Error(message, fatal))
                }
            }
        )
        cont.invokeOnCancellation {
            container.speechRecognizer.stopListening()
        }
    }

    private fun updateNotificationAndState() {
        val statusText = statusLabelFor(currentMode, awaitingFollowUpCommand)
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(statusText))
        }
        container.voiceMonitor.updateStatus(
            isServiceRunning = true,
            mode = currentMode,
            awaitingCommand = awaitingFollowUpCommand,
            statusText = statusText
        )
    }

    private fun statusLabelFor(mode: VoiceMonitorMode, awaiting: Boolean): String {
        return when {
            awaiting -> "Wake word heard — listening for command..."
            mode == VoiceMonitorMode.WAKE_WORD -> "Listening for \"Hey JARVIS\"..."
            mode == VoiceMonitorMode.CONTINUOUS -> "Continuous voice conversation active"
            else -> "Hands-free voice standby"
        }
    }

    private fun stopCleanly(statusText: String) {
        currentMode = VoiceMonitorMode.OFF
        awaitingFollowUpCommand = false
        monitorJob?.cancel()
        container.speechRecognizer.stopListening()
        container.voiceMonitor.updateStatus(
            isServiceRunning = false,
            mode = VoiceMonitorMode.OFF,
            awaitingCommand = false,
            statusText = statusText
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        container.speechRecognizer.stopListening()
        container.voiceMonitor.updateStatus(
            isServiceRunning = false,
            mode = VoiceMonitorMode.OFF,
            awaitingCommand = false,
            statusText = "Hands-free voice standby"
        )
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "JARVIS Voice Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when JARVIS is actively listening for \"Hey JARVIS\" or continuous voice."
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            101,
            createStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("JARVIS Voice Monitor")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop Voice", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private sealed interface ListenOutcome {
        data class Transcript(val text: String) : ListenOutcome
        data class Error(val message: String, val fatal: Boolean) : ListenOutcome
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_voice_monitor_channel"
        private const val NOTIFICATION_ID = 4001
        private const val EXTRA_MODE = "extra_voice_mode"
        private const val ACTION_STOP = "com.jarvis.assistant.action.STOP_VOICE_MONITOR"

        fun createStartIntent(context: Context, mode: VoiceMonitorMode): Intent {
            return Intent(context, VoiceMonitorService::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, VoiceMonitorService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
