package com.mysns.main.graphql.data

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class ConversationStore(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {
    private fun normalize(a: Long, b: Long): Pair<Long, Long> =
        if (a <= b) a to b else b to a

    @Transactional
    fun findOrCreate(a: Long, b: Long): Conversation {
        val (lo, hi) = normalize(a, b)
        conversationRepository.findByUser1IdAndUser2Id(lo, hi)?.let { return it }
        return conversationRepository.save(Conversation(user1Id = lo, user2Id = hi))
    }

    fun findExisting(a: Long, b: Long): Conversation? {
        val (lo, hi) = normalize(a, b)
        return conversationRepository.findByUser1IdAndUser2Id(lo, hi)
    }

    fun findById(id: Long): Conversation? = conversationRepository.findById(id).orElse(null)

    fun listFor(userId: Long, limit: Int, offset: Int): List<Conversation> {
        val page = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
        return conversationRepository.findForUser(userId, page)
    }

    fun lastMessage(conversation: Conversation): Message? =
        messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.id)

    fun unreadCountFor(conversation: Conversation, viewerId: Long): Int {
        val lastRead = conversation.lastReadAtFor(viewerId)
        val count = if (lastRead == null) {
            messageRepository.countByConversationIdAndSenderIdNot(conversation.id, viewerId)
        } else {
            messageRepository.countByConversationIdAndSenderIdNotAndCreatedAtAfter(
                conversation.id, viewerId, lastRead,
            )
        }
        return count.toInt()
    }

    fun totalUnread(userId: Long): Int =
        listFor(userId, MAX_CONVERSATIONS, 0).sumOf { unreadCountFor(it, userId) }

    @Transactional
    fun touch(conversation: Conversation, at: OffsetDateTime) {
        conversation.updatedAt = at
        conversationRepository.save(conversation)
    }

    @Transactional
    fun markRead(conversation: Conversation, viewerId: Long, at: OffsetDateTime = OffsetDateTime.now()): Conversation {
        conversation.setLastReadAt(viewerId, at)
        return conversationRepository.save(conversation)
    }

    private companion object {
        // 안읽음 총합 계산 시 한 사용자가 가질 수 있는 대화 수 상한 (MVP)
        const val MAX_CONVERSATIONS = 1000
    }
}
