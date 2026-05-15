package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.CommentLikeStore
import com.mysns.main.graphql.data.CommentStore
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.model.CreateCommentInput
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.UpdateCommentInput
import com.mysns.main.graphql.model.User
import jakarta.validation.Valid
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.validation.annotation.Validated

@Controller
@Validated
class CommentController(
    private val commentStore: CommentStore,
    private val commentLikeStore: CommentLikeStore,
    private val postStore: PostStore,
    private val userStore: UserStore,
) {

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun addComment(@Argument @Valid input: CreateCommentInput): Comment {
        val current = requireCurrentUser()
        val postId = input.postId.toLong()
        postStore.findById(postId)
            ?: throw IllegalArgumentException("post not found: ${input.postId}")
        return commentStore.add(current.userId, postId, input.content)
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun updateComment(@Argument id: String, @Argument @Valid input: UpdateCommentInput): Comment {
        val current = requireCurrentUser()
        val existing = commentStore.findById(id.toLong())
            ?: throw IllegalArgumentException("comment not found: $id")
        if (existing.authorId != current.userId) {
            throw AccessDeniedException("not the author of this comment")
        }
        return commentStore.update(id.toLong(), input.content)
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
        val commentId = id.toLong()
        commentStore.findById(commentId)
            ?: throw IllegalArgumentException("comment not found: $id")
        commentLikeStore.like(current.userId, commentId)
        return commentStore.findById(commentId)!!
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unlikeComment(@Argument id: String): Comment {
        val current = requireCurrentUser()
        val commentId = id.toLong()
        commentStore.findById(commentId)
            ?: throw IllegalArgumentException("comment not found: $id")
        commentLikeStore.unlike(current.userId, commentId)
        return commentStore.findById(commentId)!!
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

    @BatchMapping(typeName = "Comment", field = "viewerHasLiked")
    fun viewerHasLiked(comments: List<Comment>): Map<Comment, Boolean> {
        val current = currentUser() ?: return comments.associateWith { false }
        val likedIds = commentLikeStore.likedCommentIdsFor(current.userId, comments.map { it.id })
        return comments.associateWith { it.id in likedIds }
    }
}
