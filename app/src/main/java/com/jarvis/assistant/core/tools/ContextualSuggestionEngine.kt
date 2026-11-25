package com.jarvis.assistant.core.tools

import com.jarvis.assistant.domain.model.DeviceTelemetry

data class ContextualSuggestion(
    val label: String,
    val command: String
)

object ContextualSuggestionEngine {

    fun generate(
        telemetry: DeviceTelemetry,
        hourOfDay: Int,
        pendingTaskCount: Int,
        activeReminderCount: Int,
        pendingHabitCount: Int,
        unreadNotificationCount: Int = 0
    ): List<ContextualSuggestion> {
        val suggestions = mutableListOf<ContextualSuggestion>()

        if (telemetry.batteryPercent in 0..20 && !telemetry.isCharging) {
            suggestions.add(ContextualSuggestion("Battery & Power", "Device info"))
        }

        if (hourOfDay in 5..11) {
            suggestions.add(ContextualSuggestion("Morning Briefing", "Daily briefing"))
        } else if (hourOfDay in 17..22) {
            suggestions.add(ContextualSuggestion("Evening Briefing", "Daily briefing"))
        }

        if (pendingTaskCount > 0) {
            suggestions.add(ContextualSuggestion("Tasks ($pendingTaskCount)", "My tasks"))
        }

        if (activeReminderCount > 0) {
            suggestions.add(ContextualSuggestion("Reminders ($activeReminderCount)", "My reminders"))
        }

        if (pendingHabitCount > 0) {
            suggestions.add(ContextualSuggestion("Habits ($pendingHabitCount)", "My habits"))
        }

        if (unreadNotificationCount > 0) {
            suggestions.add(ContextualSuggestion("Notifications ($unreadNotificationCount)", "Read notifications"))
        }

        if (telemetry.isOnline) {
            suggestions.add(ContextualSuggestion("Latest News", "Latest news"))
            suggestions.add(ContextualSuggestion("Weather", "Weather in Dhaka"))
        } else {
            suggestions.add(ContextualSuggestion("Offline Unit Convert", "Convert 10 km to miles"))
            suggestions.add(ContextualSuggestion("Scientific Math", "Calculate sqrt(144) + 5!"))
        }

        return suggestions.distinctBy { it.command }.take(4)
    }
}
