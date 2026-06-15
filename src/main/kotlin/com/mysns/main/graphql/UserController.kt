package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.BookmarkStore
import com.mysns.main.graphql.data.FollowRequest
import com.mysns.main.graphql.data.FollowRequestStore
import com.mysns.main.graphql.data.FollowStore
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.UpdateMeInput
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
    private val followRequestStore: FollowRequestStore,
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

    @QueryMapping
    fun searchUsers(
        @Argument query: String,
        @Argument limit: Int,
        @Argument offset: Int,
    ): List<User> = userStore.search(query, limit, offset)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun incomingFollowRequests(
        @Argument limit: Int,
        @Argument offset: Int,
    ): List<FollowRequest> {
        val current = requireCurrentUser()
        return followRequestStore.incoming(current.userId, limit, offset)
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun outgoingFollowRequests(
        @Argument limit: Int,
        @Argument offset: Int,
    ): List<FollowRequest> {
        val current = requireCurrentUser()
        return followRequestStore.outgoing(current.userId, limit, offset)
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun incomingFollowRequestCount(): Int {
        val current = requireCurrentUser()
        return followRequestStore.incomingCount(current.userId)
    }

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

        if (target.privateAccount && !followStore.isFollowing(current.userId, targetId)) {
            // 비공개 계정 — 즉시 follow 대신 request 생성 (이미 있으면 idempotent)
            followRequestStore.create(current.userId, targetId)
        } else {
            followStore.follow(current.userId, targetId)
        }
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

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun cancelFollowRequest(@Argument id: String): Boolean {
        val current = requireCurrentUser()
        val request = followRequestStore.findById(id.toLong()) ?: return false
        if (request.requesterId != current.userId) {
            throw AccessDeniedException("only the requester can cancel this request")
        }
        followRequestStore.delete(request)
        return true
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun acceptFollowRequest(@Argument id: String): User {
        val current = requireCurrentUser()
        val request = followRequestStore.findById(id.toLong())
            ?: throw IllegalArgumentException("follow request not found: $id")
        if (request.targetId != current.userId) {
            throw AccessDeniedException("only the target can accept this request")
        }
        followStore.acceptRequest(request.requesterId, request.targetId)
        followRequestStore.delete(request)
        return userStore.findById(request.requesterId)!!
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun rejectFollowRequest(@Argument id: String): Boolean {
        val current = requireCurrentUser()
        val request = followRequestStore.findById(id.toLong()) ?: return false
        if (request.targetId != current.userId) {
            throw AccessDeniedException("only the target can reject this request")
        }
        followRequestStore.delete(request)
        return true
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun updateMe(@Argument input: UpdateMeInput): User {
        val current = requireCurrentUser()
        return userStore.update(
            userId = current.userId,
            displayName = input.displayName?.trim()?.takeIf { it.isNotEmpty() },
            bio = input.bio,
            privateAccount = input.privateAccount,
        )
    }

    @SchemaMapping(typeName = "User", field = "posts")
    fun posts(user: User, @Argument limit: Int, @Argument offset: Int): List<Post> {
        // 비공개 계정의 게시물은 본인 + 팔로워에게만.
        if (user.privateAccount) {
            val viewer = currentUser()
            val visible = viewer != null &&
                (viewer.userId == user.id || followStore.isFollowing(viewer.userId, user.id))
            if (!visible) return emptyList()
        }
        return postStore.findByAuthor(user.id, limit, offset)
    }

    @SchemaMapping(typeName = "User", field = "following")
    fun following(user: User, @Argument limit: Int, @Argument offset: Int): List<User> {
        // 내가 팔로우하는 사용자 목록 (DM 수신자 picker / 팔로잉 목록). follow 최신순 유지.
        val ids = followStore.followeeIdsOf(user.id, limit, offset)
        if (ids.isEmpty()) return emptyList()
        val byId = userStore.findAllById(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    @SchemaMapping(typeName = "User", field = "followers")
    fun followers(user: User, @Argument limit: Int, @Argument offset: Int): List<User> {
        // 나를 팔로우하는 사용자 목록. follow 최신순 유지.
        val ids = followStore.followerIdsOf(user.id, limit, offset)
        if (ids.isEmpty()) return emptyList()
        val byId = userStore.findAllById(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    @BatchMapping(typeName = "User", field = "viewerIsFollowing")
    fun viewerIsFollowing(users: List<User>): Map<User, Boolean> {
        val current = currentUser() ?: return users.associateWith { false }
        val targetIds = users.mapNotNull { if (it.id == current.userId) null else it.id }
        val followed = followStore.followedUserIdsFor(current.userId, targetIds)
        return users.associateWith { it.id != current.userId && it.id in followed }
    }

    @BatchMapping(typeName = "User", field = "viewerHasRequestedFollow")
    fun viewerHasRequestedFollow(users: List<User>): Map<User, Boolean> {
        val current = currentUser() ?: return users.associateWith { false }
        val targetIds = users.mapNotNull { if (it.id == current.userId) null else it.id }
        val pending = followRequestStore.requesterPendingToTargets(current.userId, targetIds)
        return users.associateWith { it.id != current.userId && it.id in pending }
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

    @SchemaMapping(typeName = "FollowRequest", field = "requester")
    fun followRequestRequester(req: FollowRequest): User =
        userStore.findById(req.requesterId)!!

    @SchemaMapping(typeName = "FollowRequest", field = "target")
    fun followRequestTarget(req: FollowRequest): User =
        userStore.findById(req.targetId)!!
}
