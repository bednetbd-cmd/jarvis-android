package com.jarvis.assistant.core.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

data class ClipboardSnapshot(
    val text: String,
    val isAvailable: Boolean,
    val note: String
)

interface ClipboardPort {
    fun readText(): ClipboardSnapshot
    fun writeText(label: String, text: String): Result<String>
}

class AndroidClipboardAdapter(
    context: Context
) : ClipboardPort {

    private val clipboardManager =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    override fun readText(): ClipboardSnapshot {
        val manager = clipboardManager
            ?: return ClipboardSnapshot("", false, "Android Clipboard service is unavailable.")
        if (!manager.hasPrimaryClip()) {
            return ClipboardSnapshot("", false, "Clipboard is currently empty.")
        }
        val clip = manager.primaryClip ?: return ClipboardSnapshot("", false, "Clipboard unavailable.")
        if (clip.itemCount == 0) {
            return ClipboardSnapshot("", false, "Clipboard has no text items.")
        }
        val item = clip.getItemAt(0)
        val rawText = item.text?.toString().orEmpty().trim()
        return if (rawText.isNotEmpty()) {
            ClipboardSnapshot(
                text = rawText,
                isAvailable = true,
                note = "Captured ${rawText.length} characters from Android clipboard."
            )
        } else {
            val uriHint = item.uri?.toString().orEmpty()
            ClipboardSnapshot(
                text = uriHint,
                isAvailable = uriHint.isNotEmpty(),
                note = if (uriHint.isNotEmpty()) "Clipboard contains a URI reference." else "Clipboard item is non-text."
            )
        }
    }

    override fun writeText(label: String, text: String): Result<String> {
        val manager = clipboardManager
            ?: return Result.failure(IllegalStateException("Android Clipboard service is unavailable."))
        val clean = text.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nothing to copy to clipboard."))
        }
        manager.setPrimaryClip(ClipData.newPlainText(label, clean))
        return Result.success("Copied ${clean.length} characters to clipboard.")
    }
}
