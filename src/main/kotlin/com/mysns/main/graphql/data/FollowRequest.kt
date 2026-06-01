package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

@Entity
@Table(
    name = "follow_requests",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_follow_requests_requester_target", columnNames = ["requester_id", "target_id"]),
    ],
    indexes = [
        Index(name = "idx_follow_requests_target_created", columnList = "target_id,created_at"),
        Index(name = "idx_follow_requests_requester", columnList = "requester_id"),
    ],
)
class FollowRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "requester_id", nullable = false)
    val requesterId: Long,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
