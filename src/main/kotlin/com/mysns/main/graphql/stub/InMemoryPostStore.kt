package com.mysns.main.graphql.stub

import com.mysns.main.graphql.model.Post
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class InMemoryPostStore {

    private val posts: MutableMap<Long, Post> = ConcurrentHashMap()
    private val nextId = AtomicLong(1)

    init {
        val base = OffsetDateTime.parse("2026-02-01T00:00:00Z")
        listOf(
            seed(1L, "Hello GraphQL", base),
            seed(1L, "Schema-first is the way", base.plusHours(1)),
            seed(2L, "Bob here", base.plusHours(2)),
            seed(1L, "Third post by alice", base.plusHours(3)),
            seed(3L, "lurking out", base.plusHours(4)),
            seed(2L, "Bob's second", base.plusHours(5)),
        )
    }

    private fun seed(authorId: Long, content: String, createdAt: OffsetDateTime): Post {
        val id = nextId.getAndIncrement()
        val post = Post(id, content, authorId, createdAt)
        posts[id] = post
        return post
    }

    fun findById(id: Long): Post? = posts[id]

    fun findByAuthor(authorId: Long, limit: Int, offset: Int): List<Post> =
        posts.values
            .asSequence()
            .filter { it.authorId == authorId }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
            .toList()

    fun feed(limit: Int, offset: Int): List<Post> =
        posts.values
            .asSequence()
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
            .toList()

    fun create(authorId: Long, content: String): Post {
        val id = nextId.getAndIncrement()
        val post = Post(id, content, authorId, OffsetDateTime.now())
        posts[id] = post
        return post
    }

    fun delete(id: Long): Boolean = posts.remove(id) != null
}
