package com.mysns.main.graphql.notification

import com.mysns.main.graphql.data.NotificationStore
import com.mysns.main.graphql.data.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class NotificationEvent(
    val recipientId: Long,
    val actorId: Long,
    val type: NotificationType,
    val entityId: Long?,
)

@Component
class NotificationListener(
    private val store: NotificationStore,
    private val sse: NotificationSseRegistry,
) {
    private val log = LoggerFactory.getLogger(NotificationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: NotificationEvent) {
        try {
            val delivered = store.create(event.recipientId, event.actorId, event.type, event.entityId)
            if (delivered) {
                sse.push(
                    event.recipientId,
                    mapOf("type" to event.type.name, "actorId" to event.actorId.toString()),
                )
            }
        } catch (ex: Exception) {
            log.warn("Failed to process notification event $event", ex)
        }
    }
}
