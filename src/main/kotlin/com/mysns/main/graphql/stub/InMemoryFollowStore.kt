package com.mysns.main.graphql.stub

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryFollowStore {

    // followerId -> set of userIds they follow
    private val following: MutableMap<Long, MutableSet<Long>> = ConcurrentHashMap()

    fun follow(followerId: Long, followeeId: Long): Boolean =
        following.computeIfAbsent(followerId) { ConcurrentHashMap.newKeySet() }.add(followeeId)

    fun unfollow(followerId: Long, followeeId: Long): Boolean =
        following[followerId]?.remove(followeeId) ?: false

    fun isFollowing(followerId: Long, followeeId: Long): Boolean =
        following[followerId]?.contains(followeeId) == true

    fun followingCount(userId: Long): Int = following[userId]?.size ?: 0

    fun followerCount(userId: Long): Int =
        following.values.count { it.contains(userId) }
}
