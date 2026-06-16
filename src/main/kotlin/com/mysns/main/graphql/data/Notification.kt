package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime

enum class NotificationType {
    FOLLOW, FOLLOW_REQUEST, FOLLOW_ACCEPTED, POST_LIKE, COMMENT, COMMENT_LIKE, SPLIT_REQUEST
}

@Entity
@Table(
    name = "notifications",
    indexes = [
        Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at"),
        Index(name = "idx_notifications_cooldown", columnList = "recipient_id, actor_id, type, created_at"),
    ],
)
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: Long,

    @Column(name = "actor_id", nullable = false)
    val actorId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val type: NotificationType,

    @Column(name = "entity_id")
    val entityId: Long? = null,

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
