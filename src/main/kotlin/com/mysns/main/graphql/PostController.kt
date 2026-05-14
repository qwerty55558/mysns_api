package com.mysns.main.graphql

import com.mysns.main.graphql.model.CreatePostInput
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.stub.InMemoryPostStore
import com.mysns.main.graphql.stub.InMemoryUserStore
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class PostController(
    private val postStore: InMemoryPostStore,
    private val userStore: InMemoryUserStore,
) {

    @QueryMapping
    fun post(@Argument id: String): Post? = postStore.findById(id.toLong())

    @QueryMapping
    fun feed(@Argument limit: Int, @Argument offset: Int): List<Post> =
        postStore.feed(limit, offset)

    @MutationMapping
    fun createPost(@Argument input: CreatePostInput): Post {
        // Stage 1 stub: author is hardcoded. Stage 2 will read from SecurityContext.
        val authorId = userStore.first()?.id ?: error("no seed user")
        return postStore.create(authorId, input.content)
    }

    @MutationMapping
    fun deletePost(@Argument id: String): Boolean = postStore.delete(id.toLong())

    @BatchMapping(typeName = "Post", field = "author")
    fun author(posts: List<Post>): Map<Post, User> {
        val authorIds = posts.map { it.authorId }.toSet()
        val byId = userStore.findAllById(authorIds).associateBy { it.id }
        return posts.associateWith { byId[it.authorId] ?: error("missing author ${it.authorId}") }
    }

    @SchemaMapping(typeName = "Post", field = "likeCount")
    fun likeCount(post: Post): Int = 0

    @SchemaMapping(typeName = "Post", field = "commentCount")
    fun commentCount(post: Post): Int = 0
}
