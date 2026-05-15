package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime

data class CommentLikeId(
    val userId: Long = 0,
    val commentId: Long = 0,
) : Serializable

@Entity
@Table(name = "comment_likes")
@IdClass(CommentLikeId::class)
class CommentLike(
    @Id
    @Column(name = "user_id")
    val userId: Long,

    @Id
    @Column(name = "comment_id")
    val commentId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
