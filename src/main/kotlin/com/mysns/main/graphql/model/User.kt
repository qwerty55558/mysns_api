package com.mysns.main.graphql.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    var displayName: String,

    @Column(columnDefinition = "text")
    var bio: String? = null,

    @Column(nullable = false)
    val createdAt: OffsetDateTime,

    @Column(length = 512)
    var avatarUrl: String? = null,

    @Column(nullable = false)
    val passwordHash: String,

    @Column(nullable = false)
    var postCount: Int = 0,

    @Column(nullable = false)
    var followerCount: Int = 0,

    @Column(nullable = false)
    var followingCount: Int = 0,

    @Column(name = "private_account", nullable = false)
    var privateAccount: Boolean = false,

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var role: UserRole = UserRole.USER,
)
