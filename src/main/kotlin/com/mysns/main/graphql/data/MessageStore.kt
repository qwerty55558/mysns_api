package com.mysns.main.graphql.data

import com.mysns.main.graphql.notification.MessageSentEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class MessageStore(
    private val messageRepository: MessageRepository,
    private val conversationStore: ConversationStore,
    private val postStore: PostStore,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun send(senderId: Long, recipientId: Long, text: String?, sharedPostId: Long?): Message {
        require(senderId != recipientId) { "cannot message yourself" }
        val cleanText = text?.trim()?.takeIf { it.isNotEmpty() }
        require(cleanText != null || sharedPostId != null) { "message must have text or a shared post" }

        val conversation = conversationStore.findOrCreate(senderId, recipientId)
        val now = OffsetDateTime.now()
        val saved = messageRepository.save(
            Message(
                conversationId = conversation.id,
                senderId = senderId,
                text = cleanText,
                sharedPostId = sharedPostId,
                createdAt = now,
            ),
        )
        conversationStore.touch(conversation, now)
        // 보낸 사람은 방금 그 대화를 읽은 것으로 처리해 본인 안읽음에 안 잡히게 한다.
        conversationStore.markRead(conversation, senderId, now)
        // 게시물 공유는 share 행위 — 기존 sharePost와 동일하게 shareCount 증가.
        if (sharedPostId != null) postStore.incrementShareCount(sharedPostId)
        events.publishEvent(
            MessageSentEvent(
                recipientId = recipientId,
                senderId = senderId,
                conversationId = conversation.id,
                messageId = saved.id,
            )
        )
        return saved
    }

    fun list(conversationId: Long, limit: Int, offset: Int): List<Message> {
        val page = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, page)
    }
}
