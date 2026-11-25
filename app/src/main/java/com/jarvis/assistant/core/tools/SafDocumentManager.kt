package com.jarvis.assistant.core.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SafFileEntry(
    val uriString: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val isDirectory: Boolean
)

data class SafDirectoryResult(
    val treeUri: String,
    val folderName: String,
    val entries: List<SafFileEntry>
)

data class PdfPagePreview(
    val fileName: String,
    val pageIndex: Int,
    val pageCount: Int,
    val bitmap: Bitmap,
    val extractedText: String
)

interface WorkspaceDocumentsPort {
    suspend fun listFolderFiles(treeUriString: String, query: String = ""): Result<SafDirectoryResult>
    suspend fun readTextFile(uriString: String, maxChars: Int = 20_000): Result<Pair<String, String>>
    suspend fun createTextNoteFile(treeUriString: String, fileName: String, content: String): Result<String>
    suspend fun deleteSafFile(uriString: String): Result<String>
    suspend fun renderPdfPage(uriString: String, pageIndex: Int, runOcr: Boolean = true): Result<PdfPagePreview>
    suspend fun extractPdfDigest(uriString: String, maxPages: Int = 4): Result<Pair<String, String>>
    fun persistTreePermission(treeUriString: String)
}

class AndroidWorkspaceDocumentsManager(
    context: Context
) : WorkspaceDocumentsPort {

    private val appContext = context.applicationContext
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override fun persistTreePermission(treeUriString: String) {
        val uri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    override suspend fun listFolderFiles(
        treeUriString: String,
        query: String
    ): Result<SafDirectoryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = Uri.parse(treeUriString)
            val root = DocumentFile.fromTreeUri(appContext, treeUri)
                ?: throw IllegalStateException("Unable to access selected folder via Storage Access Framework.")
            val cleanQuery = query.trim()
            val entries = root.listFiles()
                .map { doc ->
                    SafFileEntry(
                        uriString = doc.uri.toString(),
                        name = doc.name.orEmpty().ifBlank { "Unnamed" },
                        mimeType = doc.type.orEmpty().ifBlank {
                            if (doc.isDirectory) "inode/directory" else "application/octet-stream"
                        },
                        sizeBytes = doc.length(),
                        lastModifiedMillis = doc.lastModified(),
                        isDirectory = doc.isDirectory
                    )
                }
                .filter { entry ->
                    cleanQuery.isEmpty() || entry.name.contains(cleanQuery, ignoreCase = true)
                }
                .sortedWith(compareBy<SafFileEntry>({ !it.isDirectory }, { it.name.lowercase() }))

            SafDirectoryResult(
                treeUri = treeUriString,
                folderName = root.name.orEmpty().ifBlank { "Selected Folder" },
                entries = entries
            )
        }
    }

    override suspend fun readTextFile(
        uriString: String,
        maxChars: Int
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val doc = DocumentFile.fromSingleUri(appContext, uri)
            val name = doc?.name.orEmpty().ifBlank { "document.txt" }
            val mime = appContext.contentResolver.getType(uri).orEmpty()
            val isTextSupported = mime.startsWith("text/") ||
                mime in TEXT_MIME_TYPES ||
                TEXT_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) }

            if (!isTextSupported) {
                throw IllegalArgumentException(
                    "\"$name\" ($mime) is not a plain-text or code file. Use the PDF Reader for PDFs or Vision Scanner for images."
                )
            }
            if ((doc?.length() ?: 0L) > MAX_TEXT_FILE_BYTES) {
                throw IllegalArgumentException("\"$name\" exceeds the 512 KB safe preview cap.")
            }

            val content = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val buffer = CharArray(maxChars.coerceIn(512, 50_000))
                    val readCount = reader.read(buffer)
                    if (readCount <= 0) "" else String(buffer, 0, readCount)
                }
            } ?: throw IllegalStateException("Unable to open \"$name\" for reading.")

            name to content
        }
    }

    override suspend fun createTextNoteFile(
        treeUriString: String,
        fileName: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = fileName.trim()
                .replace(Regex("[/\\\\:*?\"<>|]"), "_")
                .ifBlank { "jarvis_note.txt" }
            val finalName = if (safeName.contains('.')) safeName else "$safeName.txt"
            val root = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUriString))
                ?: throw IllegalStateException("Select a folder first via Storage Access Framework.")
            val created = root.createFile("text/plain", finalName)
                ?: throw IllegalStateException("Write access was not granted for this folder.")

            appContext.contentResolver.openOutputStream(created.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IllegalStateException("Unable to write to \"$finalName\".")

            "Saved \"$finalName\" inside \"${root.name ?: "folder"}\"."
        }
    }

    override suspend fun deleteSafFile(uriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = DocumentFile.fromSingleUri(appContext, Uri.parse(uriString))
                ?: throw IllegalStateException("File reference is unavailable.")
            val name = doc.name.orEmpty().ifBlank { "file" }
            if (!doc.delete()) {
                throw IllegalStateException("System denied deletion of \"$name\".")
            }
            "Deleted \"$name\"."
        }
    }

    override suspend fun renderPdfPage(
        uriString: String,
        pageIndex: Int,
        runOcr: Boolean
    ): Result<PdfPagePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val docName = DocumentFile.fromSingleUri(appContext, uri)?.name.orEmpty().ifBlank { "document.pdf" }
            val pfd: ParcelFileDescriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Unable to open PDF descriptor.")

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = renderer.pageCount
                    if (pageCount <= 0) {
                        throw IllegalStateException("This PDF contains zero pages.")
                    }
                    val safeIndex = pageIndex.coerceIn(0, pageCount - 1)
                    val renderedBitmap = renderer.openPage(safeIndex).use { page ->
                        val maxWidth = 1080
                        val scale = (maxWidth.toFloat() / page.width.coerceAtLeast(1)).coerceIn(0.8f, 2.2f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }

                    val extractedText = if (runOcr) {
                        recognizeTextFromBitmap(renderedBitmap).orEmpty()
                    } else {
                        ""
                    }

                    PdfPagePreview(
                        fileName = docName,
                        pageIndex = safeIndex,
                        pageCount = pageCount,
                        bitmap = renderedBitmap,
                        extractedText = extractedText
                    )
                }
            }
        }
    }

    override suspend fun extractPdfDigest(
        uriString: String,
        maxPages: Int
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val docName = DocumentFile.fromSingleUri(appContext, uri)?.name.orEmpty().ifBlank { "document.pdf" }
            val pfd = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Unable to open PDF descriptor.")

            val builder = StringBuilder()
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val limit = minOf(renderer.pageCount, maxPages.coerceIn(1, 6))
                    for (idx in 0 until limit) {
                        val bmp = renderer.openPage(idx).use { page ->
                            val scale = (900f / page.width.coerceAtLeast(1)).coerceIn(0.8f, 1.8f)
                            val w = (page.width * scale).toInt().coerceAtLeast(1)
                            val h = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                        val pageText = recognizeTextFromBitmap(bmp).orEmpty().trim()
                        bmp.recycle()
                        if (pageText.isNotEmpty()) {
                            builder.append(pageText).append('\n')
                        }
                    }
                }
            }

            val combined = builder.toString().trim()
            if (combined.isEmpty()) {
                throw IllegalStateException("No readable text was detected in the first pages of \"$docName\".")
            }
            docName to combined
        }
    }

    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val input = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                textRecognizer.process(input)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result.text)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }
        }.getOrNull()
    }

    companion object {
        private const val MAX_TEXT_FILE_BYTES = 512 * 1024L
        private val TEXT_MIME_TYPES = setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-sh"
        )
        private val TEXT_EXTENSIONS = setOf(
            "txt", "md", "json", "csv", "log", "xml", "yaml", "yml",
            "kt", "kts", "java", "py", "js", "ts", "html", "css", "ini", "toml"
        )
    }
}
