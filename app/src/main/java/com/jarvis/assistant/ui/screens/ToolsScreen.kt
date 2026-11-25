package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.core.vision.VisionScanMode
import com.jarvis.assistant.ui.theme.JarvisBackground
import com.jarvis.assistant.ui.theme.JarvisSuccessGreen
import com.jarvis.assistant.ui.theme.JarvisSurface
import com.jarvis.assistant.ui.theme.JarvisSurfaceBorder
import com.jarvis.assistant.ui.theme.JarvisTextMuted
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

private data class QuickToolAction(
    val title: String,
    val subtitle: String,
    val command: String = "",
    val icon: ImageVector,
    val visionMode: VisionScanMode? = null,
    val workbenchTab: WorkbenchTab? = null
)

private data class PlannedSuiteItem(
    val title: String,
    val description: String,
    val milestoneLabel: String
)

private val quickTools = listOf(
    QuickToolAction("Scientific Calculator", "Trig, logs, roots, powers, factorials (DEG/RAD)", icon = Icons.Rounded.Functions, workbenchTab = WorkbenchTab.CALCULATOR),
    QuickToolAction("File Explorer & Search (SAF)", "Scoped storage file browser, text reader & notes", icon = Icons.Rounded.FolderOpen, workbenchTab = WorkbenchTab.FILES),
    QuickToolAction("PDF Reader & Summarizer", "Render PDF pages with PdfRenderer + offline OCR", icon = Icons.Rounded.PictureAsPdf, workbenchTab = WorkbenchTab.PDF),
    QuickToolAction("Clipboard, Code & Batch", "Inspect clipboard, format text, run multi-step commands", icon = Icons.Rounded.ContentPaste, workbenchTab = WorkbenchTab.CLIPBOARD_BATCH),
    QuickToolAction("OCR & Document Scanner", "Extract text offline with CameraX + ML Kit", icon = Icons.Rounded.DocumentScanner, visionMode = VisionScanMode.OCR),
    QuickToolAction("QR & Barcode Scanner", "Scan QR codes and links offline", icon = Icons.Rounded.QrCodeScanner, visionMode = VisionScanMode.QR_BARCODE),
    QuickToolAction("Object Identifier", "Identify objects & scenes offline on-device", icon = Icons.Rounded.CenterFocusWeak, visionMode = VisionScanMode.OBJECT_LABEL),
    QuickToolAction("Screenshot & AI Vision", "Describe images or screenshots (requires consent)", icon = Icons.Rounded.ImageSearch, visionMode = VisionScanMode.AI_DESCRIBE),
    QuickToolAction("Daily Briefing", "Summarize date, calendar, tasks, habits & battery", "Daily briefing", Icons.Rounded.Today),
    QuickToolAction("My Tasks & Planner", "View pending to-do and planner items", "My tasks", Icons.Rounded.Checklist),
    QuickToolAction("Shopping List", "View Room-persisted shopping items", "Shopping list", Icons.Rounded.ShoppingCart),
    QuickToolAction("Today's Calendar", "Read upcoming Android Calendar events", "My calendar", Icons.Rounded.CalendarMonth),
    QuickToolAction("My Saved Notes", "Read personal notes from Room database", "My notes", Icons.Rounded.EditNote),
    QuickToolAction("Habit Tracker", "Check habit streaks and daily status", "My habits", Icons.Rounded.TrackChanges),
    QuickToolAction("Battery & Diagnostics", "Read live charge level & device specs", "Device info", Icons.Rounded.BatteryStd),
    QuickToolAction("Storage Space", "Inspect free & total internal storage", "Storage info", Icons.Rounded.Storage),
    QuickToolAction("Toggle Flashlight", "Switch device camera torch on/off", "Toggle flashlight", Icons.Rounded.FlashlightOn),
    QuickToolAction("Media Play / Pause", "Control active music or video playback", "Play pause", Icons.Rounded.PlayArrow),
    QuickToolAction("Set Media Volume 50%", "Adjust media stream volume safely", "Set volume 50%", Icons.Rounded.VolumeUp),
    QuickToolAction("Read Notifications", "Summarize recent notifications (requires access)", "Read notifications", Icons.Rounded.Notifications),
    QuickToolAction("List Installed Apps", "View installed launchable apps", "List apps", Icons.Rounded.Apps),
    QuickToolAction("Quick 5-Min Timer", "Launch Android Clock timer intent", "Set timer 5 minutes", Icons.Rounded.Timer),
    QuickToolAction("7:30 AM Alarm", "Prepare alarm intent in Clock app", "Set alarm 7:30 am", Icons.Rounded.Alarm),
    QuickToolAction("Open Wi-Fi Settings", "Jump to Android Wi-Fi settings", "Open Wi-Fi", Icons.Rounded.Wifi),
    QuickToolAction("Open Bluetooth", "Jump to Android Bluetooth settings", "Open Bluetooth", Icons.Rounded.Bluetooth),
    QuickToolAction("Launch Camera", "Open device camera app", "Open camera", Icons.Rounded.CameraAlt),
    QuickToolAction("Weather in Dhaka", "Live forecast via free Open-Meteo API", "Weather in Dhaka", Icons.Rounded.WbSunny),
    QuickToolAction("Latest World News", "Top headlines via BBC World RSS", "Latest news", Icons.Rounded.Newspaper),
    QuickToolAction("Tech News", "Latest technology & AI headlines", "Tech news", Icons.Rounded.Public),
    QuickToolAction("Convert 100 USD to BDT", "Live exchange rate conversion", "Convert 100 USD to BDT", Icons.Rounded.CurrencyExchange),
    QuickToolAction("Convert 10 km to miles", "Deterministic offline unit converter", "Convert 10 km to miles", Icons.Rounded.Straighten),
    QuickToolAction("Wikipedia: AI", "Encyclopedia summary via Wikipedia REST", "Wikipedia Artificial Intelligence", Icons.Rounded.Language),
    QuickToolAction("Sample Calculation", "Evaluate sin(30) + sqrt(144) locally", "Calculate sin(30) + sqrt(144)", Icons.Rounded.Calculate),
    QuickToolAction("Offline Task Plan", "Generate a local checklist for a goal", "Plan: organize today's priority tasks", Icons.Rounded.Settings),
    QuickToolAction("Device Settings", "Open system Android settings", "Open settings", Icons.Rounded.Settings)
)

private val systemReadinessSummary = PlannedSuiteItem(
    title = "JARVIS v1.0 Core Architecture",
    description = "HUD, multi-provider AI, English/Bangla voice & wake-word monitor, device controls, free web intelligence, CameraX + ML Kit vision, Room productivity hub, scientific workbench, and biometric Keystore vault are active.",
    milestoneLabel = "ALL SYSTEMS ONLINE"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onRunCommand: (String) -> Unit,
    onOpenVision: (VisionScanMode) -> Unit = {},
    onOpenWorkbench: (WorkbenchTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JarvisBackground,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "TOOLS & SYSTEMS",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisTextPrimary
                        )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "ACTIVE QUICK COMMANDS",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap any tool below to execute it directly in JARVIS chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextSecondary
                )
            }

            items(quickTools) { tool ->
                Surface(
                    onClick = {
                        when {
                            tool.workbenchTab != null -> onOpenWorkbench(tool.workbenchTab)
                            tool.visionMode != null -> onOpenVision(tool.visionMode)
                            else -> onRunCommand(tool.command)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                            Text(
                                text = tool.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisTextMuted
                            )
                        }
                        Text(
                            text = if (tool.visionMode != null || tool.workbenchTab != null) "OPEN" else "RUN",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SYSTEM READINESS",
                    style = MaterialTheme.typography.labelMedium,
                    color = JarvisSuccessGreen
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = JarvisSurface,
                    border = BorderStroke(1.dp, JarvisSuccessGreen.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = systemReadinessSummary.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = JarvisTextPrimary
                            )
                            Text(
                                text = systemReadinessSummary.milestoneLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisSuccessGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = systemReadinessSummary.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted
                        )
                    }
                }
            }
        }
    }
}
