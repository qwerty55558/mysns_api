package com.mysns.main.graphql.data

import com.mysns.main.config.NotificationProperties
import com.mysns.main.graphql.data.NotificationType.COMMENT_LIKE
import com.mysns.main.graphql.data.NotificationType.FOLLOW
import com.mysns.main.graphql.data.NotificationType.FOLLOW_REQUEST
import com.mysns.main.graphql.data.NotificationType.POST_LIKE
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class NotificationStore(
    private val repo: NotificationRepository,
    private val props: NotificationProperties,
) {
    companion object {
        val THROTTLED = setOf(FOLLOW, FOLLOW_REQUEST, POST_LIKE, COMMENT_LIKE)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun create(recipientId: Long, actorId: Long, type: NotificationType, entityId: Long?): Boolean {
        if (recipientId == actorId) return false
        return if (type in THROTTLED) {
            repo.insertWithCooldown(
                recipientId,
                actorId,
                type.name,
                entityId,
                OffsetDateTime.now().minus(props.cooldown),
            ) > 0
        } else {
            repo.save(
                Notification(
                    recipientId = recipientId,
                    actorId = actorId,
                    type = type,
                    entityId = entityId,
                )
            )
            true
        }
    }

    fun list(recipientId: Long, limit: Int, offset: Int): List<Notification> =
        repo.findByRecipientIdOrderByCreatedAtDesc(
            recipientId,
            PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1)),
        )

    fun unreadCount(recipientId: Long): Int =
        repo.countByRecipientIdAndIsReadFalse(recipientId).toInt()

    @Transactional
    fun markRead(recipientId: Long, id: Long): Boolean =
        repo.markRead(id, recipientId) > 0

    @Transactional
    fun markAllRead(recipientId: Long): Int =
        repo.markAllRead(recipientId)
}
