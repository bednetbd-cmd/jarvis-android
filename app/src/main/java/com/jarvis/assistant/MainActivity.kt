package com.jarvis.assistant

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jarvis.assistant.domain.model.JarvisPreferences
import com.jarvis.assistant.ui.navigation.JarvisNavGraph
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val container = (application as JarvisApplication).container

        setContent {
            val preferences by container.preferencesRepository.preferences
                .collectAsStateWithLifecycle(initialValue = JarvisPreferences())

            JarvisTheme(accentTheme = preferences.accentTheme) {
                JarvisNavGraph(container = container)
            }
        }
    }
}
