package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface NotificationRepository : JpaRepository<Notification, Long> {

    @Modifying
    @Query(
        value = """
            INSERT INTO notifications (recipient_id, actor_id, type, entity_id, is_read, created_at)
            SELECT :recipientId, :actorId, :type, :entityId, false, NOW()
            WHERE NOT EXISTS (
              SELECT 1 FROM notifications
              WHERE recipient_id = :recipientId AND actor_id = :actorId AND type = :type
                AND created_at > :since)
        """,
        nativeQuery = true,
    )
    fun insertWithCooldown(
        @Param("recipientId") recipientId: Long,
        @Param("actorId") actorId: Long,
        @Param("type") type: String,
        @Param("entityId") entityId: Long?,
        @Param("since") since: OffsetDateTime,
    ): Int

    fun findByRecipientIdOrderByCreatedAtDesc(recipientId: Long, pageable: Pageable): List<Notification>

    fun countByRecipientIdAndIsReadFalse(recipientId: Long): Long

    @Modifying
    @Query(
        value = "UPDATE notifications SET is_read = true WHERE id = :id AND recipient_id = :recipientId AND is_read = false",
        nativeQuery = true,
    )
    fun markRead(@Param("id") id: Long, @Param("recipientId") recipientId: Long): Int

    @Modifying
    @Query(
        value = "UPDATE notifications SET is_read = true WHERE recipient_id = :recipientId AND is_read = false",
        nativeQuery = true,
    )
    fun markAllRead(@Param("recipientId") recipientId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM notifications WHERE type IN (:types) AND entity_id IN (:entityIds)",
        nativeQuery = true,
    )
    fun deleteByTypesAndEntityIds(
        @Param("types") types: Collection<String>,
        @Param("entityIds") entityIds: Collection<Long>,
    ): Int
}
