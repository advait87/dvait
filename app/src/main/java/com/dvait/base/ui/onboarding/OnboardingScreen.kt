package com.dvait.base.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dvait.base.R
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.ui.theme.*
import com.dvait.base.ui.components.AccentSelectorCircle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    settingsDataStore: SettingsDataStore,
    onFinish: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })
    val accentColor by settingsDataStore.accentColor.collectAsState(initial = "orange")
    val whitelistedApps by settingsDataStore.whitelistedApps.collectAsState(initial = emptySet())
    val groqApiKey by settingsDataStore.groqApiKey.collectAsState(initial = "")
    val groqModel by settingsDataStore.groqModel.collectAsState(initial = com.dvait.base.util.GroqUtils.DEFAULT_MODEL)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> PermissionsPage()
                    2 -> AppFilterPage(
                        settingsDataStore = settingsDataStore,
                        whitelistedApps = whitelistedApps
                    )
                    3 -> GroqConfigPage(
                        apiKey = groqApiKey,
                        selectedModel = groqModel,
                        onApiKeyChanged = { scope.launch { settingsDataStore.setGroqApiKey(it) } },
                        onModelSelected = { scope.launch { settingsDataStore.setGroqModel(it) } }
                    )
                    4 -> AccentColorPage(
                        currentAccent = accentColor,
                        onAccentSelected = { scope.launch { settingsDataStore.setAccentColor(it) } }
                    )
                }
            }

            // Bottom Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(5) { i ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                if (pagerState.currentPage < 4) {
                    Button(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                settingsDataStore.setOnboardingCompleted(true)
                                onFinish()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val accentPalette = LocalAccentPalette.current
    val logoRes = getLogoForAccent(accentPalette)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = logoRes),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp)
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Welcome to dvait",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your digital memory. dvait securely captures what you see on screen and hear in notifications, allowing you to recall anything later with ease.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var isBatteryOptimized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            isAccessibilityEnabled = enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            isNotificationEnabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                !powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else false

            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "To work efficiently, dvait needs a few essential permissions. Don't worry, all data stays on your device.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))

        PermissionItem(
            title = "Accessibility Service",
            subtitle = "Required to capture screen text securely.",
            isGranted = isAccessibilityEnabled,
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )
        Spacer(Modifier.height(16.dp))
        PermissionItem(
            title = "Notification Access",
            subtitle = "Required to capture incoming notifications.",
            isGranted = isNotificationEnabled,
            onClick = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        )
        Spacer(Modifier.height(16.dp))
        PermissionItem(
            title = "Battery Optimization",
            subtitle = "Exempting dvait ensures capture stays active.",
            isGranted = !isBatteryOptimized,
            onClick = {
                if (isBatteryOptimized) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            }
        )
    }
}

@Composable
private fun PermissionItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) Success else MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isGranted) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccentColorPage(
    currentAccent: String,
    onAccentSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Pick Your Accent",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Customize the look and feel of dvait to match your style.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AccentSelectorCircle(OrangePalette, currentAccent == "orange") { onAccentSelected("orange") }
            AccentSelectorCircle(TealPalette, currentAccent == "teal") { onAccentSelected("teal") }
        }
        Spacer(Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AccentSelectorCircle(BluePalette, currentAccent == "blue") { onAccentSelected("blue") }
            AccentSelectorCircle(MonoPalette, currentAccent == "mono") { onAccentSelected("mono") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppFilterPage(
    settingsDataStore: SettingsDataStore,
    whitelistedApps: Set<String>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    val installedApps = remember { com.dvait.base.util.AppUtils.getInstalledApps(context) }

    val filteredAndSortedApps = remember(installedApps, whitelistedApps, searchQuery) {
        installedApps
            .filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<com.dvait.base.util.AppItem> { it.packageName in whitelistedApps }
                    .thenBy { it.label.lowercase() }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Select Apps",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Select up to 3 apps you want dvait to monitor. You can change this later in settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredAndSortedApps, key = { it.packageName }) { app ->
                val isChecked = app.packageName in whitelistedApps
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isChecked && whitelistedApps.size >= 3) return@clickable
                            scope.launch {
                                val updated = if (isChecked) whitelistedApps - app.packageName
                                else whitelistedApps + app.packageName
                                settingsDataStore.setWhitelistedApps(updated)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked && whitelistedApps.size >= 3) return@Checkbox
                                scope.launch {
                                    val updated = if (checked) whitelistedApps + app.packageName
                                    else whitelistedApps - app.packageName
                                    settingsDataStore.setWhitelistedApps(updated)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroqConfigPage(
    apiKey: String,
    selectedModel: String,
    onApiKeyChanged: (String) -> Unit,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AI Configuration",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "dvait uses Groq for lightning-fast responses. Enter your API key and choose a model to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Groq API Key") },
            placeholder = { Text("gsk_...") },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = com.dvait.base.util.GroqUtils.getDisplayName(selectedModel),
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Model") },
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
                            onModelSelected(model)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(
            onClick = { onModelSelected(com.dvait.base.util.GroqUtils.DEFAULT_MODEL) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset to default")
        }
    }
}
