package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime

data class FollowId(
    val followerId: Long = 0,
    val followeeId: Long = 0,
) : Serializable

@Entity
@Table(
    name = "follows",
    indexes = [Index(name = "idx_follows_followee", columnList = "followee_id")],
)
@IdClass(FollowId::class)
class Follow(
    @Id
    @Column(name = "follower_id")
    val followerId: Long,

    @Id
    @Column(name = "followee_id")
    val followeeId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
