package com.jarvis.assistant.core.device

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArrayList

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postTimeMillis: Long
)

object NotificationBufferStore {
    private const val MAX_ENTRIES = 15
    private val entries = CopyOnWriteArrayList<CapturedNotification>()

    fun record(sbn: StatusBarNotification) {
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        entries.removeAll { it.key == sbn.key }
        entries.add(
            0,
            CapturedNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                postTimeMillis = sbn.postTime
            )
        )
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.lastIndex)
        }
    }

    fun removeByKey(key: String) {
        entries.removeAll { it.key == key }
    }

    fun recent(): List<CapturedNotification> = entries.toList()

    fun isListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val component = ComponentName(context, JarvisNotificationListenerService::class.java)
            .flattenToString()
        return flat.split(':').any { it.equals(component, ignoreCase = true) }
    }
}

class JarvisNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        runCatching {
            activeNotifications?.forEach { NotificationBufferStore.record(it) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) {
            NotificationBufferStore.record(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) {
            NotificationBufferStore.removeByKey(sbn.key)
        }
    }
}
