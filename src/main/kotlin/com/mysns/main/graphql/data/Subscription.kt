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
import java.time.OffsetDateTime

enum class ThemePreset { OCEAN, SUNSET, FOREST, AURORA, MONO, ROSE }
enum class NameEmphasis { NONE, GRADIENT, GLOW, NEON, SPARKLE }
enum class NameFont { DEFAULT, UNBOUNDED, SYNE, BLACK_HAN_SANS, SPACE_GROTESK }

enum class SubscriptionStatus { ACTIVE, CANCELLED, EXPIRED }

enum class SubscriptionPlan { MONTHLY, YEARLY }

/** 프리미엄 멤버십 구독 정보. owner_id 당 1건. */
@Entity
@Table(
    name = "subscriptions",
    indexes = [
        Index(name = "idx_subscriptions_owner", columnList = "owner_id", unique = true),
        Index(name = "idx_subscriptions_status_period", columnList = "status, current_period_end"),
    ],
)
class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "owner_id", nullable = false, unique = true)
    val ownerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var theme: ThemePreset,

    /** 이름(아이디) 강조효과 — 구독자 전용. */
    @Enumerated(EnumType.STRING)
    @Column(name = "name_emphasis", nullable = false, length = 16)
    var nameEmphasis: NameEmphasis = NameEmphasis.NONE,

    /** 이름(아이디) 표시 폰트 — 구독자 전용. */
    @Enumerated(EnumType.STRING)
    @Column(name = "name_font", nullable = false, length = 16)
    var nameFont: NameFont = NameFont.DEFAULT,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var plan: SubscriptionPlan,

    /** 구독 요금 — 구독 시점의 실제 청구액 스냅샷 (원 단위 정수). */
    @Column(nullable = false)
    var price: Int,

    /** 자동 갱신 여부. false이면 기간 만료 시 EXPIRED로 전환. */
    @Column(name = "auto_renew", nullable = false)
    var autoRenew: Boolean = true,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime,

    /** 현재 구독 기간 종료일. 갱신 시 periodDays 만큼 연장. */
    @Column(name = "current_period_end", nullable = false)
    var currentPeriodEnd: OffsetDateTime,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
