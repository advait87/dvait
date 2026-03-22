package com.dvait.base.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToOne

@Entity
data class Message(
    @Id var id: Long = 0,
    var role: String = "", // "user" or "assistant"
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var fullPrompt: String? = null,
    var debugContext: String? = null
) {
    lateinit var conversation: ToOne<Conversation>
}
