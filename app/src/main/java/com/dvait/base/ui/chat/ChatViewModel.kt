package com.dvait.base.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dvait.base.engine.GenerationEvent
import com.dvait.base.engine.QueryEngine
import com.dvait.base.util.FileLogger
import com.dvait.base.util.ImageUtils
import com.dvait.base.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val queryEngine: QueryEngine,
    private val conversationRepository: com.dvait.base.data.repository.ConversationRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {


    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId

    val conversations = conversationRepository.conversations.also {
        FileLogger.d("ChatViewModel", "Flow 'conversations' accessed")
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _statusLabel = MutableStateFlow("")
    val statusLabel: StateFlow<String> = _statusLabel

    private val _tokensPerSecond = MutableStateFlow(0f)
    val tokensPerSecond: StateFlow<Float> = _tokensPerSecond

    private val _tokenCount = MutableStateFlow(0)
    val tokenCount: StateFlow<Int> = _tokenCount

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    data class PendingFollowUp(
        val question: String,
        val options: List<String>,
        val originalQuestion: String,
        val originalContext: String,
        val endpoint: String,
        val history: List<Pair<String, String>>,
        val additionalContext: String? = null
    )

    private val _pendingFollowUp = MutableStateFlow<PendingFollowUp?>(null)
    val pendingFollowUp: StateFlow<PendingFollowUp?> = _pendingFollowUp

    private val _attachedImages = MutableStateFlow<List<Uri>>(emptyList())
    val attachedImages: StateFlow<List<Uri>> = _attachedImages

    fun attachImages(uris: List<Uri>) {
        _attachedImages.value = _attachedImages.value + uris
    }

    fun removeImage(uri: Uri) {
        _attachedImages.value = _attachedImages.value.filter { it != uri }
    }

    fun selectConversation(conversationId: Long) {
        viewModelScope.launch {
            _currentConversationId.value = conversationId
            val dbMessages = conversationRepository.getMessages(conversationId)
            _messages.value = dbMessages.map {
                ChatMessage(
                    id = it.id,
                    text = it.content,
                    isUser = it.role == "user",
                    timestamp = it.timestamp,
                    fullPrompt = it.fullPrompt,
                    debugContext = it.debugContext
                )
            }
        }
    }

    fun startNewChat() {
        _currentConversationId.value = null
        _messages.value = emptyList()
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                startNewChat()
            }
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            conversationRepository.updateConversationTitle(id, newTitle)
        }
    }

    fun sendMessage(text: String, context: android.content.Context, mode: InferenceMode = InferenceMode.AUTO) {
        FileLogger.d("ChatViewModel", "sendMessage: $text with ${_attachedImages.value.size} images in mode $mode")

        val imagesToProcess = _attachedImages.value
        _attachedImages.value = emptyList() // Clear immediately to prevent double send

        // Immediate UI feedback
        _messages.value = _messages.value + ChatMessage(text = text, isUser = true)

        _isLoading.value = true
        _isStreaming.value = false
        _tokensPerSecond.value = 0f
        _tokenCount.value = 0
        _statusLabel.value = "Searching captured data..."

        viewModelScope.launch {
            try {
                // 1. Ensure conversation exists and save user message
                var convId = _currentConversationId.value
                if (convId == null) {
                    val newTitle = if (text.length > 30) text.take(27) + "..." else text
                    val newConv = conversationRepository.createConversation(newTitle)
                    convId = newConv.id
                    _currentConversationId.value = convId
                }

                FileLogger.d("ChatViewModel", "Saving user message")
                conversationRepository.saveMessage(convId, "user", text)

                // Update UI state with DB messages
                val dbMessages = conversationRepository.getMessages(convId)
                FileLogger.d("ChatViewModel", "Retrieved ${dbMessages.size} messages")
                val currentMsgs = dbMessages.map {
                    ChatMessage(id = it.id, text = it.content, isUser = it.role == "user", timestamp = it.timestamp)
                }
                _messages.value = currentMsgs
                FileLogger.d("ChatViewModel", "Updated _messages state")

                _statusLabel.value = "Vectorizing query..."

                // 2. Prepare history for QueryEngine
                val history = currentMsgs.map { (if (it.isUser) "user" else "assistant") to it.text }

                // 3. Process images to Base64
                _statusLabel.value = "Processing images..."
                val b64Images = imagesToProcess.mapNotNull { uri ->
                    ImageUtils.uriToBase64(context, uri)
                }

                val response = try {
                    FileLogger.d("ChatViewModel", "Querying engine in mode $mode...")
                    queryEngine.query(history, images = b64Images.ifEmpty { null }, mode = mode) { status ->
                        _statusLabel.value = status
                    }
                } catch (e: Exception) {
                    FileLogger.e("ChatViewModel", "Query failed", e)
                    QueryEngine.QueryResponse("Error: ${e.message}", "", "")
                }

                // Check if this is a follow-up question
                if (response.needsFollowUp && response.followUpQuestion != null) {
                    FileLogger.d("ChatViewModel", "Got follow-up question: ${response.followUpQuestion}")

                    // Save the follow-up question as an assistant message (with special flag)
                    conversationRepository.saveMessage(
                        convId, "assistant", response.followUpQuestion,
                        fullPrompt = response.fullMessagePrompt,
                        debugContext = response.debugContext
                    )

                    // Set pending follow-up state
                    _pendingFollowUp.value = PendingFollowUp(
                        question = response.followUpQuestion,
                        options = response.followUpOptions,
                        originalQuestion = response.originalQuestion ?: text,
                        originalContext = response.originalContext ?: "",
                        endpoint = response.originalEndpoint ?: "/faster_prompt",
                        history = response.originalHistory ?: history,
                        additionalContext = response.additionalContext
                    )

                    // Update UI with the follow-up question message
                    _messages.value = conversationRepository.getMessages(convId).map {
                        val isFollowUpMsg = it.role == "assistant" && it.content == response.followUpQuestion
                        ChatMessage(
                            id = it.id,
                            text = it.content,
                            isUser = it.role == "user",
                            timestamp = it.timestamp,
                            fullPrompt = it.fullPrompt,
                            debugContext = it.debugContext,
                            isFollowUp = isFollowUpMsg,
                            followUpOptions = if (isFollowUpMsg) response.followUpOptions else emptyList()
                        )
                    }
                } else {
                    FileLogger.d("ChatViewModel", "Saving assistant response")
                    // 3. Save assistant response with prompt and context
                    conversationRepository.saveMessage(
                        convId,
                        "assistant",
                        response.answer,
                        fullPrompt = response.fullMessagePrompt,
                        debugContext = response.debugContext
                    )

                    // Update UI state again
                    _messages.value = conversationRepository.getMessages(convId).map {
                        ChatMessage(
                            id = it.id,
                            text = it.content,
                            isUser = it.role == "user",
                            timestamp = it.timestamp,
                            fullPrompt = it.fullPrompt,
                            debugContext = it.debugContext
                        )
                    }
                    FileLogger.d("ChatViewModel", "Messages updated after response. Size: ${_messages.value.size}")
                }

            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "Critical error in sendMessage", e)
                _messages.value = _messages.value + ChatMessage(
                    text = "Error: ${e.message ?: "Something went wrong"}", isUser = false
                )
            } finally {
                _isLoading.value = false
                _isStreaming.value = false
                _statusLabel.value = ""
                FileLogger.d("ChatViewModel", "sendMessage complete")
            }
        }
    }

    fun answerFollowUp(answerText: String, context: android.content.Context) {
        val pending = _pendingFollowUp.value ?: return
        _pendingFollowUp.value = null

        FileLogger.d("ChatViewModel", "answerFollowUp: $answerText")

        // Show user's answer as a message
        _messages.value = _messages.value + ChatMessage(text = answerText, isUser = true)
        _isLoading.value = true
        _statusLabel.value = "Processing your answer..."

        viewModelScope.launch {
            try {
                var convId = _currentConversationId.value ?: return@launch

                // Save user's follow-up answer
                conversationRepository.saveMessage(convId, "user", answerText)

                // Re-query with follow-up answer and any additional context
                val response = try {
                    queryEngine.queryWithFollowUp(
                        originalQuestion = pending.originalQuestion,
                        originalContext = pending.originalContext,
                        endpoint = pending.endpoint,
                        history = pending.history,
                        followUpAnswer = answerText,
                        additionalContext = pending.additionalContext
                    ) { status ->
                        _statusLabel.value = status
                    }
                } catch (e: Exception) {
                    FileLogger.e("ChatViewModel", "Follow-up query failed", e)
                    QueryEngine.QueryResponse("Error: ${e.message}", "", "")
                }

                if (response.needsFollowUp && response.followUpQuestion != null) {
                    // Another follow-up question
                    conversationRepository.saveMessage(
                        convId, "assistant", response.followUpQuestion,
                        fullPrompt = response.fullMessagePrompt,
                        debugContext = response.debugContext
                    )
                    _pendingFollowUp.value = PendingFollowUp(
                        question = response.followUpQuestion,
                        options = response.followUpOptions,
                        originalQuestion = response.originalQuestion ?: pending.originalQuestion,
                        originalContext = response.originalContext ?: pending.originalContext,
                        endpoint = response.originalEndpoint ?: pending.endpoint,
                        history = response.originalHistory ?: pending.history,
                        additionalContext = response.additionalContext
                    )
                    _messages.value = conversationRepository.getMessages(convId).map {
                        val isFollowUpMsg = it.role == "assistant" && it.content == response.followUpQuestion
                        ChatMessage(
                            id = it.id,
                            text = it.content,
                            isUser = it.role == "user",
                            timestamp = it.timestamp,
                            fullPrompt = it.fullPrompt,
                            debugContext = it.debugContext,
                            isFollowUp = isFollowUpMsg,
                            followUpOptions = if (isFollowUpMsg) response.followUpOptions else emptyList()
                        )
                    }
                } else {
                    conversationRepository.saveMessage(
                        convId, "assistant", response.answer,
                        fullPrompt = response.fullMessagePrompt,
                        debugContext = response.debugContext
                    )
                    _messages.value = conversationRepository.getMessages(convId).map {
                        ChatMessage(
                            id = it.id,
                            text = it.content,
                            isUser = it.role == "user",
                            timestamp = it.timestamp,
                            fullPrompt = it.fullPrompt,
                            debugContext = it.debugContext
                        )
                    }
                }
            } catch (e: Exception) {
                FileLogger.e("ChatViewModel", "Critical error in answerFollowUp", e)
                _messages.value = _messages.value + ChatMessage(
                    text = "Error: ${e.message ?: "Something went wrong"}", isUser = false
                )
            } finally {
                _isLoading.value = false
                _statusLabel.value = ""
            }
        }
    }


    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            conversationRepository.deleteMessage(messageId)
            _currentConversationId.value?.let { convId ->
                val dbMessages = conversationRepository.getMessages(convId)
                _messages.value = dbMessages.map {
                    ChatMessage(
                        id = it.id,
                        text = it.content,
                        isUser = it.role == "user",
                        timestamp = it.timestamp,
                        fullPrompt = it.fullPrompt,
                        debugContext = it.debugContext
                    )
                }
            }
        }
    }


    suspend fun fetchContextForMessage(message: ChatMessage): QueryEngine.DebugInfo? {
        // Return stored debug info directly if available
        if (message.fullPrompt != null && message.debugContext != null) {
            return QueryEngine.DebugInfo(emptyList(), message.debugContext, message.fullPrompt)
        }

        // Fallback for older messages without stored prompt
        return try {
            queryEngine.getDebugInfo(message.text)
        } catch (e: Exception) {
            null
        }
    }
}

class ChatViewModelFactory(
    private val queryEngine: QueryEngine,
    private val conversationRepository: com.dvait.base.data.repository.ConversationRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(queryEngine, conversationRepository, settingsDataStore) as T
    }
}
