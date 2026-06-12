package com.mysns.main.graphql.data

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class FollowStore(
    private val followRepository: FollowRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun follow(followerId: Long, followeeId: Long): Boolean {
        val inserted = followRepository.insertIfAbsent(followerId, followeeId)
        if (inserted == 0) return false
        userRepository.incrementFollowingCount(followerId)
        userRepository.incrementFollowerCount(followeeId)
        return true
    }

    @Transactional
    fun unfollow(followerId: Long, followeeId: Long): Boolean {
        val removed = followRepository.deleteOne(followerId, followeeId)
        if (removed == 0) return false
        userRepository.decrementFollowingCount(followerId)
        userRepository.decrementFollowerCount(followeeId)
        return true
    }

    fun isFollowing(followerId: Long, followeeId: Long): Boolean =
        followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)

    fun followedUserIdsFor(followerId: Long, followeeIds: Collection<Long>): Set<Long> {
        if (followeeIds.isEmpty()) return emptySet()
        return followRepository.findByFollowerIdAndFolloweeIdIn(followerId, followeeIds).mapTo(HashSet()) { it.followeeId }
    }

    /** 내가 팔로우하는 사용자 id 목록 (최신순, 페이징) — User.following 리졸버 용 */
    fun followeeIdsOf(followerId: Long, limit: Int, offset: Int): List<Long> {
        val page = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(followerId, page).map { it.followeeId }
    }

    /** 나를 팔로우하는 사용자 id 목록 (최신순, 페이징) — User.followers 리졸버 용 */
    fun followerIdsOf(followeeId: Long, limit: Int, offset: Int): List<Long> {
        val page = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
        return followRepository.findByFolloweeIdOrderByCreatedAtDesc(followeeId, page).map { it.followerId }
    }
}
