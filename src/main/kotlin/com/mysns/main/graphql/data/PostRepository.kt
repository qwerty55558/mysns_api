package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Post
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostRepository : JpaRepository<Post, Long> {

    @Query("SELECT p.authorId FROM Post p WHERE p.id = :id")
    fun findAuthorId(@Param("id") id: Long): Long?
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<Post>
    fun findByAuthorIdOrderByCreatedAtDesc(authorId: Long, pageable: Pageable): List<Post>

    @Query(
        """
        SELECT p FROM Post p
        WHERE LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.tag) LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(p.item) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY p.createdAt DESC
        """,
    )
    fun search(@Param("q") q: String, pageable: Pageable): List<Post>

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    fun incrementLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    fun decrementLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :id")
    fun incrementCommentCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount - 1 WHERE p.id = :id AND p.commentCount > 0")
    fun decrementCommentCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.shareCount = p.shareCount + 1 WHERE p.id = :id")
    fun incrementShareCount(@Param("id") id: Long): Int
}
