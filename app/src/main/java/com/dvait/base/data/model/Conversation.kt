package com.dvait.base.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany

@Entity
data class Conversation(
    @Id var id: Long = 0,
    var title: String = "",
    var timestamp: Long = System.currentTimeMillis()
) {
    lateinit var messages: ToMany<Message>
}
