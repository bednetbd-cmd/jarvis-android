package com.jarvis.assistant.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.tools.AngleUnit
import com.jarvis.assistant.core.tools.ClipboardSnapshot
import com.jarvis.assistant.core.tools.PdfPagePreview
import com.jarvis.assistant.core.tools.SafDirectoryResult
import com.jarvis.assistant.core.tools.ScientificCalculator
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisErrorRed
import com.jarvis.assistant.ui.theme.JarvisSuccessGreen
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisSurfaceVariant
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary
import com.jarvis.assistant.ui.theme.JarvisWarningAmber
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkbenchScreen(
    viewModel: MainViewModel,
    initialTab: WorkbenchTab = WorkbenchTab.CALCULATOR,
    onNavigateBack: () -> Unit,
    onRunChatCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "AI WORKBENCH",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = accent
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = JarvisBackground,
                        titleContentColor = JarvisTextPrimary
                    )
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = JarvisSurfaceBorder.copy(alpha = 0.65f)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkbenchTab.entries.forEach { tab ->
                    FilterChip(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        label = { Text(tab.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent,
                            containerColor = JarvisSurfaceVariant,
                            labelColor = JarvisTextSecondary
                        )
                    )
                }
            }

            when (activeTab) {
                WorkbenchTab.CALCULATOR -> ScientificCalculatorPane(
                    onSendToChat = onRunChatCommand
                )
                WorkbenchTab.FILES -> SafFileExplorerPane(
                    viewModel = viewModel,
                    onRunChatCommand = onRunChatCommand
                )
                WorkbenchTab.PDF -> PdfReaderPane(
                    viewModel = viewModel,
                    onRunChatCommand = onRunChatCommand
                )
                WorkbenchTab.CLIPBOARD_BATCH -> ClipboardAndBatchPane(
                    viewModel = viewModel,
                    onRunChatCommand = onRunChatCommand
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScientificCalculatorPane(
    onSendToChat: (String) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    var expression by remember { mutableStateOf("sin(30) + sqrt(144)") }
    var angleUnit by remember { mutableStateOf(AngleUnit.DEG) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }

    fun evaluateNow() {
        ScientificCalculator.evaluate(expression, angleUnit)
            .onSuccess { value ->
                val formatted = ScientificCalculator.format(value)
                resultText = formatted
                errorText = null
                history = (listOf("$expression = $formatted") + history).distinct().take(10)
            }
            .onFailure { err ->
                resultText = null
                errorText = err.message ?: "Invalid expression"
            }
    }

    LaunchedEffect(Unit) {
        evaluateNow()
    }

    val keypadTokens = listOf(
        "sin(", "cos(", "tan(", "sqrt(",
        "ln(", "log(", "^", "!",
        "(", ")", "pi", "e",
        "7", "8", "9", "/",
        "4", "5", "6", "*",
        "1", "2", "3", "-",
        "0", ".", "%", "+"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurface,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCIENTIFIC CALCULATOR",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AngleUnit.entries.forEach { unit ->
                                FilterChip(
                                    selected = angleUnit == unit,
                                    onClick = {
                                        angleUnit = unit
                                        evaluateNow()
                                    },
                                    label = { Text(unit.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent,
                                        containerColor = JarvisSurfaceVariant,
                                        labelColor = JarvisTextSecondary
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = expression,
                        onValueChange = { expression = it },
                        label = { Text("Expression (trig, log, ln, sqrt, ^, !, pi, e)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = JarvisSurfaceBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary,
                            cursorColor = accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resultText != null) {
                        Text(
                            text = "= $resultText",
                            style = MaterialTheme.typography.headlineSmall,
                            color = JarvisSuccessGreen
                        )
                    } else if (errorText != null) {
                        Text(
                            text = errorText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = JarvisErrorRed
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = ::evaluateNow,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Evaluate")
                        }
                        OutlinedButton(
                            onClick = {
                                expression = ""
                                resultText = null
                                errorText = null
                            },
                            border = BorderStroke(1.dp, JarvisSurfaceBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextSecondary)
                        ) {
                            Text("Clear")
                        }
                        OutlinedButton(
                            onClick = {
                                if (expression.isNotBlank()) {
                                    onSendToChat("Calculate $expression")
                                }
                            },
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                        ) {
                            Text("Chat")
                        }
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                keypadTokens.forEach { token ->
                    OutlinedButton(
                        onClick = { expression += token },
                        border = BorderStroke(1.dp, JarvisSurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = JarvisSurface,
                            contentColor = JarvisTextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = token)
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT CALCULATIONS",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
            items(history) { item ->
                Surface(
                    onClick = {
                        expression = item.substringBefore(" = ").trim()
                        evaluateNow()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SafFileExplorerPane(
    viewModel: MainViewModel,
    onRunChatCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docs = viewModel.workspaceDocs
    val accent = MaterialTheme.colorScheme.primary

    var treeUri by remember { mutableStateOf<String?>(null) }
    var directoryResult by remember { mutableStateOf<SafDirectoryResult?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewContent by remember { mutableStateOf<String?>(null) }
    var newNoteName by remember { mutableStateOf("jarvis_export.txt") }
    var newNoteBody by remember { mutableStateOf("") }

    fun loadDirectory(uri: String, query: String) {
        if (docs == null) return
        scope.launch {
            isLoading = true
            errorMessage = null
            docs.listFolderFiles(uri, query)
                .onSuccess { directoryResult = it }
                .onFailure { errorMessage = it.message ?: "Unable to list folder." }
            isLoading = false
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null && docs != null) {
            val uriStr = uri.toString()
            docs.persistTreePermission(uriStr)
            treeUri = uriStr
            loadDirectory(uriStr, searchQuery)
        }
    }

    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && docs != null) {
            scope.launch {
                isLoading = true
                errorMessage = null
                docs.readTextFile(uri.toString())
                    .onSuccess { (name, text) ->
                        previewTitle = name
                        previewContent = text
                    }
                    .onFailure { errorMessage = it.message ?: "Unable to read file." }
                isLoading = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurface,
                border = BorderStroke(1.dp, JarvisSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "SCOPED STORAGE FILE MANAGER (SAF)",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = "Uses Android Storage Access Framework so JARVIS only touches folders or documents you explicitly select.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Folder")
                        }
                        OutlinedButton(
                            onClick = { singleFileLauncher.launch(arrayOf("text/*", "application/json", "*/*")) },
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open File")
                        }
                    }

                    if (treeUri != null) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { next ->
                                searchQuery = next
                                treeUri?.let { loadDirectory(it, next) }
                            },
                            label = { Text("Search files in folder...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accent) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = JarvisSurfaceBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary,
                                cursorColor = accent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = accent)
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisErrorRed
                )
            }
        }

        if (previewContent != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "FILE PREVIEW: ${previewTitle ?: "Document"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        Text(
                            text = previewContent.orEmpty().take(2500),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = JarvisTextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onRunChatCommand("Summarize: ${previewContent.orEmpty().take(3500)}")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = JarvisBackground
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Summarize in Chat")
                            }
                            OutlinedButton(
                                onClick = { previewContent = null },
                                border = BorderStroke(1.dp, JarvisSurfaceBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextMuted)
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }

        val currentDir = directoryResult
        if (currentDir != null && treeUri != null && docs != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CREATE FILE IN \"${currentDir.folderName}\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        OutlinedTextField(
                            value = newNoteName,
                            onValueChange = { newNoteName = it },
                            label = { Text("Filename") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = JarvisSurfaceBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newNoteBody,
                            onValueChange = { newNoteBody = it },
                            label = { Text("Text content") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                unfocusedBorderColor = JarvisSurfaceBorder,
                                focusedTextColor = JarvisTextPrimary,
                                unfocusedTextColor = JarvisTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    docs.createTextNoteFile(treeUri!!, newNoteName, newNoteBody)
                                        .onSuccess { msg ->
                                            viewModel.showNotice(msg)
                                            newNoteBody = ""
                                            loadDirectory(treeUri!!, searchQuery)
                                        }
                                        .onFailure { err -> errorMessage = err.message }
                                }
                            },
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Write File to Selected Folder")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "${currentDir.folderName} (${currentDir.entries.size} item(s))",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }

            items(currentDir.entries, key = { it.uriString }) { entry ->
                val dateLabel = remember(entry.lastModifiedMillis) {
                    if (entry.lastModifiedMillis > 0L) {
                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                            .format(Date(entry.lastModifiedMillis))
                    } else ""
                }
                val sizeKb = if (entry.isDirectory) "Folder" else "${maxOf(1L, entry.sizeBytes / 1024L)} KB"
                Surface(
                    onClick = {
                        if (!entry.isDirectory) {
                            scope.launch {
                                docs.readTextFile(entry.uriString)
                                    .onSuccess { (name, text) ->
                                        previewTitle = name
                                        previewContent = text
                                    }
                                    .onFailure {
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(android.net.Uri.parse(entry.uriString), entry.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(openIntent) }
                                            .onFailure { err -> errorMessage = err.message }
                                    }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = JarvisTextPrimary
                            )
                            Text(
                                text = "$sizeKb · ${entry.mimeType} · $dateLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextMuted
                            )
                        }
                        if (!entry.isDirectory) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        docs.deleteSafFile(entry.uriString)
                                            .onSuccess { msg ->
                                                viewModel.showNotice(msg)
                                                loadDirectory(treeUri!!, searchQuery)
                                            }
                                            .onFailure { err -> errorMessage = err.message }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete file",
                                    tint = JarvisTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfReaderPane(
    viewModel: MainViewModel,
    onRunChatCommand: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val docs = viewModel.workspaceDocs
    val accent = MaterialTheme.colorScheme.primary

    var pdfUri by remember { mutableStateOf<String?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<PdfPagePreview?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadPage(uri: String, index: Int) {
        if (docs == null) return
        scope.launch {
            isLoading = true
            errorMessage = null
            docs.renderPdfPage(uri, index, runOcr = true)
                .onSuccess { page ->
                    preview = page
                    pageIndex = page.pageIndex
                }
                .onFailure { err -> errorMessage = err.message ?: "Unable to render PDF." }
            isLoading = false
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pdfUri = uri.toString()
            pageIndex = 0
            loadPage(uri.toString(), 0)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurface,
                border = BorderStroke(1.dp, JarvisSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "NATIVE PDF READER & SUMMARIZER",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = "Renders PDF pages locally using Android PdfRenderer and extracts page text offline with ML Kit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextMuted
                    )
                    Button(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = JarvisBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (pdfUri == null) "Select PDF Document" else "Choose Another PDF")
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = accent)
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            item {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisErrorRed
                )
            }
        }

        val currentPage = preview
        if (currentPage != null && pdfUri != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentPage.fileName} (${currentPage.pageIndex + 1}/${currentPage.pageCount})",
                                style = MaterialTheme.typography.titleSmall,
                                color = accent,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(
                                    onClick = { loadPage(pdfUri!!, pageIndex - 1) },
                                    enabled = pageIndex > 0 && !isLoading
                                ) {
                                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous page", tint = accent)
                                }
                                IconButton(
                                    onClick = { loadPage(pdfUri!!, pageIndex + 1) },
                                    enabled = pageIndex + 1 < currentPage.pageCount && !isLoading
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next page", tint = accent)
                                }
                            }
                        }

                        val aspect = currentPage.bitmap.width.toFloat() / currentPage.bitmap.height.coerceAtLeast(1)
                        Image(
                            bitmap = currentPage.bitmap.asImageBitmap(),
                            contentDescription = "PDF page preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspect.coerceIn(0.5f, 1.5f))
                        )

                        if (currentPage.extractedText.isNotBlank()) {
                            Text(
                                text = "EXTRACTED TEXT (THIS PAGE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                            Text(
                                text = currentPage.extractedText.take(1500),
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisTextSecondary
                            )
                        }

                        Button(
                            onClick = {
                                if (docs != null) {
                                    scope.launch {
                                        isLoading = true
                                        docs.extractPdfDigest(pdfUri!!, maxPages = 4)
                                            .onSuccess { (_, digest) ->
                                                onRunChatCommand("Summarize: ${digest.take(3800)}")
                                            }
                                            .onFailure { err ->
                                                errorMessage = err.message ?: "Unable to extract PDF text."
                                            }
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Summarize PDF Document in Chat")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClipboardAndBatchPane(
    viewModel: MainViewModel,
    onRunChatCommand: (String) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val clipboardPort = viewModel.clipboardPort
    var clipText by remember { mutableStateOf("") }
    var clipStatus by remember { mutableStateOf("Tap Read Clipboard to inspect current clipboard text.") }
    var batchScript by remember {
        mutableStateOf("What time is it?; Battery status; Daily briefing")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurface,
                border = BorderStroke(1.dp, JarvisSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "CLIPBOARD & CODE ASSISTANT",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = clipStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val snap: ClipboardSnapshot = clipboardPort?.readText()
                                    ?: ClipboardSnapshot("", false, "Clipboard unavailable.")
                                clipText = snap.text
                                clipStatus = snap.note
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read Clipboard")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboardPort?.writeText("JARVIS Text", clipText)
                                    ?.onSuccess { viewModel.showNotice(it) }
                                    ?.onFailure { viewModel.showNotice(it.message ?: "Unable to copy.") }
                            },
                            enabled = clipText.isNotBlank(),
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }

                    OutlinedTextField(
                        value = clipText,
                        onValueChange = { clipText = it },
                        label = { Text("Text / Code workspace") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = JarvisSurfaceBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Summarize" to { "Summarize: ${clipText.take(3600)}" },
                            "Bullet Points" to { "Bullets: ${clipText.take(3600)}" },
                            "Text Stats" to { "Word count: ${clipText.take(3600)}" },
                            "Code Review (AI)" to { "Explain and review this code concisely:\n${clipText.take(3000)}" },
                            "Translate to Bangla" to { "Translate ${clipText.take(300)} to Bangla" }
                        ).forEach { (label, commandBuilder) ->
                            OutlinedButton(
                                onClick = {
                                    if (clipText.isBlank()) {
                                        viewModel.showNotice("Read clipboard or paste text first.")
                                    } else {
                                        onRunChatCommand(commandBuilder())
                                    }
                                },
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = JarvisSurface,
                border = BorderStroke(1.dp, JarvisSurfaceBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MULTI-STEP BATCH EXECUTOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = "Chain up to 6 JARVIS commands separated by semicolons (;). Tap STOP in the top telemetry bar to abort anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextMuted
                    )
                    OutlinedTextField(
                        value = batchScript,
                        onValueChange = { batchScript = it },
                        label = { Text("Commands (; or newline separated)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = JarvisSurfaceBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (batchScript.isNotBlank()) {
                                onRunChatCommand("Batch: $batchScript")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = JarvisBackground
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute Multi-Step Batch")
                    }
                }
            }
        }
    }
}
