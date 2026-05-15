package com.mysns.main.graphql.data

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class CommentLikeStore(
    private val commentLikeRepository: CommentLikeRepository,
    private val commentRepository: CommentRepository,
) {
    @Transactional
    fun like(userId: Long, commentId: Long): Boolean {
        val inserted = commentLikeRepository.insertIfAbsent(userId, commentId)
        if (inserted == 0) return false
        commentRepository.incrementLikeCount(commentId)
        return true
    }

    @Transactional
    fun unlike(userId: Long, commentId: Long): Boolean {
        val removed = commentLikeRepository.deleteOne(userId, commentId)
        if (removed == 0) return false
        commentRepository.decrementLikeCount(commentId)
        return true
    }

    fun isLikedBy(userId: Long, commentId: Long): Boolean =
        commentLikeRepository.existsByUserIdAndCommentId(userId, commentId)

    fun likedCommentIdsFor(userId: Long, commentIds: Collection<Long>): Set<Long> {
        if (commentIds.isEmpty()) return emptySet()
        return commentLikeRepository.findByUserIdAndCommentIdIn(userId, commentIds).mapTo(HashSet()) { it.commentId }
    }
}
