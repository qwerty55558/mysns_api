package com.mysns.main.graphql.data

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
}
