package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FollowRepository : JpaRepository<Follow, FollowId> {
    @Modifying
    @Query(
        value = "INSERT INTO follows (follower_id, followee_id, created_at) " +
            "VALUES (:followerId, :followeeId, NOW()) " +
            "ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("followerId") followerId: Long, @Param("followeeId") followeeId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId",
        nativeQuery = true,
    )
    fun deleteOne(@Param("followerId") followerId: Long, @Param("followeeId") followeeId: Long): Int

    fun existsByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Boolean
    fun findByFollowerIdAndFolloweeIdIn(followerId: Long, followeeIds: Collection<Long>): List<Follow>

    /** 내가 팔로우하는 사용자 목록 (최신순) — DM 수신자 picker / User.following 용 */
    fun findByFollowerIdOrderByCreatedAtDesc(followerId: Long, pageable: Pageable): List<Follow>

    /** 나를 팔로우하는 사용자 목록 (최신순) — User.followers 용 */
    fun findByFolloweeIdOrderByCreatedAtDesc(followeeId: Long, pageable: Pageable): List<Follow>
}
