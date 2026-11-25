package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber

private data class ModuleStatusItem(
    val title: String,
    val description: String,
    val status: String,
    val isActive: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val modules = listOf(
        ModuleStatusItem(
            title = "On-Device Assistant",
            description = "${uiState.aiEngineLabel}: time, date, math calculator, live battery/device diagnostics, and command routing.",
            status = if (uiState.isAiConnected) "ACTIVE" else "OFFLINE",
            isActive = uiState.isAiConnected
        ),
        ModuleStatusItem(
            title = "Speech Recognition",
            description = "Uses Android's built-in SpeechRecognizer service when you tap the microphone button.",
            status = if (uiState.isVoiceAvailable) "AVAILABLE" else "UNAVAILABLE",
            isActive = uiState.isVoiceAvailable
        ),
        ModuleStatusItem(
            title = "Persistent Memory",
            description = "Saves conversation history locally on your device in private app storage.",
            status = "ACTIVE",
            isActive = true
        ),
        ModuleStatusItem(
            title = "Phone Automation",
            description = "Built-in Android Intents for alarms, timers, Wi-Fi/Bluetooth/System settings, camera, web search, and dialer.",
            status = "ACTIVE",
            isActive = true
        ),
        ModuleStatusItem(
            title = "Cloud LLM Integration",
            description = "Optional slot for connecting an external AI model in a future step without modifying UI architecture.",
            status = "PLANNED",
            isActive = false
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "SETTINGS",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = JarvisCyan
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = JarvisBackground,
                        titleContentColor = JarvisTextPrimary
                    )
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = JarvisSurfaceBorder.copy(alpha = 0.65f)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Voice Replies (Text-to-Speech)",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Speak JARVIS responses aloud using Android's built-in TTS engine.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                        }
                        Switch(
                            checked = uiState.isTtsEnabled,
                            onCheckedChange = viewModel::setTtsEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisBackground,
                                checkedTrackColor = JarvisCyan,
                                uncheckedThumbColor = JarvisTextMuted,
                                uncheckedTrackColor = JarvisSurfaceVariant
                            )
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Conversation History",
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.messages.size} messages stored locally on device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.clearHistory()
                                onNavigateBack()
                            },
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = JarvisCyan
                            )
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SYSTEM MODULES",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisCyan
                )
            }

            items(modules) { module ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = module.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                            Text(
                                text = module.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (module.isActive) JarvisCyan else JarvisWarningAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    }
                }
            }
        }
    }
}
