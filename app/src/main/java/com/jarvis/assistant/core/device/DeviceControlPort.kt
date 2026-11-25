package com.jarvis.assistant.core.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.ContactsContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MediaAction {
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS
}

enum class VolumeAdjustment {
    UP,
    DOWN,
    MUTE,
    UNMUTE,
    SET_PERCENT
}

interface DeviceControlPort {
    fun setFlashlight(enabled: Boolean?): Result<String>
    fun launchApp(appNameQuery: String): Result<String>
    fun listInstalledApps(limit: Int = 15): Result<String>
    fun adjustVolume(adjustment: VolumeAdjustment, percent: Int? = null): Result<String>
    fun setBrightnessPercent(percent: Int): Result<String>
    fun getStorageSummary(): String
    fun controlMedia(action: MediaAction): Result<String>
    fun readRecentNotifications(): Result<String>
    fun openNotificationListenerSettings(): Result<String>
    fun findContacts(query: String): Result<List<Pair<String, String>>>
    fun draftSms(recipientOrName: String, body: String): Result<String>
    fun shareText(text: String): Result<String>
}

class AndroidDeviceController(
    context: Context
) : DeviceControlPort {

    private val appContext = context.applicationContext
    @Volatile
    private var lastTorchState: Boolean = false

    override fun setFlashlight(enabled: Boolean?): Result<String> {
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return Result.failure(IllegalStateException("Camera hardware service is unavailable."))

        return runCatching {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                hasFlash && isBack
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: throw IllegalStateException("This device does not have a controllable camera flashlight.")

            val targetState = enabled ?: !lastTorchState
            cameraManager.setTorchMode(cameraId, targetState)
            lastTorchState = targetState
            if (targetState) "Flashlight turned ON." else "Flashlight turned OFF."
        }.recoverCatching { error ->
            if (error is IllegalStateException) throw error
            throw IllegalStateException(
                "Unable to control flashlight right now (${error.message ?: "camera busy"}).",
                error
            )
        }
    }

    override fun launchApp(appNameQuery: String): Result<String> {
        val query = appNameQuery.trim()
        if (query.isEmpty()) {
            return Result.failure(IllegalArgumentException("Specify which installed app to open."))
        }

        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = queryLauncherActivities(pm, mainIntent)

        val candidates = resolveInfos.map { info ->
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            val pkg = info.activityInfo?.packageName.orEmpty()
            label to pkg
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }

        val match = candidates.firstOrNull { it.first.equals(query, ignoreCase = true) }
            ?: candidates.firstOrNull { it.first.startsWith(query, ignoreCase = true) }
            ?: candidates.firstOrNull { it.first.contains(query, ignoreCase = true) }
            ?: return Result.failure(
                IllegalStateException("Could not find an installed app matching \"$query\".")
            )

        val launchIntent = pm.getLaunchIntentForPackage(match.second)
            ?: return Result.failure(
                IllegalStateException("${match.first} cannot be launched directly.")
            )

        return runCatching {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launchIntent)
            "Opening ${match.first}."
        }
    }

    override fun listInstalledApps(limit: Int): Result<String> {
        val pm = appContext.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val labels = queryLauncherActivities(pm, mainIntent)
            .mapNotNull { it.loadLabel(pm)?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (labels.isEmpty()) {
            return Result.failure(IllegalStateException("No launchable apps were returned by Android."))
        }
        val preview = labels.take(limit).joinToString(", ")
        return Result.success("Found ${labels.size} installed launchable apps. Sample: $preview. Say \"open <app name>\" to launch one.")
    }

    override fun adjustVolume(adjustment: VolumeAdjustment, percent: Int?): Result<String> {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Result.failure(IllegalStateException("Audio service unavailable."))

        val stream = AudioManager.STREAM_MUSIC
        val maxVol = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)

        return runCatching {
            when (adjustment) {
                VolumeAdjustment.UP -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
                VolumeAdjustment.DOWN -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }
                VolumeAdjustment.MUTE -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                }
                VolumeAdjustment.UNMUTE -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                }
                VolumeAdjustment.SET_PERCENT -> {
                    val clampedPct = (percent ?: 50).coerceIn(0, 100)
                    val targetIndex = ((clampedPct / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
                    audioManager.setStreamVolume(stream, targetIndex, AudioManager.FLAG_SHOW_UI)
                }
            }
            val currentVol = audioManager.getStreamVolume(stream)
            val currentPct = ((currentVol * 100.0) / maxVol).toInt()
            "Media volume is now at $currentPct% ($currentVol/$maxVol)."
        }.recoverCatching { error ->
            throw IllegalStateException(
                "Android restricted changing volume directly (${error.message ?: "permission/DND active"}).",
                error
            )
        }
    }

    override fun setBrightnessPercent(percent: Int): Result<String> {
        val clamped = percent.coerceIn(5, 100)
        if (!Settings.System.canWrite(appContext)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { appContext.startActivity(intent) }
            return Result.failure(
                SecurityException(
                    "Modify System Settings permission is required to adjust screen brightness. Opening Android permission screen so you can allow it."
                )
            )
        }

        return runCatching {
            val resolver = appContext.contentResolver
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val brightnessValue = ((clamped / 100.0) * 255).toInt().coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
            "Screen brightness set to $clamped%."
        }
    }

    override fun getStorageSummary(): String {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0L)
            val fmt = DecimalFormat("#0.00")
            val gb = 1024.0 * 1024.0 * 1024.0
            "Internal Storage: ${fmt.format(availableBytes / gb)} GB free of ${fmt.format(totalBytes / gb)} GB total (${fmt.format(usedBytes / gb)} GB used)."
        }.getOrElse {
            "Storage telemetry is unavailable on this device."
        }
    }

    override fun controlMedia(action: MediaAction): Result<String> {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Result.failure(IllegalStateException("Audio service unavailable."))

        val keyCode = when (action) {
            MediaAction.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }

        return runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            when (action) {
                MediaAction.PLAY_PAUSE -> "Toggled media playback."
                MediaAction.PLAY -> "Sent media play command."
                MediaAction.PAUSE -> "Sent media pause command."
                MediaAction.NEXT -> "Skipped to next track."
                MediaAction.PREVIOUS -> "Returned to previous track."
            }
        }
    }

    override fun readRecentNotifications(): Result<String> {
        if (!NotificationBufferStore.isListenerEnabled(appContext)) {
            return Result.failure(
                SecurityException(
                    "Notification Access is not enabled for JARVIS. Enable it in Settings -> Permissions Dashboard (or say \"open notification access\")."
                )
            )
        }
        val items = NotificationBufferStore.recent()
        if (items.isEmpty()) {
            return Result.success("Notification Access is active, and no notifications have been captured yet.")
        }
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val summary = items.take(6).joinToString("\n") { item ->
            val time = formatter.format(Date(item.postTimeMillis))
            val body = listOf(item.title, item.text).filter { it.isNotBlank() }.joinToString(": ")
            "• [$time] $body"
        }
        return Result.success("Recent notifications:\n$summary")
    }

    override fun openNotificationListenerSettings(): Result<String> {
        return runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            "Opening Android Notification Access settings."
        }
    }

    override fun findContacts(query: String): Result<List<Pair<String, String>>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide a contact name to search."))
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return Result.failure(
                SecurityException("Contacts permission (READ_CONTACTS) is required to search contacts.")
            )
        }

        return runCatching {
            val results = mutableListOf<Pair<String, String>>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$cleanQuery%")
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && results.size < 8) {
                    val name = if (nameCol >= 0) cursor.getString(nameCol)?.trim().orEmpty() else ""
                    val number = if (numCol >= 0) cursor.getString(numCol)?.trim().orEmpty() else ""
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        val entry = name to number
                        if (entry !in results) results.add(entry)
                    }
                }
            }
            results.toList()
        }
    }

    override fun draftSms(recipientOrName: String, body: String): Result<String> {
        val target = recipientOrName.trim()
        if (target.isEmpty()) {
            return Result.failure(IllegalArgumentException("Specify a phone number or contact name for the SMS draft."))
        }

        val resolvedNumber = if (target.any { it.isDigit() }) {
            target.filter { it.isDigit() || it == '+' }
        } else {
            val lookup = findContacts(target).getOrElse { return Result.failure(it) }
            lookup.firstOrNull()?.second
                ?: return Result.failure(IllegalStateException("No contact found matching \"$target\"."))
        }

        if (resolvedNumber.isBlank()) {
            return Result.failure(IllegalArgumentException("No valid phone number found for \"$target\"."))
        }

        return runCatching {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$resolvedNumber")).apply {
                if (body.isNotBlank()) {
                    putExtra("sms_body", body.trim())
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            "Opened SMS composer for $resolvedNumber (review and tap Send in your Messages app)."
        }
    }

    override fun shareText(text: String): Result<String> {
        val clean = text.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Provide text to share."))
        }
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, clean)
            }
            val chooser = Intent.createChooser(sendIntent, "Share via JARVIS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
            "Opened Android share sheet."
        }
    }

    @Suppress("DEPRECATION")
    private fun queryLauncherActivities(
        pm: PackageManager,
        intent: Intent
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        pm.queryIntentActivities(intent, 0)
    }
}
