package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BookmarkRepository : JpaRepository<Bookmark, BookmarkId> {
    @Modifying
    @Query(
        value = "INSERT INTO bookmarks (user_id, post_id, created_at) " +
            "VALUES (:userId, :postId, NOW()) " +
            "ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM bookmarks WHERE user_id = :userId AND post_id = :postId",
        nativeQuery = true,
    )
    fun deleteOne(@Param("userId") userId: Long, @Param("postId") postId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM bookmarks WHERE post_id = :postId",
        nativeQuery = true,
    )
    fun deleteAllForPost(@Param("postId") postId: Long): Int

    fun existsByUserIdAndPostId(userId: Long, postId: Long): Boolean
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<Bookmark>
    fun findByUserIdAndPostIdIn(userId: Long, postIds: Collection<Long>): List<Bookmark>
}
