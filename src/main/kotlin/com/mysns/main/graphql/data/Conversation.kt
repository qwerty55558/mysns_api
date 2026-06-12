package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

/**
 * 1:1 DM 대화방. 같은 두 사용자 사이에 대화가 하나만 생기도록 (user1Id, user2Id)를
 * 항상 작은 id = user1, 큰 id = user2 로 정규화해 저장하고 unique 제약을 건다.
 * 안읽음 계산은 참여자별 last-read 타임스탬프 기준.
 */
@Entity
@Table(
    name = "conversations",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_conversations_pair", columnNames = ["user1_id", "user2_id"]),
    ],
    indexes = [
        Index(name = "idx_conversations_user1_updated", columnList = "user1_id,updated_at"),
        Index(name = "idx_conversations_user2_updated", columnList = "user2_id,updated_at"),
    ],
)
class Conversation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user1_id", nullable = false)
    val user1Id: Long,

    @Column(name = "user2_id", nullable = false)
    val user2Id: Long,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "user1_last_read_at")
    var user1LastReadAt: OffsetDateTime? = null,

    @Column(name = "user2_last_read_at")
    var user2LastReadAt: OffsetDateTime? = null,
) {
    fun otherUserId(viewerId: Long): Long = if (viewerId == user1Id) user2Id else user1Id

    fun hasParticipant(userId: Long): Boolean = userId == user1Id || userId == user2Id

    fun lastReadAtFor(viewerId: Long): OffsetDateTime? =
        if (viewerId == user1Id) user1LastReadAt else user2LastReadAt

    fun setLastReadAt(viewerId: Long, at: OffsetDateTime) {
        if (viewerId == user1Id) user1LastReadAt = at else if (viewerId == user2Id) user2LastReadAt = at
    }
}
