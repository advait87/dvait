package com.dvait.base.data.repository

import com.dvait.base.data.model.Conversation
import com.dvait.base.data.model.Conversation_
import com.dvait.base.data.model.Message
import io.objectbox.Box
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

class ConversationRepository(private val boxStore: BoxStore) {
    private val conversationBox: Box<Conversation> = boxStore.boxFor(Conversation::class.java)
    private val messageBox: Box<Message> = boxStore.boxFor(Message::class.java)

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: Flow<List<Conversation>> = _conversations

    init {
        updateConversations()
    }

    private fun updateConversations() {
        _conversations.value = conversationBox.query()
            .orderDesc(Conversation_ .timestamp)
            .build()
            .find()
    }

    suspend fun createConversation(title: String): Conversation = withContext(Dispatchers.IO) {
        val conv = Conversation(title = title)
        conversationBox.put(conv)
        updateConversations()
        conv
    }

    suspend fun saveMessage(
        conversationId: Long,
        role: String,
        content: String,
        fullPrompt: String? = null,
        debugContext: String? = null
    ) = withContext(Dispatchers.IO) {
        val conv = conversationBox.get(conversationId) ?: return@withContext
        val msg = Message(role = role, content = content, fullPrompt = fullPrompt, debugContext = debugContext)
        msg.conversation.target = conv
        messageBox.put(msg)
        
        // Update conversation timestamp to bring it to top
        conv.timestamp = System.currentTimeMillis()
        conversationBox.put(conv)
        updateConversations()
    }

    suspend fun getMessages(conversationId: Long): List<Message> = withContext(Dispatchers.IO) {
        messageBox.query()
            .equal(com.dvait.base.data.model.Message_.conversationId, conversationId)
            .order(com.dvait.base.data.model.Message_.timestamp)
            .build()
            .find()
    }

    suspend fun deleteMessage(messageId: Long) = withContext(Dispatchers.IO) {
        messageBox.remove(messageId)
    }

    suspend fun deleteConversation(conversationId: Long) = withContext(Dispatchers.IO) {
        conversationBox.remove(conversationId)
        updateConversations()
    }

    suspend fun updateConversationTitle(conversationId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val conv = conversationBox.get(conversationId) ?: return@withContext
        conv.title = newTitle
        conversationBox.put(conv)
        updateConversations()
    }
}
