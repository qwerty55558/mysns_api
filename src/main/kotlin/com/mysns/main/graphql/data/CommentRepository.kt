package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Comment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param


interface CommentRepository : JpaRepository<Comment, Long> {

    @Query("SELECT c.authorId FROM Comment c WHERE c.id = :id")
    fun findAuthorId(@Param("id") id: Long): Long?
    fun findByPostIdOrderByCreatedAtAsc(postId: Long, pageable: Pageable): List<Comment>
    fun findByPostId(postId: Long): List<Comment>
    fun deleteByPostId(postId: Long): Long
    fun countByPostId(postId: Long): Long

    @Query(
        "SELECT c FROM Comment c WHERE c.id IN (" +
            "SELECT MAX(c2.id) FROM Comment c2 " +
            "WHERE c2.postId IN :postIds " +
            "GROUP BY c2.postId" +
            ")"
    )
    fun findLatestByPostIdIn(@Param("postIds") postIds: Collection<Long>): List<Comment>

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    fun incrementLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount - 1 WHERE c.id = :id AND c.likeCount > 0")
    fun decrementLikeCount(@Param("id") id: Long): Int
}
