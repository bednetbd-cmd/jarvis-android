package com.jarvis.assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.assistant.core.device.NotificationBufferStore
import com.jarvis.assistant.core.voice.TtsLanguageAvailability
import com.jarvis.assistant.core.voice.VoiceMonitorMode
import com.jarvis.assistant.domain.model.AiProviderType
import com.jarvis.assistant.domain.model.AssistantPersonality
import com.jarvis.assistant.domain.model.JarvisAccentTheme
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSuccessGreen
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber
import java.util.Locale

private data class PermissionEntry(
    val title: String,
    val description: String,
    val granted: Boolean,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

private val speechLanguages = listOf(
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "bn-BD" to "Bangla (BD)"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = uiState.preferences
    val aiConfig = prefs.aiProviderConfig
    val accent = MaterialTheme.colorScheme.primary
    val snackbarHostState = remember { SnackbarHostState() }

    var ollamaUrlDraft by remember(aiConfig.ollamaBaseUrl) { mutableStateOf(aiConfig.ollamaBaseUrl) }
    var ollamaModelDraft by remember(aiConfig.ollamaModel) { mutableStateOf(aiConfig.ollamaModel) }
    var openAiUrlDraft by remember(aiConfig.openAiBaseUrl) { mutableStateOf(aiConfig.openAiBaseUrl) }
    var openAiModelDraft by remember(aiConfig.openAiModel) { mutableStateOf(aiConfig.openAiModel) }
    var openAiTokenDraft by remember { mutableStateOf("") }
    var geminiModelDraft by remember(aiConfig.geminiModel) { mutableStateOf(aiConfig.geminiModel) }
    var geminiTokenDraft by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.noticeEvents.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = JarvisSurfaceBorder,
        focusedTextColor = JarvisTextPrimary,
        unfocusedTextColor = JarvisTextPrimary,
        focusedLabelColor = accent,
        unfocusedLabelColor = JarvisTextMuted,
        cursorColor = accent
    )

    var permissionRefreshCounter by remember { mutableStateOf(0) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefreshCounter++
        viewModel.showNotice(
            if (granted) "Contacts access granted." else "Contacts permission denied."
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefreshCounter++
        viewModel.showNotice(
            if (granted) "Camera access granted." else "Camera permission denied."
        )
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefreshCounter++
        viewModel.showNotice(
            if (granted) "Calendar access granted." else "Calendar permission denied."
        )
    }

    val permissions = remember(permissionRefreshCounter, context) {
        buildList {
            add(
                PermissionEntry(
                    title = "Microphone (RECORD_AUDIO)",
                    description = "Required for voice commands, live audio waveform, and wake-word mode.",
                    granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                )
            )
            add(
                PermissionEntry(
                    title = "Camera (CAMERA)",
                    description = "Used for live CameraX OCR document, QR code, and object scanning.",
                    granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED,
                    actionLabel = "Request Camera Access",
                    onAction = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    PermissionEntry(
                        title = "Notifications (POST_NOTIFICATIONS)",
                        description = "Used for foreground voice monitor status and scheduled WorkManager reminders.",
                        granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                )
            }
            add(
                PermissionEntry(
                    title = "Contacts (READ_CONTACTS)",
                    description = "Allows JARVIS to find contacts by name for dialer & SMS drafts.",
                    granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED,
                    actionLabel = "Request Contacts Access",
                    onAction = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                )
            )
            add(
                PermissionEntry(
                    title = "Calendar (READ_CALENDAR)",
                    description = "Allows JARVIS to include upcoming events in your Daily Briefing and calendar queries.",
                    granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALENDAR
                    ) == PackageManager.PERMISSION_GRANTED,
                    actionLabel = "Request Calendar Access",
                    onAction = { calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR) }
                )
            )
            add(
                PermissionEntry(
                    title = "Screen Brightness (WRITE_SETTINGS)",
                    description = "Optional special access needed to change device brightness directly.",
                    granted = Settings.System.canWrite(context),
                    actionLabel = "Configure Brightness Access",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            )
            add(
                PermissionEntry(
                    title = "Notification Reader (NotificationListenerService)",
                    description = "Optional Android access to read recent notifications on your command.",
                    granted = NotificationBufferStore.isListenerEnabled(context),
                    actionLabel = "Open Notification Access",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "SETTINGS & AI ENGINE",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = accent
                                )
                            }
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
                Text(
                    text = "AI INTELLIGENCE ENGINE (\$0 FREE PROVIDERS)",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Active AI Provider",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AiProviderType.entries.forEach { provider ->
                                FilterChip(
                                    selected = aiConfig.activeProvider == provider,
                                    onClick = { viewModel.setActiveAiProvider(provider) },
                                    label = { Text(provider.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent,
                                        containerColor = JarvisSurfaceVariant,
                                        labelColor = JarvisTextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when (aiConfig.activeProvider) {
                                AiProviderType.ON_DEVICE ->
                                    "100% offline deterministic engine for math, diagnostics, summarization, task checklists, and Android phone actions. No API key or internet required."
                                AiProviderType.LOCAL_OLLAMA ->
                                    "Connects over local Wi-Fi/LAN (or Termux localhost) to your own free Ollama server. Zero cloud quota or cost."
                                AiProviderType.OPENAI_COMPATIBLE ->
                                    "Supports free-tier OpenAI-compatible APIs (e.g. Groq, OpenRouter free models, or local LM Studio). Free tiers enforce per-minute/daily rate limits."
                                AiProviderType.GEMINI_REST ->
                                    "Uses Google AI Studio Gemini Free-Tier REST API. Requires your free API key and enforces free-tier rate/quota limits."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Assistant Personality",
                            style = MaterialTheme.typography.titleSmall,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistantPersonality.entries.forEach { personality ->
                                FilterChip(
                                    selected = aiConfig.personality == personality,
                                    onClick = { viewModel.setAssistantPersonality(personality) },
                                    label = { Text(personality.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent,
                                        containerColor = JarvisSurfaceVariant,
                                        labelColor = JarvisTextSecondary
                                    )
                                )
                            }
                        }

                        when (aiConfig.activeProvider) {
                            AiProviderType.ON_DEVICE -> Unit

                            AiProviderType.LOCAL_OLLAMA -> {
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = ollamaUrlDraft,
                                    onValueChange = { ollamaUrlDraft = it },
                                    label = { Text("Ollama Base URL (http://localhost or LAN IP)") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = ollamaModelDraft,
                                    onValueChange = { ollamaModelDraft = it },
                                    label = { Text("Ollama Model (e.g. llama3.2, qwen2.5)") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.saveOllamaConfig(ollamaUrlDraft, ollamaModelDraft) },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Ollama Config")
                                }
                            }

                            AiProviderType.OPENAI_COMPATIBLE -> {
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = openAiUrlDraft,
                                    onValueChange = { openAiUrlDraft = it },
                                    label = { Text("HTTPS Base URL (e.g. https://api.groq.com/openai/v1)") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = openAiModelDraft,
                                    onValueChange = { openAiModelDraft = it },
                                    label = { Text("Model ID (e.g. llama-3.1-8b-instant)") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.saveOpenAiConfig(openAiUrlDraft, openAiModelDraft) },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Endpoint & Model")
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (aiConfig.hasOpenAiToken) {
                                        "API Key Status: ENCRYPTED IN ANDROID KEYSTORE"
                                    } else {
                                        "API Key Status: NOT SET"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (aiConfig.hasOpenAiToken) JarvisSuccessGreen else JarvisWarningAmber
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = openAiTokenDraft,
                                    onValueChange = { openAiTokenDraft = it },
                                    label = { Text("API Key (stored encrypted locally)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.saveProviderToken(AiProviderType.OPENAI_COMPATIBLE, openAiTokenDraft)
                                            openAiTokenDraft = ""
                                        },
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save Key")
                                    }
                                    if (aiConfig.hasOpenAiToken) {
                                        OutlinedButton(
                                            onClick = { viewModel.requestClearProviderToken(AiProviderType.OPENAI_COMPATIBLE) },
                                            border = BorderStroke(1.dp, JarvisWarningAmber.copy(alpha = 0.7f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisWarningAmber),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Clear Key")
                                        }
                                    }
                                }
                            }

                            AiProviderType.GEMINI_REST -> {
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = geminiModelDraft,
                                    onValueChange = { geminiModelDraft = it },
                                    label = { Text("Gemini Model (e.g. gemini-2.5-flash)") },
                                    singleLine = true,
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { viewModel.saveGeminiModel(geminiModelDraft) },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Gemini Model")
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (aiConfig.hasGeminiToken) {
                                        "Gemini Key Status: ENCRYPTED IN ANDROID KEYSTORE"
                                    } else {
                                        "Gemini Key Status: NOT SET"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (aiConfig.hasGeminiToken) JarvisSuccessGreen else JarvisWarningAmber
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = geminiTokenDraft,
                                    onValueChange = { geminiTokenDraft = it },
                                    label = { Text("Free Gemini API Key (Google AI Studio)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.saveProviderToken(AiProviderType.GEMINI_REST, geminiTokenDraft)
                                            geminiTokenDraft = ""
                                        },
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save Key")
                                    }
                                    if (aiConfig.hasGeminiToken) {
                                        OutlinedButton(
                                            onClick = { viewModel.requestClearProviderToken(AiProviderType.GEMINI_REST) },
                                            border = BorderStroke(1.dp, JarvisWarningAmber.copy(alpha = 0.7f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisWarningAmber),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Clear Key")
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::testAiProviderConnection,
                            enabled = !uiState.isTestingAi,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.isTestingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = JarvisBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (uiState.isTestingAi) "Testing Connection..." else "Test Provider Connection")
                        }

                        val testStatus = uiState.aiTestStatus
                        if (!testStatus.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val statusColor = when (uiState.aiTestSuccess) {
                                true -> JarvisSuccessGreen
                                false -> JarvisWarningAmber
                                null -> JarvisTextSecondary
                            }
                            Text(
                                text = testStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "HUD APPEARANCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Accent Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            JarvisAccentTheme.entries.forEach { theme ->
                                val selected = prefs.accentTheme == theme
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setAccentTheme(theme) },
                                    label = { Text(theme.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent,
                                        containerColor = JarvisSurfaceVariant,
                                        labelColor = JarvisTextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Live Voice Waveform",
                                style = MaterialTheme.typography.bodyLarge,
                                color = JarvisTextPrimary
                            )
                            Switch(
                                checked = prefs.showWaveform,
                                onCheckedChange = viewModel::setShowWaveform,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisBackground,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "VOICE & SPEECH",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }

            item {
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
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Voice Replies (Text-to-Speech)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Speak JARVIS replies aloud.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                            }
                            Switch(
                                checked = prefs.ttsEnabled,
                                onCheckedChange = viewModel::setTtsEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisBackground,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Recognition & Voice Language",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            speechLanguages.forEach { (tag, label) ->
                                FilterChip(
                                    selected = prefs.speechLanguageTag == tag,
                                    onClick = { viewModel.setSpeechLanguageTag(tag) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent,
                                        containerColor = JarvisSurfaceVariant,
                                        labelColor = JarvisTextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = String.format(Locale.US, "Speech Speed: %.2fx", prefs.speechRate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                        Slider(
                            value = prefs.speechRate,
                            onValueChange = viewModel::setSpeechRate,
                            valueRange = 0.6f..1.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = String.format(Locale.US, "Voice Pitch: %.2fx", prefs.speechPitch),
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextSecondary
                        )
                        Slider(
                            value = prefs.speechPitch,
                            onValueChange = viewModel::setSpeechPitch,
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Prefer Offline Voice Recognition",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Requires Android offline speech pack for selected language.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                            Switch(
                                checked = prefs.preferOfflineVoice,
                                onCheckedChange = viewModel::setPreferOfflineVoice,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisBackground,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisSurfaceVariant
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        val diag = uiState.voiceDiagnostics
                        Text(
                            text = "Voice Diagnostics: STT ${if (diag.speechRecognitionAvailable) "Available" else "Missing"} · " +
                                "On-Device ${if (diag.onDeviceRecognitionAvailable) "Supported" else "System-Dependent"} · " +
                                "TTS (${prefs.speechLanguageTag}): ${diag.activeLanguageAvailability.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (diag.activeLanguageAvailability == TtsLanguageAvailability.READY) {
                                JarvisSuccessGreen
                            } else {
                                JarvisWarningAmber
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::speakTestSample,
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test Voice (TTS)")
                            }
                            if (uiState.isSpeaking) {
                                OutlinedButton(
                                    onClick = viewModel::stopSpeaking,
                                    border = BorderStroke(1.dp, JarvisWarningAmber.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisWarningAmber),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Stop Speaking")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Hands-Free Voice Mode (Foreground Service)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VoiceMonitorMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.voiceMonitorStatus.mode == mode,
                                    onClick = { viewModel.setVoiceMonitorMode(mode) },
                                    label = { Text(mode.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent,
                                        containerColor = JarvisSurfaceVariant,
                                        labelColor = JarvisTextSecondary
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${uiState.voiceMonitorStatus.statusText}. When active, Android shows a persistent microphone notification with a Stop button. Audio is never captured when Off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisTextMuted
                        )
                    }
                }
            }

            item {
                Text(
                    text = "SECURITY & PRIVACY VAULT",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Biometric App Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = uiState.biometricCapability.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                            Switch(
                                checked = prefs.biometricLockEnabled,
                                onCheckedChange = viewModel::setBiometricLockEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisBackground,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisSurfaceVariant
                                )
                            )
                        }

                        if (uiState.biometricCapability == com.jarvis.assistant.core.security.BiometricCapability.AVAILABLE) {
                            OutlinedButton(
                                onClick = viewModel::lockAppNow,
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Lock JARVIS Vault Now")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Confirm Sensitive Actions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Prompt before calls, SMS drafts, history wipes, or Keystore key removal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                            Switch(
                                checked = prefs.confirmSensitiveActions,
                                onCheckedChange = viewModel::setConfirmSensitiveActions,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisBackground,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisSurfaceVariant
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Encrypted Conversation History",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisTextPrimary
                                )
                                Text(
                                    text = "Encrypts chat logs at rest using Android Keystore AES-256-GCM.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisTextMuted
                                )
                            }
                            Switch(
                                checked = prefs.encryptConversationHistory,
                                onCheckedChange = viewModel::setEncryptConversationHistory,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = JarvisBackground,
                                    checkedTrackColor = accent,
                                    uncheckedThumbColor = JarvisTextMuted,
                                    uncheckedTrackColor = JarvisSurfaceVariant
                                )
                            )
                        }

                        Text(
                            text = "Sensor Transparency: Mic & Camera are never accessed silently. Active states display live HUD badges and foreground notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisSuccessGreen
                        )
                    }
                }
            }

            item {
                Text(
                    text = "PERMISSIONS DASHBOARD",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }

            items(permissions) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (entry.granted) "GRANTED" else "NOT GRANTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.granted) accent else JarvisWarningAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                        if (!entry.granted && entry.actionLabel != null && entry.onAction != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = entry.onAction,
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                            ) {
                                Text(entry.actionLabel)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                ) {
                    Text("Open Android App Permissions")
                }
            }
        }
    }
}
