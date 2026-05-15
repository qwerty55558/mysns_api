package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeRepository : JpaRepository<Like, LikeId> {
    @Modifying
    @Query(
        value = "INSERT INTO post_likes (user_id, post_id, created_at) " +
            "VALUES (:userId, :postId, NOW()) " +
            "ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM post_likes WHERE user_id = :userId AND post_id = :postId",
        nativeQuery = true,
    )
    fun deleteOne(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM post_likes WHERE post_id = :postId",
        nativeQuery = true,
    )
    fun deleteAllForPost(@Param("postId") postId: Long): Int

    fun existsByUserIdAndPostId(userId: Long, postId: Long): Boolean
    fun countByPostId(postId: Long): Long
    fun findByPostIdOrderByCreatedAtDesc(postId: Long, pageable: Pageable): List<Like>
    fun findByUserIdAndPostIdIn(userId: Long, postIds: Collection<Long>): List<Like>
}
