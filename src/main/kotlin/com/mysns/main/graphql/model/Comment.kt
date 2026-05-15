package com.mysns.main.graphql.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(
    name = "comments",
    indexes = [
        Index(name = "idx_comments_post_created", columnList = "post_id, created_at"),
    ],
)
class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, columnDefinition = "text")
    var content: String,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime,

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime? = null,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,
)
