package com.mysns.main.graphql.data

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class BookmarkStore(
    private val bookmarkRepository: BookmarkRepository,
) {
    @Transactional
    fun bookmark(userId: Long, postId: Long): Boolean =
        bookmarkRepository.insertIfAbsent(userId, postId) > 0

    @Transactional
    fun unbookmark(userId: Long, postId: Long): Boolean =
        bookmarkRepository.deleteOne(userId, postId) > 0

    fun isBookmarkedBy(userId: Long, postId: Long): Boolean =
        bookmarkRepository.existsByUserIdAndPostId(userId, postId)

    fun bookmarkedPostIdsFor(userId: Long, postIds: Collection<Long>): Set<Long> {
        if (postIds.isEmpty()) return emptySet()
        return bookmarkRepository.findByUserIdAndPostIdIn(userId, postIds).mapTo(HashSet()) { it.postId }
    }

    fun bookmarksOf(userId: Long, limit: Int, offset: Int): List<Long> =
        bookmarkRepository.findByUserIdOrderByCreatedAtDesc(
            userId,
            PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1)),
        ).map { it.postId }
}
