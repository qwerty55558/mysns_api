package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FollowRequestRepository : JpaRepository<FollowRequest, Long> {

    @Modifying
    @Query(
        value = "INSERT INTO follow_requests (requester_id, target_id, created_at) " +
            "VALUES (:requesterId, :targetId, NOW()) " +
            "ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("requesterId") requesterId: Long, @Param("targetId") targetId: Long): Int

    fun existsByRequesterIdAndTargetId(requesterId: Long, targetId: Long): Boolean

    fun findByRequesterIdAndTargetId(requesterId: Long, targetId: Long): FollowRequest?

    fun findByRequesterIdAndTargetIdIn(
        requesterId: Long,
        targetIds: Collection<Long>,
    ): List<FollowRequest>

    fun findByTargetIdOrderByCreatedAtDesc(targetId: Long, pageable: Pageable): List<FollowRequest>

    fun findByRequesterIdOrderByCreatedAtDesc(requesterId: Long, pageable: Pageable): List<FollowRequest>

    fun countByTargetId(targetId: Long): Int
}
