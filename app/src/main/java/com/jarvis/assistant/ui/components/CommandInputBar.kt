package com.jarvis.assistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber

@Composable
fun CommandInputBar(
    inputText: String,
    isListening: Boolean,
    isSpeaking: Boolean = false,
    partialTranscript: String = "",
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit,
    onStopSpeakingClick: () -> Unit = {},
    onVisionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val canSend = inputText.isNotBlank()
    val accent = MaterialTheme.colorScheme.primary

    val placeholderText = when {
        isListening && partialTranscript.isNotBlank() -> partialTranscript
        isListening -> "Listening..."
        else -> "Enter command or message..."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = JarvisSurface,
        border = BorderStroke(1.dp, JarvisSurfaceBorder.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMicClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isListening) accent else JarvisSurfaceVariant,
                    contentColor = if (isListening) JarvisBackground else accent
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Voice input"
                )
            }

            if (isSpeaking) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onStopSpeakingClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = JarvisWarningAmber.copy(alpha = 0.2f),
                        contentColor = JarvisWarningAmber
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = "Stop speaking"
                    )
                }
            }

            if (onVisionClick != null) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onVisionClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = JarvisSurfaceVariant,
                        contentColor = accent
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CenterFocusWeak,
                        contentDescription = "Open JARVIS Vision Scanner"
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isListening) accent else JarvisTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = false,
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) onSendClick()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = JarvisBackground,
                    unfocusedContainerColor = JarvisBackground,
                    focusedBorderColor = accent,
                    unfocusedBorderColor = JarvisSurfaceBorder,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary,
                    cursorColor = accent
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = if (canSend) accent else JarvisSurfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(
                    onClick = onSendClick,
                    enabled = canSend
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message",
                        tint = if (canSend) JarvisBackground else Color(0xFF475569)
                    )
                }
            }
        }
    }
}
