package com.jarvis.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisTextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisTopBar(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    ColumnWrapper(modifier = modifier) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(JarvisCyan)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "JARVIS",
                        style = MaterialTheme.typography.headlineSmall,
                        color = JarvisTextPrimary
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = JarvisCyan
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = JarvisBackground,
                titleContentColor = JarvisTextPrimary,
                actionIconContentColor = JarvisCyan
            )
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = JarvisSurfaceBorder.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun ColumnWrapper(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        content()
    }
}
