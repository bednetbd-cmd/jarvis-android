package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.assistant.ui.components.ArcReactorIndicator
import com.jarvis.assistant.ui.components.ChatMessageList
import com.jarvis.assistant.ui.components.CommandInputBar
import com.jarvis.assistant.ui.components.JarvisTopBar
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.noticeEvents.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground),
        containerColor = JarvisBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            JarvisTopBar(onOpenSettings = onNavigateToSettings)
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                ArcReactorIndicator(visualState = uiState.visualState)
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

            CommandInputBar(
                inputText = uiState.inputText,
                onInputChange = viewModel::onInputChanged,
                onSendClick = viewModel::sendMessage,
                onMicClick = viewModel::onMicrophoneClicked
            )
        }
    }
}
