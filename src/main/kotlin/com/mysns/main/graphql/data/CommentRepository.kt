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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    fun incrementLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount - 1 WHERE c.id = :id AND c.likeCount > 0")
    fun decrementLikeCount(@Param("id") id: Long): Int

    /**
     * 탈퇴 보정: uid가 작성한 댓글이 달린 포스트들의 commentCount를 작성 댓글 수만큼 차감.
     * GROUP BY로 포스트별 댓글 수를 구한 뒤 각각 감산. 0 이하 방지를 위해 GREATEST 사용.
     * H2(MODE=PostgreSQL)와 PostgreSQL 모두에서 동작하는 native SQL.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            UPDATE posts
            SET comment_count = GREATEST(0, comment_count - c.cnt)
            FROM (
                SELECT post_id, COUNT(*) AS cnt
                FROM comments
                WHERE author_id = :uid
                GROUP BY post_id
            ) c
            WHERE posts.id = c.post_id
        """,
        nativeQuery = true,
    )
    fun decrementCommentCountForPostsByAuthor(@Param("uid") uid: Long): Int
}
