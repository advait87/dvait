package com.dvait.base.ui.debug

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.dvait.base.data.model.CapturedText
import com.dvait.base.data.repository.CapturedTextRepository
import com.dvait.base.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataViewerScreen(
    repository: CapturedTextRepository,
    onBack: () -> Unit
) {
    // var entries by remember { mutableStateOf<List<CapturedText>>(emptyList()) }
    var totalCount by remember { mutableLongStateOf(0L) }
    
    // Multi-select state
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val isSelectionMode by remember { derivedStateOf { selectedIds.isNotEmpty() } }
    
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val copyEntries: (List<CapturedText>) -> Unit = { list ->
        val text = list.joinToString("\n---\n") { it.text }
        clipboardManager.setText(AnnotatedString(text))
    }

    val shareEntries: (List<CapturedText>) -> Unit = { list ->
        val text = list.joinToString("\n---\n") { it.text }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    val entriesFlow = remember { repository.getRecentFlow() }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    
    LaunchedEffect(entries) {
        totalCount = repository.count()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectionMode) "Selected: ${selectedIds.size}" else "Captured Data ($totalCount)",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            val selected = entries.filter { it.id in selectedIds }
                            copyEntries(selected)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Selected")
                        }
                        IconButton(onClick = {
                            val selected = entries.filter { it.id in selectedIds }
                            shareEntries(selected)
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Selected")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text(
                    "No captured data yet.\n\nEnable the Accessibility Service in Settings to start capturing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DataEntryCard(
                        entry = entry,
                        isSelected = entry.id in selectedIds,
                        isSelectionMode = isSelectionMode,
                        onToggleSelection = {
                            selectedIds = if (entry.id in selectedIds) {
                                selectedIds - entry.id
                            } else {
                                selectedIds + entry.id
                            }
                        },
                        onCopy = { copyEntries(listOf(entry)) },
                        onShare = { shareEntries(listOf(entry)) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DataEntryCard(
    entry: CapturedText,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val time = java.text.SimpleDateFormat(
        "MMM dd, HH:mm:ss", java.util.Locale.getDefault()
    ).format(java.util.Date(entry.timestamp))

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelection() else expanded = !expanded
                },
                onLongClick = {
                    onToggleSelection()
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        entry.sourceType,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!isSelectionMode) {
                    Row {
                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text(
                entry.sourceApp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(6.dp))

            val isLongText = entry.text.length > 300
            val displayText = if (expanded || !isLongText) entry.text else entry.text.take(300) + "…"

            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val annotatedString = remember(displayText) {
                buildAnnotatedString {
                    append(displayText)
                    val urlMatcher = android.util.Patterns.WEB_URL.matcher(displayText)
                    while (urlMatcher.find()) {
                        addStyle(
                            style = SpanStyle(
                                color = LinkBlue,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Bold
                            ),
                            start = urlMatcher.start(),
                            end = urlMatcher.end()
                        )
                    }
                }
            }

            Text(
                text = annotatedString,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable {
                    val offset = 0 // Optimization: we don't have layout results here for simplicity
                    // If we want full clickable links in viewer we'd need more logic, 
                    // but usually just making them white is what's asked.
                }
            )
            
            if (isLongText && !isSelectionMode) {
                Text(
                    text = if (expanded) "Show Less" else "Read More",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { expanded = !expanded }
                )
            }
        }
    }
}
