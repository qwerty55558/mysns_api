package com.mysns.main.graphql.model

import com.mysns.main.graphql.data.ThemePreset
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize
import java.time.OffsetDateTime

@Entity
@Table(
    name = "posts",
    indexes = [
        Index(name = "idx_posts_author_created", columnList = "author_id, created_at"),
        Index(name = "idx_posts_created", columnList = "created_at"),
    ],
)
class Post(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, columnDefinition = "text")
    var content: String,

    @Column(name = "author_id", nullable = false)
    val authorId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime,

    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "post_image_urls",
        joinColumns = [JoinColumn(name = "post_id")],
    )
    @Column(name = "image_url", nullable = false, length = 512)
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    val imageUrls: MutableList<String> = mutableListOf(),

    @Column(length = 64)
    var tag: String? = null,

    @Column(length = 128)
    var item: String? = null,

    @Column
    var amount: Int? = null,

    @Embedded
    var place: Place? = null,

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    var commentCount: Int = 0,

    @Column(name = "share_count", nullable = false)
    var shareCount: Int = 0,

    /** 게시글 테마 (구독자 전용). null 이면 기본 테마. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var theme: ThemePreset? = null,
)
