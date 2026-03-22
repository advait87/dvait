package com.dvait.base.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dvait.base.data.repository.CapturedTextRepository
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.engine.EmbeddingEngine
import com.dvait.base.ui.theme.*
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsDataStore: SettingsDataStore,
    repository: CapturedTextRepository,
    embeddingEngine: EmbeddingEngine,
    onBack: () -> Unit,
    onViewData: () -> Unit = {},
    onViewAppFilter: () -> Unit = {},
    onNavigateToThemeSettings: () -> Unit = {},
    onNavigateToBackendSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val captureNotifications by settingsDataStore.captureNotifications.collectAsState(initial = false)
    var showClearDialog by remember { mutableStateOf(false) }
    var showDedupDialog by remember { mutableStateOf(false) }
    var dedupProgress by remember { mutableStateOf(0) }
    var dedupTotal by remember { mutableStateOf(0) }
    var dedupRemoved by remember { mutableStateOf(0) }
    var isDedupRunning by remember { mutableStateOf(false) }
    val dataCount by repository.countFlow().collectAsState(initial = 0L)
    val appTheme by settingsDataStore.appTheme.collectAsState(initial = "system")
    val accentColor by settingsDataStore.accentColor.collectAsState(initial = "orange")
    val groqApiKey by settingsDataStore.groqApiKey.collectAsState(initial = "")
    val groqModel by settingsDataStore.groqModel.collectAsState(initial = com.dvait.base.util.GroqUtils.DEFAULT_MODEL)



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {



            // ─── Appearance ───
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Appearance") }
            item {
              SettingsCard {
                NavRow(
                  icon = if (appTheme == "system") Icons.Default.AutoMode else if (appTheme == "dark") Icons.Default.DarkMode else Icons.Default.LightMode,
                  title = "Theme",
                  subtitle = appTheme,
                  onClick = onNavigateToThemeSettings
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                NavRow(
                    icon = Icons.Default.Palette,
                    title = "Accent Color",
                    subtitle = accentColor.replaceFirstChar { it.uppercase() },
                    onClick = onNavigateToThemeSettings // Navigate to theme settings which now has accent selector
                )
              }
            }




            // ─── Permissions ───
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Permissions") }
            item {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                        var isServiceAlive by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            while (true) {
                                val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                                isServiceAlive = enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
                                kotlinx.coroutines.delay(1000)
                            }
                        }

                        NavRow(
                            icon = Icons.Default.Accessibility,
                            title = "Accessibility Service",
                            subtitle = if (isServiceAlive) "Running normally" else "Stopped! Tap here to re-enable",
                            titleColor = if (isServiceAlive) MaterialTheme.colorScheme.onSurface else Error,
                            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        NavRow(
                            icon = Icons.Default.Notifications,
                            title = "Notification Access",
                            subtitle = "Required for notification capture",
                            onClick = {
                                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            powerManager.isIgnoringBatteryOptimizations(context.packageName)
                        } else true

                        NavRow(
                            icon = Icons.Default.BatteryChargingFull,
                            title = "Battery Optimization",
                            subtitle = if (isIgnoringBatteryOptimizations) "Exempted" else "Currently restricted (tap to exempt)",
                            titleColor = if (isIgnoringBatteryOptimizations) Success else MaterialTheme.colorScheme.onSurface,
                            onClick = {
                                if (!isIgnoringBatteryOptimizations) {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                } else {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }

            // ─── Inference ───
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Inference") }
            item {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        var expanded by remember { mutableStateOf(false) }

                        OutlinedTextField(
                            value = groqApiKey,
                            onValueChange = { scope.launch { settingsDataStore.setGroqApiKey(it) } },
                            label = { Text("Groq API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = com.dvait.base.util.GroqUtils.getDisplayName(groqModel),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Groq Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                com.dvait.base.util.GroqUtils.MODELS.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                com.dvait.base.util.GroqUtils.getDisplayName(model),
                                                color = if (model == com.dvait.base.util.GroqUtils.DEFAULT_MODEL) 
                                                        MaterialTheme.colorScheme.primary 
                                                        else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            scope.launch { settingsDataStore.setGroqModel(model) }
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = { scope.launch { settingsDataStore.setGroqModel(com.dvait.base.util.GroqUtils.DEFAULT_MODEL) } },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reset to default")
                        }
                    }
                }
            }

            // ─── Capture ───
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Capture") }
            item {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Notifications", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Capture notification text",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = captureNotifications,
                                onCheckedChange = { scope.launch { settingsDataStore.setCaptureNotifications(it) } },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        NavRow(
                            icon = Icons.Default.FilterList,
                            title = "App Filter",
                            subtitle = "Choose which apps to capture",
                            onClick = onViewAppFilter
                        )
                    }
                }
            }

            // ─── Data ───
            item { Spacer(Modifier.height(8.dp)); SectionLabel("Data") }
            item {
                SettingsCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        NavRow(
                            icon = Icons.Default.Storage,
                            title = "Captured Data",
                            subtitle = "$dataCount entries",
                            onClick = onViewData
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        NavRow(
                            icon = Icons.Default.CleaningServices,
                            title = "Deduplicate Data",
                            subtitle = "Remove duplicate entries (debug)",
                            onClick = {
                                showDedupDialog = true
                                isDedupRunning = true
                                dedupProgress = 0
                                dedupTotal = 0
                                dedupRemoved = 0
                                scope.launch {
                                    repository.deduplicate(embeddingEngine) { processed, total, removed ->
                                        dedupProgress = processed
                                        dedupTotal = total
                                        dedupRemoved = removed
                                    }
                                    isDedupRunning = false
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        NavRow(
                            icon = Icons.Default.Delete,
                            title = "Clear All Data",
                            subtitle = "Permanently delete everything",
                            titleColor = Error,
                            onClick = { showClearDialog = true }
                        )
                    }
                }
            }


        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Clear All Data?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This will permanently delete all captured entries.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.clearAll()
                    }
                    showClearDialog = false
                }) { Text("Clear", color = Error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showDedupDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDedupRunning) showDedupDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isDedupRunning) "Deduplicating…" else "Deduplication Complete", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    if (isDedupRunning) {
                        LinearProgressIndicator(
                            progress = { if (dedupTotal > 0) dedupProgress.toFloat() / dedupTotal else 0f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Scanned: $dedupProgress / $dedupTotal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Duplicates removed: $dedupRemoved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dedupRemoved > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (!isDedupRunning) {
                    TextButton(onClick = { showDedupDialog = false }) {
                        Text("Done", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

