package com.mysns.main.graphql.stub

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryBookmarkStore {

    private val postsByUser: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()

    fun bookmark(userId: Long, postId: Long): Boolean =
        postsByUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(postId)

    fun unbookmark(userId: Long, postId: Long): Boolean =
        postsByUser[userId]?.remove(postId) ?: false

    fun isBookmarkedBy(userId: Long, postId: Long): Boolean =
        postsByUser[userId]?.contains(postId) == true

    fun bookmarksOf(userId: Long, limit: Int, offset: Int): List<Long> =
        postsByUser[userId]?.toList()?.drop(offset)?.take(limit) ?: emptyList()
}
