package com.mysns.main.graphql.data

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class LikeStore(
    private val likeRepository: LikeRepository,
    private val postRepository: PostRepository,
) {
    @Transactional
    fun like(userId: Long, postId: Long): Boolean {
        val inserted = likeRepository.insertIfAbsent(userId, postId)
        if (inserted == 0) return false
        postRepository.incrementLikeCount(postId)
        return true
    }

    @Transactional
    fun unlike(userId: Long, postId: Long): Boolean {
        val removed = likeRepository.deleteOne(userId, postId)
        if (removed == 0) return false
        postRepository.decrementLikeCount(postId)
        return true
    }

    fun isLikedBy(userId: Long, postId: Long): Boolean =
        likeRepository.existsByUserIdAndPostId(userId, postId)

    fun likedPostIdsFor(userId: Long, postIds: Collection<Long>): Set<Long> {
        if (postIds.isEmpty()) return emptySet()
        return likeRepository.findByUserIdAndPostIdIn(userId, postIds).mapTo(HashSet()) { it.postId }
    }

    fun likersOf(postId: Long, limit: Int, offset: Int): List<Long> =
        likeRepository.findByPostIdOrderByCreatedAtDesc(
            postId,
            PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1)),
        ).map { it.userId }
}
