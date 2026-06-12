package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * DM 한 통. text(일반 텍스트) 또는 sharedPostId(게시물 공유) 중 하나 이상을 가진다.
 */
@Entity
@Table(
    name = "messages",
    indexes = [
        Index(name = "idx_messages_conversation_created", columnList = "conversation_id,created_at"),
    ],
)
class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "conversation_id", nullable = false)
    val conversationId: Long,

    @Column(name = "sender_id", nullable = false)
    val senderId: Long,

    @Column(columnDefinition = "text")
    val text: String? = null,

    @Column(name = "shared_post_id")
    val sharedPostId: Long? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
