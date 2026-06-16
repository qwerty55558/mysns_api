package com.mysns.main.graphql

import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.CommentStore
import com.mysns.main.graphql.data.Notification
import com.mysns.main.graphql.data.NotificationStore
import com.mysns.main.graphql.data.NotificationType
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.SplitBill
import com.mysns.main.graphql.data.SplitBillStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class NotificationController(
    private val notificationStore: NotificationStore,
    private val userStore: UserStore,
    private val postStore: PostStore,
    private val commentStore: CommentStore,
    private val splitBillStore: SplitBillStore,
) {

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun notifications(@Argument limit: Int, @Argument offset: Int): List<Notification> =
        notificationStore.list(requireCurrentUser().userId, limit, offset)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun unreadNotificationCount(): Int =
        notificationStore.unreadCount(requireCurrentUser().userId)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun markNotificationRead(@Argument id: String): Boolean =
        notificationStore.markRead(requireCurrentUser().userId, id.toLong())

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun markAllNotificationsRead(): Boolean {
        notificationStore.markAllRead(requireCurrentUser().userId)
        return true
    }

    @BatchMapping(typeName = "Notification", field = "actor")
    fun actor(ns: List<Notification>): Map<Notification, User> {
        val actorIds = ns.map { it.actorId }.toSet()
        val byId = userStore.findAllById(actorIds).associateBy { it.id }
        return ns.associateWith { byId[it.actorId] ?: error("missing actor ${it.actorId}") }
    }

    @BatchMapping(typeName = "Notification", field = "post")
    fun post(ns: List<Notification>): Map<Notification, Post> {
        val postLikeNotifications = ns.filter { it.type == NotificationType.POST_LIKE }
        if (postLikeNotifications.isEmpty()) return emptyMap()
        val ids = postLikeNotifications.mapNotNull { it.entityId }.toSet()
        if (ids.isEmpty()) return emptyMap()
        val byId = postStore.findAllById(ids).associateBy { it.id }
        val result = HashMap<Notification, Post>()
        for (n in postLikeNotifications) {
            val postId = n.entityId ?: continue
            byId[postId]?.let { result[n] = it }
        }
        return result
    }

    @BatchMapping(typeName = "Notification", field = "comment")
    fun comment(ns: List<Notification>): Map<Notification, Comment> {
        val commentNotifications = ns.filter {
            it.type == NotificationType.COMMENT || it.type == NotificationType.COMMENT_LIKE
        }
        if (commentNotifications.isEmpty()) return emptyMap()
        val ids = commentNotifications.mapNotNull { it.entityId }.toSet()
        if (ids.isEmpty()) return emptyMap()
        val byId = ids.mapNotNull { commentStore.findById(it) }.associateBy { it.id }
        val result = HashMap<Notification, Comment>()
        for (n in commentNotifications) {
            val commentId = n.entityId ?: continue
            byId[commentId]?.let { result[n] = it }
        }
        return result
    }

    @BatchMapping(typeName = "Notification", field = "splitBill")
    fun splitBill(ns: List<Notification>): Map<Notification, SplitBill> {
        val splitNotifications = ns.filter { it.type == NotificationType.SPLIT_REQUEST }
        if (splitNotifications.isEmpty()) return emptyMap()
        val ids = splitNotifications.mapNotNull { it.entityId }.toSet()
        if (ids.isEmpty()) return emptyMap()
        val byId = splitBillStore.findBills(ids).associateBy { it.id }
        val result = HashMap<Notification, SplitBill>()
        for (n in splitNotifications) {
            val billId = n.entityId ?: continue
            byId[billId]?.let { result[n] = it }
        }
        return result
    }
}
