package com.jarvis.assistant

import com.jarvis.assistant.domain.model.DeviceTelemetry
import com.jarvis.assistant.domain.model.JarvisAccentTheme
import com.jarvis.assistant.domain.model.JarvisPreferences
import com.jarvis.assistant.domain.model.NetworkTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisPreferencesTest {

    @Test
    fun speechRateAndPitchAreClampedWithinSafeBounds() {
        assertEquals(0.6f, JarvisPreferences.clampSpeechRate(0.1f), 0.001f)
        assertEquals(1.8f, JarvisPreferences.clampSpeechRate(3.0f), 0.001f)
        assertEquals(1.1f, JarvisPreferences.clampSpeechRate(1.1f), 0.001f)

        assertEquals(0.7f, JarvisPreferences.clampSpeechPitch(0.2f), 0.001f)
        assertEquals(1.5f, JarvisPreferences.clampSpeechPitch(2.4f), 0.001f)
        assertEquals(1.0f, JarvisPreferences.clampSpeechPitch(1.0f), 0.001f)
    }

    @Test
    fun defaultPreferencesUseArcCyanAndOnlineTelemetryCheck() {
        val prefs = JarvisPreferences()
        assertEquals(JarvisAccentTheme.ARC_CYAN, prefs.accentTheme)
        assertTrue(prefs.ttsEnabled)
        assertTrue(prefs.showWaveform)

        val offline = DeviceTelemetry(batteryPercent = 50, isCharging = false, networkTransport = NetworkTransport.OFFLINE)
        val wifi = DeviceTelemetry(batteryPercent = 80, isCharging = true, networkTransport = NetworkTransport.WIFI)
        assertFalse(offline.isOnline)
        assertTrue(wifi.isOnline)
    }
}
