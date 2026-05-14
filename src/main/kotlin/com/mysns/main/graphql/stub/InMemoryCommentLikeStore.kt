package com.mysns.main.graphql.stub

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryCommentLikeStore {

    private val likersByComment: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()

    fun like(userId: Long, commentId: Long): Boolean =
        likersByComment.computeIfAbsent(commentId) { ConcurrentHashMap.newKeySet() }.add(userId)

    fun unlike(userId: Long, commentId: Long): Boolean =
        likersByComment[commentId]?.remove(userId) ?: false

    fun isLikedBy(userId: Long, commentId: Long): Boolean =
        likersByComment[commentId]?.contains(userId) == true

    fun countFor(commentId: Long): Int = likersByComment[commentId]?.size ?: 0
}
