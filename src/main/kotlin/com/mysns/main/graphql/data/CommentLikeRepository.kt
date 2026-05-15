package com.mysns.main.graphql.data

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CommentLikeRepository : JpaRepository<CommentLike, CommentLikeId> {
    @Modifying
    @Query(
        value = "INSERT INTO comment_likes (user_id, comment_id, created_at) " +
            "VALUES (:userId, :commentId, NOW()) " +
            "ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("commentId") commentId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM comment_likes WHERE user_id = :userId AND comment_id = :commentId",
        nativeQuery = true,
    )
    fun deleteOne(@Param("userId") userId: Long, @Param("commentId") commentId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM comment_likes WHERE comment_id = :commentId",
        nativeQuery = true,
    )
    fun deleteAllForComment(@Param("commentId") commentId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM comment_likes WHERE comment_id IN (:commentIds)",
        nativeQuery = true,
    )
    fun deleteAllForComments(@Param("commentIds") commentIds: Collection<Long>): Int

    fun existsByUserIdAndCommentId(userId: Long, commentId: Long): Boolean
    fun findByUserIdAndCommentIdIn(userId: Long, commentIds: Collection<Long>): List<CommentLike>
}
