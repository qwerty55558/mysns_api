package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Place
import com.mysns.main.graphql.model.Post
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class PostStore(
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val likeRepository: LikeRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
) {
    fun findById(id: Long): Post? = postRepository.findById(id).orElse(null)

    fun findAllById(ids: Collection<Long>): List<Post> =
        if (ids.isEmpty()) emptyList() else postRepository.findAllById(ids)

    fun findByAuthor(authorId: Long, limit: Int, offset: Int): List<Post> =
        postRepository.findByAuthorIdOrderByCreatedAtDesc(authorId, slice(limit, offset))

    fun feed(limit: Int, offset: Int): List<Post> =
        postRepository.findAllByOrderByCreatedAtDesc(slice(limit, offset))

    @Transactional
    fun create(
        authorId: Long,
        content: String,
        imageUrls: List<String>,
        tag: String?,
        item: String?,
        amount: Int?,
        place: Place?,
    ): Post {
        val saved = postRepository.save(
            Post(
                content = content,
                authorId = authorId,
                createdAt = OffsetDateTime.now(),
                imageUrls = imageUrls.toMutableList(),
                tag = tag,
                item = item,
                amount = amount,
                place = place,
            )
        )
        userRepository.incrementPostCount(authorId)
        return saved
    }

    @Transactional
    fun update(
        id: Long,
        content: String?,
        tag: String?,
        item: String?,
        amount: Int?,
        place: Place?,
    ): Post? {
        val existing = postRepository.findById(id).orElse(null) ?: return null
        if (content != null) existing.content = content
        if (tag != null) existing.tag = tag
        if (item != null) existing.item = item
        if (amount != null) existing.amount = amount
        if (place != null) existing.place = place
        existing.updatedAt = OffsetDateTime.now()
        return existing
    }

    @Transactional
    fun delete(id: Long): Boolean {
        val post = postRepository.findById(id).orElse(null) ?: return false
        val commentIds = commentRepository.findByPostId(id).map { it.id }
        if (commentIds.isNotEmpty()) {
            commentLikeRepository.deleteAllForComments(commentIds)
        }
        commentRepository.deleteByPostId(id)
        likeRepository.deleteAllForPost(id)
        bookmarkRepository.deleteAllForPost(id)
        postRepository.delete(post)
        userRepository.decrementPostCount(post.authorId)
        return true
    }

    @Transactional
    fun incrementShareCount(id: Long): Post? {
        val updated = postRepository.incrementShareCount(id)
        if (updated == 0) return null
        return postRepository.findById(id).orElse(null)
    }

    private fun slice(limit: Int, offset: Int) =
        PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
}
