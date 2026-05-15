package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime

data class BookmarkId(
    val userId: Long = 0,
    val postId: Long = 0,
) : Serializable

@Entity
@Table(
    name = "bookmarks",
    indexes = [Index(name = "idx_bookmarks_user_created", columnList = "user_id, created_at")],
)
@IdClass(BookmarkId::class)
class Bookmark(
    @Id
    @Column(name = "user_id")
    val userId: Long,

    @Id
    @Column(name = "post_id")
    val postId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
