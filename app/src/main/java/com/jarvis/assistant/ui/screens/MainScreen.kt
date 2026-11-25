package com.jarvis.assistant.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.assistant.core.voice.VoiceMonitorMode
import com.jarvis.assistant.core.vision.VisionScanMode
import com.jarvis.assistant.ui.components.ArcReactorIndicator
import com.jarvis.assistant.ui.components.ChatMessageList
import com.jarvis.assistant.ui.components.CommandInputBar
import com.jarvis.assistant.ui.components.JarvisTopBar
import com.jarvis.assistant.ui.components.TelemetryHudBar
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenVision: (VisionScanMode) -> Unit = {},
    onOpenWorkbench: (WorkbenchTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMonitorMode by remember { mutableStateOf<VoiceMonitorMode?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingMode = pendingMonitorMode
        pendingMonitorMode = null
        if (granted) {
            if (pendingMode != null) {
                viewModel.setVoiceMonitorMode(pendingMode)
            } else {
                viewModel.onMicrophoneClicked()
            }
        } else {
            viewModel.showNotice("Microphone permission is required for voice commands.")
        }
    }

    val monitorPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val pendingMode = pendingMonitorMode ?: VoiceMonitorMode.WAKE_WORD
        pendingMonitorMode = null
        val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (audioGranted) {
            viewModel.setVoiceMonitorMode(pendingMode)
        } else {
            viewModel.showNotice("Microphone permission is required for hands-free voice mode.")
        }
    }

    fun requestVoiceMonitorMode(mode: VoiceMonitorMode) {
        if (mode == VoiceMonitorMode.OFF || uiState.voiceMonitorStatus.mode == mode) {
            viewModel.setVoiceMonitorMode(VoiceMonitorMode.OFF)
            return
        }
        val permissionsNeeded = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsNeeded.isEmpty()) {
            viewModel.setVoiceMonitorMode(mode)
        } else {
            pendingMonitorMode = mode
            monitorPermissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.noticeEvents.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openVisionEvents.collect { mode ->
            onOpenVision(mode)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.openWorkbenchEvents.collect { tab ->
            onOpenWorkbench(tab)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground),
        containerColor = JarvisBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            JarvisTopBar(
                isTtsEnabled = uiState.isTtsEnabled,
                onToggleTts = viewModel::toggleTts,
                onOpenSettings = onNavigateToSettings
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            TelemetryHudBar(
                telemetry = uiState.telemetry,
                visualState = uiState.visualState,
                providerBadge = uiState.providerBadgeLabel,
                onEmergencyStop = viewModel::emergencyStop,
                isVoiceMonitorActive = uiState.voiceMonitorStatus.isServiceRunning
            )

            HandsFreeModeStrip(
                activeMode = uiState.voiceMonitorStatus.mode,
                statusText = uiState.voiceMonitorStatus.statusText,
                onSelectMode = ::requestVoiceMonitorMode
            )

            val requestOrToggleVoice = {
                if (uiState.isSpeaking) {
                    viewModel.stopSpeaking()
                } else {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        viewModel.onMicrophoneClicked()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                ArcReactorIndicator(
                    visualState = uiState.visualState,
                    rmsLevel = uiState.rmsLevel,
                    showWaveform = uiState.preferences.showWaveform,
                    onCoreClick = requestOrToggleVoice
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = JarvisSurfaceBorder.copy(alpha = 0.5f)
            )

            ChatMessageList(
                messages = uiState.messages,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (uiState.contextualSuggestions.isNotEmpty()) {
                val accent = MaterialTheme.colorScheme.primary
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.contextualSuggestions) { suggestion ->
                        Surface(
                            onClick = { viewModel.sendMessage(overrideText = suggestion.command) },
                            shape = RoundedCornerShape(10.dp),
                            color = JarvisSurfaceVariant,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = suggestion.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            CommandInputBar(
                inputText = uiState.inputText,
                isListening = uiState.isListening,
                isSpeaking = uiState.isSpeaking,
                partialTranscript = uiState.partialTranscript,
                onInputChange = viewModel::onInputChanged,
                onSendClick = { viewModel.sendMessage() },
                onMicClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        viewModel.onMicrophoneClicked()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopSpeakingClick = viewModel::stopSpeaking,
                onVisionClick = { onOpenVision(VisionScanMode.OCR) }
            )
        }
    }
}

@Composable
private fun HandsFreeModeStrip(
    activeMode: VoiceMonitorMode,
    statusText: String,
    onSelectMode: (VoiceMonitorMode) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Rounded.Hearing,
                contentDescription = "Hands-free voice monitor",
                tint = if (activeMode == VoiceMonitorMode.OFF) JarvisTextMuted else accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (activeMode == VoiceMonitorMode.OFF) "Hands-Free: Off" else statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (activeMode == VoiceMonitorMode.OFF) JarvisTextMuted else accent,
                maxLines = 1
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(VoiceMonitorMode.WAKE_WORD, VoiceMonitorMode.CONTINUOUS).forEach { mode ->
                val selected = activeMode == mode
                Surface(
                    onClick = { onSelectMode(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) accent.copy(alpha = 0.2f) else JarvisSurfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (selected) accent else JarvisSurfaceBorder
                    )
                ) {
                    Text(
                        text = mode.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) accent else JarvisTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
