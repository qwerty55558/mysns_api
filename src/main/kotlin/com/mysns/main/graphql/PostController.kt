package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.model.CreatePostInput
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.UpdatePostInput
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.stub.InMemoryBookmarkStore
import com.mysns.main.graphql.stub.InMemoryCommentStore
import com.mysns.main.graphql.stub.InMemoryLikeStore
import com.mysns.main.graphql.stub.InMemoryPostStore
import com.mysns.main.graphql.stub.InMemoryUserStore
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class PostController(
    private val postStore: InMemoryPostStore,
    private val userStore: InMemoryUserStore,
    private val likeStore: InMemoryLikeStore,
    private val bookmarkStore: InMemoryBookmarkStore,
    private val commentStore: InMemoryCommentStore,
) {

    @QueryMapping
    fun post(@Argument id: String): Post? = postStore.findById(id.toLong())

    @QueryMapping
    fun feed(@Argument limit: Int, @Argument offset: Int): List<Post> =
        postStore.feed(limit, offset)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun bookmarks(@Argument limit: Int, @Argument offset: Int): List<Post> {
        val current = requireCurrentUser()
        val postIds = bookmarkStore.bookmarksOf(current.userId, limit, offset)
        return postStore.findAllById(postIds)
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun createPost(@Argument input: CreatePostInput): Post {
        val current = requireCurrentUser()
        return postStore.create(
            authorId = current.userId,
            content = input.content,
            imageUrls = input.imageUrls.orEmpty(),
            tag = input.tag,
        )
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun updatePost(@Argument id: String, @Argument input: UpdatePostInput): Post {
        val current = requireCurrentUser()
        val existing = postStore.findById(id.toLong())
            ?: throw IllegalArgumentException("post not found: $id")
        if (existing.authorId != current.userId) {
            throw AccessDeniedException("not the author of this post")
        }
        return postStore.update(id.toLong(), input.content, input.tag)
            ?: throw IllegalStateException("update failed")
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun deletePost(@Argument id: String): Boolean {
        val current = requireCurrentUser()
        val post = postStore.findById(id.toLong()) ?: return false
        if (post.authorId != current.userId) {
            throw AccessDeniedException("not the author of this post")
        }
        return postStore.delete(id.toLong())
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun likePost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val post = postStore.findById(postId.toLong())
            ?: throw IllegalArgumentException("post not found: $postId")
        likeStore.like(current.userId, post.id)
        return post
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unlikePost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val post = postStore.findById(postId.toLong())
            ?: throw IllegalArgumentException("post not found: $postId")
        likeStore.unlike(current.userId, post.id)
        return post
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun bookmarkPost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val post = postStore.findById(postId.toLong())
            ?: throw IllegalArgumentException("post not found: $postId")
        bookmarkStore.bookmark(current.userId, post.id)
        return post
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unbookmarkPost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val post = postStore.findById(postId.toLong())
            ?: throw IllegalArgumentException("post not found: $postId")
        bookmarkStore.unbookmark(current.userId, post.id)
        return post
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun sharePost(@Argument postId: String): Post {
        requireCurrentUser()
        return postStore.incrementShareCount(postId.toLong())
            ?: throw IllegalArgumentException("post not found: $postId")
    }

    @BatchMapping(typeName = "Post", field = "author")
    fun author(posts: List<Post>): Map<Post, User> {
        val authorIds = posts.map { it.authorId }.toSet()
        val byId = userStore.findAllById(authorIds).associateBy { it.id }
        return posts.associateWith { byId[it.authorId] ?: error("missing author ${it.authorId}") }
    }

    @SchemaMapping(typeName = "Post", field = "likeCount")
    fun likeCount(post: Post): Int = likeStore.countFor(post.id)

    @SchemaMapping(typeName = "Post", field = "commentCount")
    fun commentCount(post: Post): Int = commentStore.countByPost(post.id)

    @SchemaMapping(typeName = "Post", field = "shareCount")
    fun shareCount(post: Post): Int = post.shareCount

    @SchemaMapping(typeName = "Post", field = "viewerHasLiked")
    fun viewerHasLiked(post: Post): Boolean {
        val current = currentUser() ?: return false
        return likeStore.isLikedBy(current.userId, post.id)
    }

    @SchemaMapping(typeName = "Post", field = "viewerHasBookmarked")
    fun viewerHasBookmarked(post: Post): Boolean {
        val current = currentUser() ?: return false
        return bookmarkStore.isBookmarkedBy(current.userId, post.id)
    }

    @SchemaMapping(typeName = "Post", field = "comments")
    fun comments(post: Post, @Argument limit: Int, @Argument offset: Int): List<Comment> =
        commentStore.listByPost(post.id, limit, offset)

    @SchemaMapping(typeName = "Post", field = "likedBy")
    fun likedBy(post: Post, @Argument limit: Int, @Argument offset: Int): List<User> {
        val userIds = likeStore.likersOf(post.id, limit, offset)
        return userStore.findAllById(userIds)
    }
}
