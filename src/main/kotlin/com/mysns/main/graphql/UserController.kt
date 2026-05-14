package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.stub.InMemoryBookmarkStore
import com.mysns.main.graphql.stub.InMemoryFollowStore
import com.mysns.main.graphql.stub.InMemoryPostStore
import com.mysns.main.graphql.stub.InMemoryUserStore
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class UserController(
    private val userStore: InMemoryUserStore,
    private val postStore: InMemoryPostStore,
    private val bookmarkStore: InMemoryBookmarkStore,
    private val followStore: InMemoryFollowStore,
) {

    @QueryMapping
    fun me(): User? {
        val current = currentUser() ?: return null
        return userStore.findById(current.userId)
    }

    @QueryMapping
    fun user(@Argument id: String): User? = userStore.findById(id.toLong())

    @QueryMapping
    fun userByUsername(@Argument username: String): User? =
        userStore.findByUsername(username)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun followUser(@Argument id: String): User {
        val current = requireCurrentUser()
        val targetId = id.toLong()
        if (current.userId == targetId) {
            throw AccessDeniedException("cannot follow yourself")
        }
        val target = userStore.findById(targetId)
            ?: throw IllegalArgumentException("user not found: $id")
        followStore.follow(current.userId, targetId)
        return target
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unfollowUser(@Argument id: String): User {
        val current = requireCurrentUser()
        val targetId = id.toLong()
        val target = userStore.findById(targetId)
            ?: throw IllegalArgumentException("user not found: $id")
        followStore.unfollow(current.userId, targetId)
        return target
    }

    @SchemaMapping(typeName = "User", field = "posts")
    fun posts(user: User, @Argument limit: Int, @Argument offset: Int): List<Post> =
        postStore.findByAuthor(user.id, limit, offset)

    @SchemaMapping(typeName = "User", field = "postCount")
    fun postCount(user: User): Int = postStore.countByAuthor(user.id)

    @SchemaMapping(typeName = "User", field = "followerCount")
    fun followerCount(user: User): Int = followStore.followerCount(user.id)

    @SchemaMapping(typeName = "User", field = "followingCount")
    fun followingCount(user: User): Int = followStore.followingCount(user.id)

    @SchemaMapping(typeName = "User", field = "viewerIsFollowing")
    fun viewerIsFollowing(user: User): Boolean {
        val current = currentUser() ?: return false
        if (current.userId == user.id) return false
        return followStore.isFollowing(current.userId, user.id)
    }

    @SchemaMapping(typeName = "User", field = "bookmarkedPosts")
    fun bookmarkedPosts(user: User, @Argument limit: Int, @Argument offset: Int): List<Post> {
        val current = requireCurrentUser()
        if (current.userId != user.id) {
            throw AccessDeniedException("can only view your own bookmarks")
        }
        val postIds = bookmarkStore.bookmarksOf(user.id, limit, offset)
        return postStore.findAllById(postIds)
    }
}
