package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.BookmarkStore
import com.mysns.main.graphql.data.FollowStore
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class UserController(
    private val userStore: UserStore,
    private val postStore: PostStore,
    private val bookmarkStore: BookmarkStore,
    private val followStore: FollowStore,
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
        userStore.findById(targetId)
            ?: throw IllegalArgumentException("user not found: $id")
        followStore.follow(current.userId, targetId)
        return userStore.findById(targetId)!!
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unfollowUser(@Argument id: String): User {
        val current = requireCurrentUser()
        val targetId = id.toLong()
        userStore.findById(targetId)
            ?: throw IllegalArgumentException("user not found: $id")
        followStore.unfollow(current.userId, targetId)
        return userStore.findById(targetId)!!
    }

    @SchemaMapping(typeName = "User", field = "posts")
    fun posts(user: User, @Argument limit: Int, @Argument offset: Int): List<Post> =
        postStore.findByAuthor(user.id, limit, offset)

    @BatchMapping(typeName = "User", field = "viewerIsFollowing")
    fun viewerIsFollowing(users: List<User>): Map<User, Boolean> {
        val current = currentUser() ?: return users.associateWith { false }
        val targetIds = users.mapNotNull { if (it.id == current.userId) null else it.id }
        val followed = followStore.followedUserIdsFor(current.userId, targetIds)
        return users.associateWith { it.id != current.userId && it.id in followed }
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
