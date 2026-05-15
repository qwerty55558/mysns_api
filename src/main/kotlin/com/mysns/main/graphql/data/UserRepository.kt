package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun findFirstByOrderByIdAsc(): User?

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.postCount = u.postCount + 1 WHERE u.id = :id")
    fun incrementPostCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.postCount = u.postCount - 1 WHERE u.id = :id AND u.postCount > 0")
    fun decrementPostCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount + 1 WHERE u.id = :id")
    fun incrementFollowerCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followerCount = u.followerCount - 1 WHERE u.id = :id AND u.followerCount > 0")
    fun decrementFollowerCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount + 1 WHERE u.id = :id")
    fun incrementFollowingCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount - 1 WHERE u.id = :id AND u.followingCount > 0")
    fun decrementFollowingCount(@Param("id") id: Long): Int
}
