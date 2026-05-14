package com.mysns.main.graphql.stub

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryLikeStore {

    private val likersByPost: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()

    fun like(userId: Long, postId: Long): Boolean =
        likersByPost.computeIfAbsent(postId) { ConcurrentHashMap.newKeySet() }.add(userId)

    fun unlike(userId: Long, postId: Long): Boolean =
        likersByPost[postId]?.remove(userId) ?: false

    fun isLikedBy(userId: Long, postId: Long): Boolean =
        likersByPost[postId]?.contains(userId) == true

    fun countFor(postId: Long): Int = likersByPost[postId]?.size ?: 0

    fun likersOf(postId: Long, limit: Int, offset: Int): List<Long> =
        likersByPost[postId]?.toList()?.drop(offset)?.take(limit) ?: emptyList()
}
