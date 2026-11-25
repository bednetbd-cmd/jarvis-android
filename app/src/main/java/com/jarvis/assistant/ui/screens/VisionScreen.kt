package com.jarvis.assistant.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvis.assistant.core.vision.VisionConsentInfo
import com.jarvis.assistant.core.vision.VisionScanMode
import com.jarvis.assistant.core.vision.VisionScanOutput
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VisionScreen(
    viewModel: MainViewModel,
    initialMode: VisionScanMode = VisionScanMode.OCR,
    onNavigateBack: () -> Unit,
    onRunChatCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val analyzer = viewModel.visionAnalyzer

    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var isCameraActive by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var scanOutput by remember { mutableStateOf<VisionScanOutput?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    var pendingConsentInfo by remember { mutableStateOf<VisionConsentInfo?>(null) }
    var aiVisionPrompt by remember {
        mutableStateOf("Describe this image or screenshot clearly and extract any key text or details.")
    }

    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    fun runOfflineOrAskConsent(bitmap: Bitmap, mode: VisionScanMode) {
        if (analyzer == null) {
            scanError = "Vision analyzer is unavailable."
            return
        }
        scanError = null
        if (mode == VisionScanMode.AI_DESCRIBE) {
            scope.launch {
                pendingConsentInfo = analyzer.describeConsentRequirement()
            }
            return
        }
        isAnalyzing = true
        scope.launch {
            analyzer.scanBitmapOffline(bitmap, mode)
                .onSuccess { output ->
                    scanOutput = output
                }
                .onFailure { error ->
                    scanError = error.message ?: "Unable to scan image."
                }
            isAnalyzing = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            viewModel.showNotice("Camera permission denied. You can still pick a photo or screenshot from Gallery.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || analyzer == null) return@rememberLauncherForActivityResult
        scope.launch {
            isAnalyzing = true
            scanError = null
            analyzer.loadScaledBitmapFromUri(uri)
                .onSuccess { bitmap ->
                    capturedBitmap = bitmap
                    isAnalyzing = false
                    runOfflineOrAskConsent(bitmap, selectedMode)
                }
                .onFailure { error ->
                    isAnalyzing = false
                    scanError = error.message ?: "Unable to open selected image."
                }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val showLiveCamera = hasCameraPermission && capturedBitmap == null

    DisposableEffect(showLiveCamera, lifecycleOwner) {
        if (!showLiveCamera) {
            isCameraActive = false
            activeCamera = null
            imageCapture = null
            onDispose {}
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            var boundProvider: ProcessCameraProvider? = null

            val listener = Runnable {
                runCatching {
                    val cameraProvider = providerFuture.get()
                    boundProvider = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    cameraProvider.unbindAll()
                    val cam = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    activeCamera = cam
                    imageCapture = capture
                    isCameraActive = true
                    cameraError = null
                }.onFailure { error ->
                    isCameraActive = false
                    cameraError = error.message ?: "Unable to start device camera."
                }
            }

            providerFuture.addListener(listener, mainExecutor)

            onDispose {
                isCameraActive = false
                torchEnabled = false
                activeCamera = null
                imageCapture = null
                boundProvider?.unbindAll()
            }
        }
    }

    val currentConsent = pendingConsentInfo
    val loadedBitmapForConsent = capturedBitmap
    if (currentConsent != null && loadedBitmapForConsent != null && analyzer != null) {
        AlertDialog(
            onDismissRequest = { pendingConsentInfo = null },
            containerColor = JarvisSurface,
            titleContentColor = JarvisTextPrimary,
            textContentColor = JarvisTextSecondary,
            title = {
                Text(
                    text = if (currentConsent.requiresRemoteUpload) {
                        "Confirm Image Upload"
                    } else {
                        "Analyze Image (${currentConsent.provider.displayName})"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Destination: ${currentConsent.destinationLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = currentConsent.disclosureMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = aiVisionPrompt,
                        onValueChange = { aiVisionPrompt = it },
                        label = { Text("Analysis prompt") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = JarvisSurfaceBorder,
                            focusedTextColor = JarvisTextPrimary,
                            unfocusedTextColor = JarvisTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConsentInfo = null
                        isAnalyzing = true
                        scanError = null
                        scope.launch {
                            analyzer.analyzeWithConsent(
                                bitmap = loadedBitmapForConsent,
                                prompt = aiVisionPrompt,
                                uploadConsentGranted = true
                            ).onSuccess { output ->
                                scanOutput = output
                            }.onFailure { error ->
                                scanError = error.message ?: "Image analysis failed."
                            }
                            isAnalyzing = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = JarvisBackground
                    )
                ) {
                    Text(if (currentConsent.requiresRemoteUpload) "Allow & Upload" else "Analyze")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConsentInfo = null }) {
                    Text("Cancel", color = JarvisTextMuted)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "JARVIS VISION & SCANNER",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCameraActive) JarvisErrorRed.copy(alpha = 0.14f) else JarvisSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isCameraActive) JarvisErrorRed.copy(alpha = 0.6f) else JarvisSurfaceBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isCameraActive) JarvisErrorRed else JarvisSuccessGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isCameraActive -> "CAMERA ACTIVE · LIVE PREVIEW"
                                    capturedBitmap != null -> "IMAGE LOADED · CAMERA PAUSED"
                                    else -> "CAMERA STANDBY · PHOTO PICKER READY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCameraActive) JarvisErrorRed else JarvisTextSecondary
                            )
                        }
                        if (isCameraActive && activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                            IconButton(
                                onClick = {
                                    val next = !torchEnabled
                                    torchEnabled = next
                                    activeCamera?.cameraControl?.enableTorch(next)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                    contentDescription = "Toggle scanner torch",
                                    tint = if (torchEnabled) accent else JarvisTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VisionScanMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = {
                                selectedMode = mode
                                val bmp = capturedBitmap
                                if (bmp != null) {
                                    runOfflineOrAskConsent(bmp, mode)
                                }
                            },
                            label = { Text(mode.label) },
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

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(270.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    when {
                        capturedBitmap != null -> {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Captured or selected image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        hasCameraPermission && cameraError == null -> {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = cameraError ?: "Camera permission is not granted. Grant Camera access or pick an image/screenshot from Gallery.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisTextSecondary
                                )
                                if (!hasCameraPermission) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                        border = BorderStroke(1.dp, accent),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                                    ) {
                                        Text("Grant Camera Permission")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (capturedBitmap == null) {
                        Button(
                            onClick = {
                                val capture = imageCapture
                                if (capture == null || !hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    return@Button
                                }
                                isAnalyzing = true
                                scanError = null
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            scope.launch {
                                                val bitmap = withContext(Dispatchers.Default) {
                                                    try {
                                                        imageProxyToRotatedBitmap(image, maxDimension = 1600)
                                                    } finally {
                                                        image.close()
                                                    }
                                                }
                                                isAnalyzing = false
                                                if (bitmap != null) {
                                                    capturedBitmap = bitmap
                                                    runOfflineOrAskConsent(bitmap, selectedMode)
                                                } else {
                                                    scanError = "Failed to decode camera frame."
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isAnalyzing = false
                                            scanError = exception.message ?: "Camera capture failed."
                                        }
                                    }
                                )
                            },
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capture & Scan")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                capturedBitmap = null
                                scanOutput = null
                                scanError = null
                            },
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake / Camera")
                        }
                        Button(
                            onClick = {
                                capturedBitmap?.let { runOfflineOrAskConsent(it, selectedMode) }
                            },
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = JarvisBackground
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-Scan (${selectedMode.label.substringBefore(' ')})")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        border = BorderStroke(1.dp, JarvisSurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisTextPrimary)
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Screenshot / Gallery")
                    }
                }
            }

            if (isAnalyzing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Analyzing image...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                    }
                }
            }

            val err = scanError
            if (!err.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = JarvisWarningAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, JarvisWarningAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisWarningAmber,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            val result = scanOutput
            if (result != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = JarvisSurface,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "VISION OUTPUT · ${result.mode.label.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            Text(
                                text = result.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextPrimary
                            )

                            result.detectedUrls.forEach { url ->
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                        }.onFailure {
                                            viewModel.showNotice("No browser available to open $url.")
                                        }
                                    },
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open $url", maxLines = 1)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.sendVisionOutputToChat(result)
                                        onNavigateBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = JarvisBackground
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Send to JARVIS Chat")
                                }
                                if (result.mode == VisionScanMode.OCR && result.rawText.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            onRunChatCommand("Summarize: ${result.rawText}")
                                            onNavigateBack()
                                        },
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Summarize Text")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToRotatedBitmap(
    imageProxy: ImageProxy,
    maxDimension: Int = 1600
): Bitmap? {
    val raw = runCatching { imageProxy.toBitmap() }.getOrNull() ?: return null
    val rotation = imageProxy.imageInfo.rotationDegrees
    val largestSide = max(raw.width, raw.height)
    val scale = if (largestSide > maxDimension) maxDimension.toFloat() / largestSide.toFloat() else 1f
    if (rotation == 0 && scale >= 1f) return raw
    val matrix = Matrix().apply {
        if (rotation != 0) postRotate(rotation.toFloat())
        if (scale < 1f) postScale(scale, scale)
    }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
        if (it !== raw) raw.recycle()
    }
}
