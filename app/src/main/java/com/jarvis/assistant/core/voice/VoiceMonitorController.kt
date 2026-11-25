package com.jarvis.assistant.core.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface VoiceMonitorPort {
    val status: StateFlow<VoiceMonitorStatus>
    fun startMode(mode: VoiceMonitorMode): Result<Unit>
    fun stopMonitoring()
    fun updateStatus(
        isServiceRunning: Boolean,
        mode: VoiceMonitorMode,
        awaitingCommand: Boolean = false,
        statusText: String
    )
}

class InMemoryVoiceMonitorController : VoiceMonitorPort {
    private val _status = MutableStateFlow(VoiceMonitorStatus())
    override val status: StateFlow<VoiceMonitorStatus> = _status.asStateFlow()

    override fun startMode(mode: VoiceMonitorMode): Result<Unit> {
        if (mode == VoiceMonitorMode.OFF) {
            stopMonitoring()
            return Result.success(Unit)
        }
        _status.value = VoiceMonitorStatus(
            isServiceRunning = true,
            mode = mode,
            awaitingCommand = false,
            statusText = "${mode.displayName} active"
        )
        return Result.success(Unit)
    }

    override fun stopMonitoring() {
        _status.value = VoiceMonitorStatus()
    }

    override fun updateStatus(
        isServiceRunning: Boolean,
        mode: VoiceMonitorMode,
        awaitingCommand: Boolean,
        statusText: String
    ) {
        _status.value = VoiceMonitorStatus(
            isServiceRunning = isServiceRunning,
            mode = mode,
            awaitingCommand = awaitingCommand,
            statusText = statusText
        )
    }
}

class AndroidVoiceMonitorController(
    context: Context
) : VoiceMonitorPort {

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(VoiceMonitorStatus())
    override val status: StateFlow<VoiceMonitorStatus> = _status.asStateFlow()

    override fun startMode(mode: VoiceMonitorMode): Result<Unit> {
        if (mode == VoiceMonitorMode.OFF) {
            stopMonitoring()
            return Result.success(Unit)
        }

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            return Result.failure(
                SecurityException("Microphone permission is required for hands-free voice mode.")
            )
        }

        return runCatching {
            val intent = VoiceMonitorService.createStartIntent(appContext, mode)
            ContextCompat.startForegroundService(appContext, intent)
            _status.value = VoiceMonitorStatus(
                isServiceRunning = true,
                mode = mode,
                awaitingCommand = false,
                statusText = if (mode == VoiceMonitorMode.WAKE_WORD) {
                    "Listening for \"Hey JARVIS\"..."
                } else {
                    "Continuous voice conversation active"
                }
            )
        }
    }

    override fun stopMonitoring() {
        runCatching {
            appContext.stopService(VoiceMonitorService.createStopIntent(appContext))
        }
        _status.value = VoiceMonitorStatus(
            isServiceRunning = false,
            mode = VoiceMonitorMode.OFF,
            awaitingCommand = false,
            statusText = "Hands-free voice standby"
        )
    }

    override fun updateStatus(
        isServiceRunning: Boolean,
        mode: VoiceMonitorMode,
        awaitingCommand: Boolean,
        statusText: String
    ) {
        _status.value = VoiceMonitorStatus(
            isServiceRunning = isServiceRunning,
            mode = mode,
            awaitingCommand = awaitingCommand,
            statusText = statusText
        )
    }
}
