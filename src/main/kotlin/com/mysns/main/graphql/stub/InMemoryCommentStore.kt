package com.mysns.main.graphql.stub

import com.mysns.main.graphql.model.Comment
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Component
class InMemoryCommentStore {

    private val comments: MutableMap<Long, Comment> = ConcurrentHashMap()
    private val nextId = AtomicLong(1)

    fun findById(id: Long): Comment? = comments[id]

    fun listByPost(postId: Long, limit: Int, offset: Int): List<Comment> =
        comments.values
            .asSequence()
            .filter { it.postId == postId }
            .sortedBy { it.createdAt }
            .drop(offset)
            .take(limit)
            .toList()

    fun countByPost(postId: Long): Int =
        comments.values.count { it.postId == postId }

    fun add(authorId: Long, postId: Long, content: String): Comment {
        val id = nextId.getAndIncrement()
        val comment = Comment(
            id = id,
            content = content,
            authorId = authorId,
            postId = postId,
            createdAt = OffsetDateTime.now(),
        )
        comments[id] = comment
        return comment
    }

    fun update(id: Long, content: String): Comment? {
        val existing = comments[id] ?: return null
        val updated = existing.copy(content = content, updatedAt = OffsetDateTime.now())
        comments[id] = updated
        return updated
    }

    fun delete(id: Long): Boolean = comments.remove(id) != null
}
