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
        seed(1L, "Hello GraphQL", base, imageUrls = listOf("https://picsum.photos/seed/post1/600/600"))
        seed(1L, "Schema-first is the way", base.plusHours(1))
        seed(2L, "Bob here", base.plusHours(2), imageUrls = listOf("https://picsum.photos/seed/post3/600/600"))
        seed(1L, "Third post by alice", base.plusHours(3), imageUrls = listOf(
            "https://picsum.photos/seed/post4a/600/600",
            "https://picsum.photos/seed/post4b/600/600",
        ))
        seed(3L, "lurking out", base.plusHours(4))
        seed(2L, "Bob's second", base.plusHours(5),
            imageUrls = listOf("https://picsum.photos/seed/post6/600/600"),
            tag = "Payflow")
    }

    private fun seed(
        authorId: Long,
        content: String,
        createdAt: OffsetDateTime,
        imageUrls: List<String> = emptyList(),
        tag: String? = null,
    ): Post {
        val id = nextId.getAndIncrement()
        val post = Post(
            id = id,
            content = content,
            authorId = authorId,
            createdAt = createdAt,
            imageUrls = imageUrls,
            tag = tag,
        )
        posts[id] = post
        return post
    }

    fun findById(id: Long): Post? = posts[id]

    fun findAllById(ids: Collection<Long>): List<Post> = ids.mapNotNull { posts[it] }

    fun findByAuthor(authorId: Long, limit: Int, offset: Int): List<Post> =
        posts.values
            .asSequence()
            .filter { it.authorId == authorId }
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
            .toList()

    fun countByAuthor(authorId: Long): Int =
        posts.values.count { it.authorId == authorId }

    fun feed(limit: Int, offset: Int): List<Post> =
        posts.values
            .asSequence()
            .sortedByDescending { it.createdAt }
            .drop(offset)
            .take(limit)
            .toList()

    fun create(authorId: Long, content: String, imageUrls: List<String>, tag: String?): Post {
        val id = nextId.getAndIncrement()
        val post = Post(
            id = id,
            content = content,
            authorId = authorId,
            createdAt = OffsetDateTime.now(),
            imageUrls = imageUrls,
            tag = tag,
        )
        posts[id] = post
        return post
    }

    fun update(id: Long, content: String?, tag: String?): Post? {
        val existing = posts[id] ?: return null
        val updated = existing.copy(
            content = content ?: existing.content,
            tag = tag ?: existing.tag,
            updatedAt = OffsetDateTime.now(),
        )
        posts[id] = updated
        return updated
    }

    fun delete(id: Long): Boolean = posts.remove(id) != null

    fun incrementShareCount(id: Long): Post? {
        val existing = posts[id] ?: return null
        val updated = existing.copy(shareCount = existing.shareCount + 1)
        posts[id] = updated
        return updated
    }
}
