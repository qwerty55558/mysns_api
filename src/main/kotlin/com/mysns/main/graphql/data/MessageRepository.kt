package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface MessageRepository : JpaRepository<Message, Long> {

    fun findByConversationIdOrderByCreatedAtDesc(conversationId: Long, pageable: Pageable): List<Message>

    fun findFirstByConversationIdOrderByCreatedAtDesc(conversationId: Long): Message?

    /** 안읽음 카운트: 상대가 보낸(=내가 안 보낸) 메시지 중 last-read 이후. */
    fun countByConversationIdAndSenderIdNotAndCreatedAtAfter(
        conversationId: Long,
        senderId: Long,
        createdAt: OffsetDateTime,
    ): Long

    /** last-read 타임스탬프가 없을 때: 상대가 보낸 전체. */
    fun countByConversationIdAndSenderIdNot(conversationId: Long, senderId: Long): Long
}
