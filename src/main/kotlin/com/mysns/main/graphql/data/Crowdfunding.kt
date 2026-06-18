package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

enum class CrowdfundingStatus { OPEN, SUCCEEDED, FAILED }

enum class BackingStatus { ACTIVE, CANCELLED, SETTLED, REFUNDED }

/** 크라우드펀딩 모집 정보. 포스트 1건에 1개만 연결된다. */
@Entity
@Table(
    name = "crowdfundings",
    uniqueConstraints = [UniqueConstraint(name = "uq_crowdfundings_post", columnNames = ["post_id"])],
    indexes = [
        Index(name = "idx_crowdfundings_status_deadline", columnList = "status, deadline"),
        Index(name = "idx_crowdfundings_creator", columnList = "creator_id"),
    ],
)
class Crowdfunding(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false, unique = true)
    val postId: Long,

    @Column(name = "creator_id", nullable = false)
    val creatorId: Long,

    @Column(name = "goal_amount", nullable = false)
    val goalAmount: Int,

    @Column(nullable = false)
    val deadline: OffsetDateTime,

    @Column(name = "current_amount", nullable = false)
    var currentAmount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: CrowdfundingStatus = CrowdfundingStatus.OPEN,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "settled_at")
    var settledAt: OffsetDateTime? = null,
)

/** 크라우드펀딩 후원 1건. 후원자당 한 프로젝트에 1건(ACTIVE 상태 기준). */
@Entity
@Table(
    name = "backings",
    indexes = [
        Index(name = "idx_backings_crowdfunding", columnList = "crowdfunding_id"),
        Index(name = "idx_backings_user", columnList = "user_id"),
    ],
)
class Backing(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "crowdfunding_id", nullable = false)
    val crowdfundingId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val amount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: BackingStatus = BackingStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

/** 크라우드펀딩 체크리스트 항목. 개설자가 진행 상황을 표시하는 용도. */
@Entity
@Table(
    name = "crowdfunding_checklist_items",
    indexes = [Index(name = "idx_checklist_items_crowdfunding", columnList = "crowdfunding_id")],
)
class ChecklistItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "crowdfunding_id", nullable = false)
    val crowdfundingId: Long,

    @Column(nullable = false, length = 200)
    val text: String,

    @Column(nullable = false)
    var done: Boolean = false,

    @Column(nullable = false)
    val position: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
