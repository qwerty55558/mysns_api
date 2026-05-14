package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.model.AddCommentInput
import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.stub.InMemoryCommentLikeStore
import com.mysns.main.graphql.stub.InMemoryCommentStore
import com.mysns.main.graphql.stub.InMemoryPostStore
import com.mysns.main.graphql.stub.InMemoryUserStore
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class CommentController(
    private val commentStore: InMemoryCommentStore,
    private val commentLikeStore: InMemoryCommentLikeStore,
    private val postStore: InMemoryPostStore,
    private val userStore: InMemoryUserStore,
) {

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun addComment(@Argument input: AddCommentInput): Comment {
        val current = requireCurrentUser()
        val postId = input.postId.toLong()
        postStore.findById(postId)
            ?: throw IllegalArgumentException("post not found: ${input.postId}")
        return commentStore.add(current.userId, postId, input.content)
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun updateComment(@Argument id: String, @Argument content: String): Comment {
        val current = requireCurrentUser()
        val existing = commentStore.findById(id.toLong())
            ?: throw IllegalArgumentException("comment not found: $id")
        if (existing.authorId != current.userId) {
            throw AccessDeniedException("not the author of this comment")
        }
        return commentStore.update(id.toLong(), content)
            ?: throw IllegalStateException("update failed")
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun deleteComment(@Argument id: String): Boolean {
        val current = requireCurrentUser()
        val existing = commentStore.findById(id.toLong()) ?: return false
        if (existing.authorId != current.userId) {
            throw AccessDeniedException("not the author of this comment")
        }
        return commentStore.delete(id.toLong())
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun likeComment(@Argument id: String): Comment {
        val current = requireCurrentUser()
        val comment = commentStore.findById(id.toLong())
            ?: throw IllegalArgumentException("comment not found: $id")
        commentLikeStore.like(current.userId, comment.id)
        return comment
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unlikeComment(@Argument id: String): Comment {
        val current = requireCurrentUser()
        val comment = commentStore.findById(id.toLong())
            ?: throw IllegalArgumentException("comment not found: $id")
        commentLikeStore.unlike(current.userId, comment.id)
        return comment
    }

    @BatchMapping(typeName = "Comment", field = "author")
    fun author(comments: List<Comment>): Map<Comment, User> {
        val authorIds = comments.map { it.authorId }.toSet()
        val byId = userStore.findAllById(authorIds).associateBy { it.id }
        return comments.associateWith { byId[it.authorId] ?: error("missing author ${it.authorId}") }
    }

    @BatchMapping(typeName = "Comment", field = "post")
    fun post(comments: List<Comment>): Map<Comment, Post> {
        val postIds = comments.map { it.postId }.toSet()
        val byId = postStore.findAllById(postIds).associateBy { it.id }
        return comments.associateWith { byId[it.postId] ?: error("missing post ${it.postId}") }
    }

    @SchemaMapping(typeName = "Comment", field = "likeCount")
    fun likeCount(comment: Comment): Int = commentLikeStore.countFor(comment.id)

    @SchemaMapping(typeName = "Comment", field = "viewerHasLiked")
    fun viewerHasLiked(comment: Comment): Boolean {
        val current = currentUser() ?: return false
        return commentLikeStore.isLikedBy(current.userId, comment.id)
    }
}
