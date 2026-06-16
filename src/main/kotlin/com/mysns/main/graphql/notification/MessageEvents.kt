package com.mysns.main.graphql.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class MessageSentEvent(
    val recipientId: Long,
    val senderId: Long,
    val conversationId: Long,
    val messageId: Long,
)

@Component
class MessageSseListener(private val sse: NotificationSseRegistry) {

    private val log = LoggerFactory.getLogger(MessageSseListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: MessageSentEvent) {
        val payload = mapOf(
            "conversationId" to event.conversationId.toString(),
            "messageId" to event.messageId.toString(),
            "senderId" to event.senderId.toString(),
        )
        try {
            sse.push(event.recipientId, "message", payload)
        } catch (ex: Exception) {
            log.debug("Failed to push message SSE to recipient ${event.recipientId}", ex)
        }
        try {
            sse.push(event.senderId, "message", payload)
        } catch (ex: Exception) {
            log.debug("Failed to push message SSE to sender ${event.senderId}", ex)
        }
    }
}
