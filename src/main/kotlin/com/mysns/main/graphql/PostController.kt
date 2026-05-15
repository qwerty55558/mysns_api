package com.mysns.main.graphql

import com.mysns.main.auth.currentUser
import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.BookmarkStore
import com.mysns.main.graphql.data.LikeStore
import com.mysns.main.graphql.data.PostStore
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.model.CreatePostInput
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.UpdatePostInput
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.data.CommentStore
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
    private val postStore: PostStore,
    private val userStore: UserStore,
    private val likeStore: LikeStore,
    private val bookmarkStore: BookmarkStore,
    private val commentStore: CommentStore,
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
            amount = input.amount,
            category = input.category,
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
        return postStore.update(id.toLong(), input.content, input.tag, input.amount, input.category)
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
        val id = postId.toLong()
        postStore.findById(id) ?: throw IllegalArgumentException("post not found: $postId")
        likeStore.like(current.userId, id)
        return postStore.findById(id)!!
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unlikePost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val id = postId.toLong()
        postStore.findById(id) ?: throw IllegalArgumentException("post not found: $postId")
        likeStore.unlike(current.userId, id)
        return postStore.findById(id)!!
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun bookmarkPost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val id = postId.toLong()
        val post = postStore.findById(id)
            ?: throw IllegalArgumentException("post not found: $postId")
        bookmarkStore.bookmark(current.userId, id)
        return post
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun unbookmarkPost(@Argument postId: String): Post {
        val current = requireCurrentUser()
        val id = postId.toLong()
        val post = postStore.findById(id)
            ?: throw IllegalArgumentException("post not found: $postId")
        bookmarkStore.unbookmark(current.userId, id)
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

    @BatchMapping(typeName = "Post", field = "viewerHasLiked")
    fun viewerHasLiked(posts: List<Post>): Map<Post, Boolean> {
        val current = currentUser() ?: return posts.associateWith { false }
        val likedIds = likeStore.likedPostIdsFor(current.userId, posts.map { it.id })
        return posts.associateWith { it.id in likedIds }
    }

    @BatchMapping(typeName = "Post", field = "viewerHasBookmarked")
    fun viewerHasBookmarked(posts: List<Post>): Map<Post, Boolean> {
        val current = currentUser() ?: return posts.associateWith { false }
        val markedIds = bookmarkStore.bookmarkedPostIdsFor(current.userId, posts.map { it.id })
        return posts.associateWith { it.id in markedIds }
    }

    @BatchMapping(typeName = "Post", field = "previewComment")
    fun previewComment(posts: List<Post>): Map<Post, Comment> {
        val byPostId = commentStore.latestByPostIds(posts.map { it.id })
        val result = HashMap<Post, Comment>(byPostId.size)
        for (p in posts) {
            byPostId[p.id]?.let { result[p] = it }
        }
        return result
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
