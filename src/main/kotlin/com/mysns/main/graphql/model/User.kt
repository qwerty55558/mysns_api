package com.mysns.main.graphql.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 64)
    val username: String,

    @Column(nullable = false, length = 64)
    val displayName: String,

    @Column(columnDefinition = "text")
    val bio: String? = null,

    @Column(nullable = false)
    val createdAt: OffsetDateTime,

    @Column(length = 512)
    val avatarUrl: String? = null,

    @Column(nullable = false)
    val passwordHash: String,

    @Column(nullable = false)
    var postCount: Int = 0,

    @Column(nullable = false)
    var followerCount: Int = 0,

    @Column(nullable = false)
    var followingCount: Int = 0,
)
