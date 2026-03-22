package com.dvait.base.ui.chat

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dvait.base.engine.QueryEngine
import com.dvait.base.ui.theme.*
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.data.model.Conversation
import com.dvait.base.util.FileLogger
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector

data class ChatMessage(
    val id: Long = 0L,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedQuestion: String? = null,
    val fullPrompt: String? = null,
    val debugContext: String? = null,
    val followUpOptions: List<String> = emptyList(),
    val isFollowUp: Boolean = false
)

enum class InferenceMode(val title: String, val description: String) {
    AUTO("Auto", "Selects the best model for your specific question."),
    FASTER("Faster", "High-speed responses for simple queries."),
    SMARTER("Smarter", "Best for complex reasoning and tasks."),
    RLM("RLM", "Recursive Language Model for deep research.")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    queryEngine: QueryEngine,
    conversationRepository: com.dvait.base.data.repository.ConversationRepository,
    onNavigateToSettings: () -> Unit,
    onNavigateToBackendSettings: () -> Unit = {},
    settingsDataStore: SettingsDataStore,
    initialQuery: String? = null
) {
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(queryEngine, conversationRepository, settingsDataStore)
    )
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val tokensPerSecond by viewModel.tokensPerSecond.collectAsState()
    val tokenCount by viewModel.tokenCount.collectAsState()
    val statusLabel by viewModel.statusLabel.collectAsState()
    val interactionSourceBolt = remember { MutableInteractionSource() }
    val interactionSourceSend = remember { MutableInteractionSource() }
    val interactionSourceImage = remember { MutableInteractionSource() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val androidClipboard = LocalContext.current.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val context = LocalContext.current
    var showContextDialogFor by remember { mutableStateOf<ChatMessage?>(null) }
    var debugInfo by remember { mutableStateOf<QueryEngine.DebugInfo?>(null) }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            FileLogger.d("ChatScreen", "Received initial query from intent: $initialQuery")
            viewModel.sendMessage(initialQuery, context, InferenceMode.AUTO)
        }
    }
    var isContextLoading by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    // Local state to hide logo instantly before ViewModel propagates state
    var showBoltMenu by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(InferenceMode.AUTO) }
    val sheetState = rememberModalBottomSheetState()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                inputText += if (inputText.isNotEmpty()) " $spokenText" else spokenText
            }
        }
    }

    val attachedImages by viewModel.attachedImages.collectAsState()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> viewModel.attachImages(uris) }
    )

    val conversations by viewModel.conversations.collectAsState(initial = emptyList())
    LaunchedEffect(conversations) {
        FileLogger.d("ChatScreen", "Conversations updated: ${conversations.size} items")
    }
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val currentConversation = conversations.find { it.id == currentConvId }
    val pendingFollowUp by viewModel.pendingFollowUp.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Explicitly log drawer state changes
    LaunchedEffect(drawerState.currentValue) {
        FileLogger.d("ChatScreen", "Drawer state changed to: ${drawerState.currentValue}")
    }

    val scope = rememberCoroutineScope()

    // Conversation management state
    var selectedConvForMenu by remember { mutableStateOf<Conversation?>(null) }
    var showRenameDialogFor by remember { mutableStateOf<Conversation?>(null) }
    var showDeleteDialogFor by remember { mutableStateOf<Conversation?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp
            ) {
                Spacer(Modifier.height(48.dp))
                Text(
                    "CONVERSATIONS",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        viewModel.startNewChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("New Chat", color = MaterialTheme.colorScheme.onPrimary)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(conversations) { conv ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.selectConversation(conv.id)
                                            scope.launch { drawerState.close() }
                                        },
                                        onLongClick = { selectedConvForMenu = conv }
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                color = if (conv.id == currentConvId) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                                contentColor = if (conv.id == currentConvId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ) {
                                Text(
                                    text = conv.title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            DropdownMenu(
                                expanded = selectedConvForMenu?.id == conv.id,
                                onDismissRequest = { selectedConvForMenu = null },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        showRenameDialogFor = conv
                                        selectedConvForMenu = null
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showDeleteDialogFor = conv
                                        selectedConvForMenu = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentConversation?.title ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Messages area
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (isLoading) {
                            item(key = "status") { StatusBubble(statusLabel.ifEmpty { "Searching captured data..." }) }
                        }
                        
                        items(items = messages.asReversed(), key = { it.timestamp }) { message ->
                            if (message.isFollowUp && message.followUpOptions.isNotEmpty()) {
                                FollowUpBubble(
                                    message = message,
                                    onOptionSelected = { option ->
                                        viewModel.answerFollowUp(option, context)
                                    },
                                    onDeleteMessage = {
                                        viewModel.deleteMessage(message.id)
                                    }
                                )
                            } else {
                            ChatBubble(
                                message = message,
                                onCopy = {
                                    androidClipboard.setPrimaryClip(android.content.ClipData.newPlainText("Copied Text", message.text))
                                },
                                onDeleteMessage = {
                                    viewModel.deleteMessage(message.id)
                                }
                            )
                            }
                        }
                    }
                }

                // Stats bar
                AnimatedVisibility(visible = isStreaming || (tokenCount > 0 && tokensPerSecond > 0f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isStreaming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (isStreaming) statusLabel.ifEmpty { "Generating..." } else "Complete",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isStreaming) MaterialTheme.colorScheme.onSurfaceVariant else Success
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "$tokenCount tok",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "%.1f t/s".format(tokensPerSecond),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }



                // Input Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {


                        // Center: Input Pill
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            enabled = !isLoading && !isStreaming,
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        )
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (inputText.isEmpty()) {
                                            Text(
                                                "Ask dvait...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp)
                                            )
                                        }
                                        innerTextField()
                                    }

                                }
                            }
                        )

                        Spacer(Modifier.width(12.dp))

                        // Right Button: Voice/Waveform
                        val showVoice = inputText.isBlank() && attachedImages.isEmpty()
                        val showSend = inputText.isNotBlank() || attachedImages.isNotEmpty()

                        if (showVoice || showSend) {
                            Surface(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(
                                        interactionSource = interactionSourceSend,
                                        indication = null
                                    ) {
                                        if (inputText.isNotBlank() || attachedImages.isNotEmpty()) {
                                            viewModel.sendMessage(inputText.trim(), context, selectedMode)
                                            inputText = ""
                                        } else {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            }
                                            try {
                                                speechRecognizerLauncher.launch(intent)
                                            } catch (e: Exception) {}
                                        }
                                    },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (inputText.isNotBlank() || attachedImages.isNotEmpty()) Icons.AutoMirrored.Filled.Send else Icons.Default.GraphicEq,
                                        contentDescription = "Voice/Send",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Welcome overlay perfectly centered on screen
    androidx.compose.animation.AnimatedVisibility(
        visible = messages.isEmpty() && !isLoading,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            WelcomeView(modelLoaded = false)
        }
    }
}

    if (showContextDialogFor != null) {
        val message = showContextDialogFor!!

        LaunchedEffect(message) {
            debugInfo = viewModel.fetchContextForMessage(message)
            isContextLoading = false
        }

        AlertDialog(
            onDismissRequest = {
                showContextDialogFor = null
                debugInfo = null
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Query Context", color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = {
                        showContextDialogFor = null
                        debugInfo = null
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column {
                    if (!isContextLoading && debugInfo != null) {
                        SecondaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = {},
                            indicator = {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(selectedTab),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Matches", style = MaterialTheme.typography.labelLarge) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Full Prompt", style = MaterialTheme.typography.labelLarge) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (isContextLoading) {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        debugInfo?.let { info ->
                            Column(modifier = Modifier.fillMaxHeight(0.7f).fillMaxWidth()) {
                                if (selectedTab == 0) {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (info.matches.isEmpty()) {
                                            item {
                                                Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        items(info.matches) { scoredItem ->
                                            val entry = scoredItem.capturedText
                                            val similarity = 1f - scoredItem.score
                                            var expandedContext by remember { mutableStateOf(false) }

                                            val time = java.text.SimpleDateFormat(
                                                "MMM dd, HH:mm", java.util.Locale.getDefault()
                                            ).format(java.util.Date(entry.timestamp))

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column {
                                                            Text(
                                                                entry.sourceType,
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(
                                                                entry.sourceApp,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            Text(
                                                                time,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "Sim: ${"%.4f".format(similarity)}",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                                color = if (similarity > 0.8) Success else MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(6.dp))

                                                    val isLongText = entry.text.length > 300
                                                    val displayText = if (expandedContext || !isLongText) entry.text else entry.text.take(300) + "…"

                                                    Text(
                                                        text = displayText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )

                                                    if (isLongText) {
                                                        Text(
                                                            text = if (expandedContext) "Show Less" else "Read More",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier
                                                                .padding(top = 8.dp)
                                                                .clickable { expandedContext = !expandedContext }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Full Prompt View
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = {
                                                androidClipboard.setPrimaryClip(android.content.ClipData.newPlainText("Copied Text", info.fullPrompt))
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                                .padding(8.dp)
                                        ) {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                item {
                                                    Text(
                                                        text = info.fullPrompt,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Rename Dialog
    showRenameDialogFor?.let { conv ->
        var newTitle by remember { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialogFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Rename Conversation", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) {
                        viewModel.renameConversation(conv.id, newTitle)
                        showRenameDialogFor = null
                    }
                }) {
                    Text("Rename", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialogFor = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Delete Dialog
    showDeleteDialogFor?.let { conv ->
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete Conversation?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This will permanently delete this conversation and all its messages.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConversation(conv.id)
                    showDeleteDialogFor = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showBoltMenu) {
        ModalBottomSheet(
            onDismissRequest = { showBoltMenu = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text(
                    "Select Mode",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                BoltMenuItem(
                    title = InferenceMode.AUTO.title,
                    description = InferenceMode.AUTO.description,
                    icon = Icons.Default.AutoMode,
                    isSelected = selectedMode == InferenceMode.AUTO,
                    onClick = {
                        selectedMode = InferenceMode.AUTO
                        showBoltMenu = false
                    }
                )

                BoltMenuItem(
                    title = InferenceMode.FASTER.title,
                    description = InferenceMode.FASTER.description,
                    icon = Icons.Default.Bolt,
                    isSelected = selectedMode == InferenceMode.FASTER,
                    onClick = {
                        selectedMode = InferenceMode.FASTER
                        showBoltMenu = false
                    }
                )

                BoltMenuItem(
                    title = InferenceMode.SMARTER.title,
                    description = InferenceMode.SMARTER.description,
                    icon = Icons.Default.Psychology,
                    isSelected = selectedMode == InferenceMode.SMARTER,
                    onClick = {
                        selectedMode = InferenceMode.SMARTER
                        showBoltMenu = false
                    }
                )

                BoltMenuItem(
                    title = InferenceMode.RLM.title,
                    description = InferenceMode.RLM.description,
                    icon = Icons.Default.Memory,
                    isSelected = selectedMode == InferenceMode.RLM,
                    onClick = {
                        selectedMode = InferenceMode.RLM
                        showBoltMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun BoltMenuItem(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) {
                    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else Color(0xFFF0F0F0) // Correct gray for light mode
                } else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Selection indicator removed as per user request
    }
}

@Composable
private fun WelcomeView(modelLoaded: Boolean) {
    Surface(
        modifier = Modifier.size(144.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = getLogoForAccent(LocalAccentPalette.current)),
            contentDescription = "dvait Logo",
            modifier = Modifier.size(64.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopy: () -> Unit = {},
    onDeleteMessage: () -> Unit = {}
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {

        Box {
            Surface(
                color = bubbleColor,
                shape = shape,
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    ),
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                val linkColor = LinkBlue
                val annotatedString = remember(message.text, linkColor) {
                    buildAnnotatedString {
                        append(message.text)

                        // Find URLs
                        val urlMatcher = android.util.Patterns.WEB_URL.matcher(message.text)
                        while (urlMatcher.find()) {
                            addStyle(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = urlMatcher.start(),
                                end = urlMatcher.end()
                            )
                            var url = urlMatcher.group() ?: ""
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                url = "http://$url"
                            }
                            addStringAnnotation(
                                tag = "URL",
                                annotation = url,
                                start = urlMatcher.start(),
                                end = urlMatcher.end()
                            )
                        }

                        // Find Phone Numbers
                        val phoneMatcher = android.util.Patterns.PHONE.matcher(message.text)
                        while (phoneMatcher.find()) {
                            addStyle(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = phoneMatcher.start(),
                                end = phoneMatcher.end()
                            )
                            addStringAnnotation(
                                tag = "PHONE",
                                annotation = "tel:${phoneMatcher.group()}",
                                start = phoneMatcher.start(),
                                end = phoneMatcher.end()
                            )
                        }

                        // Find Emails
                        val emailMatcher = android.util.Patterns.EMAIL_ADDRESS.matcher(message.text)
                        while (emailMatcher.find()) {
                            addStyle(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start = emailMatcher.start(),
                                end = emailMatcher.end()
                            )
                            addStringAnnotation(
                                tag = "EMAIL",
                                annotation = "mailto:${emailMatcher.group()}",
                                start = emailMatcher.start(),
                                end = emailMatcher.end()
                            )
                        }
                    }
                }

                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

                Text(
                    text = annotatedString,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .pointerInput(annotatedString) {
                            detectTapGestures(
                                onLongPress = {
                                    showMenu = true
                                },
                                onTap = { pos: androidx.compose.ui.geometry.Offset ->
                                    layoutResult?.let { result ->
                                        val offset = result.getOffsetForPosition(pos)
                                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation -> uriHandler.openUri(annotation.item) }

                                        annotatedString.getStringAnnotations(tag = "PHONE", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation -> uriHandler.openUri(annotation.item) }

                                        annotatedString.getStringAnnotations(tag = "EMAIL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation -> uriHandler.openUri(annotation.item) }
                                    }
                                }
                            )
                        },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = contentColor,
                        lineHeight = 22.sp
                    ),
                    onTextLayout = { layoutResult = it }
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Copy", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        onCopy()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDeleteMessage()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun FollowUpBubble(
    message: ChatMessage,
    onOptionSelected: (String) -> Unit,
    onDeleteMessage: () -> Unit = {}
) {
    var customAnswer by remember { mutableStateOf("") }

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Box {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    ),
                tonalElevation = 1.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDeleteMessage()
                        showMenu = false
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Option chips
        if (message.followUpOptions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Each option as its own tappable pill
                message.followUpOptions.forEach { option ->
                    Surface(
                        modifier = Modifier
                            .clickable { onOptionSelected(option) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Custom answer input
        Row(
            modifier = Modifier.widthIn(max = 310.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = customAnswer,
                onValueChange = { customAnswer = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (customAnswer.isEmpty()) {
                                    Text(
                                        "Type your answer...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                innerTextField()
                            }
                        }
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clickable {
                        if (customAnswer.isNotBlank()) {
                            onOptionSelected(customAnswer.trim())
                            customAnswer = ""
                        }
                    },
                shape = CircleShape,
                color = if (customAnswer.isNotBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send answer",
                        tint = if (customAnswer.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBubble(statusLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    statusLabel.ifEmpty { "Processing..." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    }
}
}
