package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.notification.NotificationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class CommentStore(
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val notificationRepository: NotificationRepository,
    private val postRepository: PostRepository,
    private val events: ApplicationEventPublisher,
) {
    fun findById(id: Long): Comment? = commentRepository.findById(id).orElse(null)

    fun listByPost(postId: Long, limit: Int, offset: Int): List<Comment> =
        commentRepository.findByPostIdOrderByCreatedAtAsc(
            postId,
            PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1)),
        )

    fun countByPost(postId: Long): Int = commentRepository.countByPostId(postId).toInt()

    fun latestByPostIds(postIds: Collection<Long>): Map<Long, Comment> {
        if (postIds.isEmpty()) return emptyMap()
        return commentRepository.findLatestByPostIdIn(postIds).associateBy { it.postId }
    }

    @Transactional
    fun add(authorId: Long, postId: Long, content: String): Comment {
        val saved = commentRepository.save(
            Comment(
                content = content,
                authorId = authorId,
                postId = postId,
                createdAt = OffsetDateTime.now(),
            )
        )
        postRepository.incrementCommentCount(postId)
        val postAuthor = postRepository.findAuthorId(postId)
        if (postAuthor != null) {
            events.publishEvent(
                NotificationEvent(
                    recipientId = postAuthor,
                    actorId = authorId,
                    type = NotificationType.COMMENT,
                    entityId = saved.id,
                )
            )
        }
        return saved
    }

    @Transactional
    fun update(id: Long, content: String): Comment? {
        val existing = commentRepository.findById(id).orElse(null) ?: return null
        existing.content = content
        existing.updatedAt = OffsetDateTime.now()
        return existing
    }

    @Transactional
    fun delete(id: Long): Boolean {
        val existing = commentRepository.findById(id).orElse(null) ?: return false
        commentLikeRepository.deleteAllForComment(id)
        // 이 댓글을 가리키는 고아 알림 정리 (COMMENT / COMMENT_LIKE)
        notificationRepository.deleteByTypesAndEntityIds(
            listOf(NotificationType.COMMENT.name, NotificationType.COMMENT_LIKE.name),
            listOf(id),
        )
        commentRepository.delete(existing)
        postRepository.decrementCommentCount(existing.postId)
        return true
    }
}
