package com.jarvis.assistant

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jarvis.assistant.ui.navigation.JarvisNavGraph
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val container = (application as JarvisApplication).container

        setContent {
            JarvisTheme {
                JarvisNavGraph(container = container)
            }
        }
    }
}
