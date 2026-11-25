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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber

private data class PlannedModuleItem(
    val title: String,
    val description: String,
    val status: String
)

private val plannedModules = listOf(
    PlannedModuleItem(
        title = "AI Reasoning Engine",
        description = "Placeholder for local or free-tier AI response generation.",
        status = "NOT IMPLEMENTED"
    ),
    PlannedModuleItem(
        title = "Speech Recognition",
        description = "Placeholder for Android built-in SpeechRecognizer voice input.",
        status = "NOT IMPLEMENTED"
    ),
    PlannedModuleItem(
        title = "Text-to-Speech",
        description = "Placeholder for Android built-in TextToSpeech voice output.",
        status = "NOT IMPLEMENTED"
    ),
    PlannedModuleItem(
        title = "Persistent Memory",
        description = "Currently uses volatile in-memory storage; Room/DataStore planned.",
        status = "IN-MEMORY ONLY"
    ),
    PlannedModuleItem(
        title = "Phone Automation",
        description = "Placeholder for Android Intents, timers, alarms, and app actions.",
        status = "NOT IMPLEMENTED"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text(
                    text = "FOUNDATION PLACEHOLDER",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Settings and assistant capabilities listed below are architectural placeholders for upcoming development steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(plannedModules) { module ->
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
                                color = JarvisWarningAmber
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
