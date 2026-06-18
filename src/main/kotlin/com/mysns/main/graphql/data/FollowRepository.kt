package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FollowRepository : JpaRepository<Follow, FollowId> {
    @Modifying
    @Query(
        value = "INSERT INTO follows (follower_id, followee_id, created_at) " +
            "VALUES (:followerId, :followeeId, NOW()) " +
            "ON CONFLICT DO NOTHING",
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("followerId") followerId: Long, @Param("followeeId") followeeId: Long): Int

    @Modifying
    @Query(
        value = "DELETE FROM follows WHERE follower_id = :followerId AND followee_id = :followeeId",
        nativeQuery = true,
    )
    fun deleteOne(@Param("followerId") followerId: Long, @Param("followeeId") followeeId: Long): Int

    fun existsByFollowerIdAndFolloweeId(followerId: Long, followeeId: Long): Boolean
    fun findByFollowerIdAndFolloweeIdIn(followerId: Long, followeeIds: Collection<Long>): List<Follow>

    /** 내가 팔로우하는 사용자 목록 (최신순) — DM 수신자 picker / User.following 용 */
    fun findByFollowerIdOrderByCreatedAtDesc(followerId: Long, pageable: Pageable): List<Follow>

    /** 나를 팔로우하는 사용자 목록 (최신순) — User.followers 용 */
    fun findByFolloweeIdOrderByCreatedAtDesc(followeeId: Long, pageable: Pageable): List<Follow>

    /**
     * 탈퇴 보정: uid가 팔로우하는(followee인) 유저들의 followerCount -1.
     * 0 이하로 내려가지 않도록 WHERE > 0 가드.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE User u SET u.followerCount = u.followerCount - 1 " +
            "WHERE u.id IN (SELECT f.followeeId FROM Follow f WHERE f.followerId = :uid) " +
            "AND u.followerCount > 0",
    )
    fun decrementFollowerCountForFollowees(@Param("uid") uid: Long): Int

    /**
     * 탈퇴 보정: uid를 팔로우하는(follower인) 유저들의 followingCount -1.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE User u SET u.followingCount = u.followingCount - 1 " +
            "WHERE u.id IN (SELECT f.followerId FROM Follow f WHERE f.followeeId = :uid) " +
            "AND u.followingCount > 0",
    )
    fun decrementFollowingCountForFollowers(@Param("uid") uid: Long): Int
}
