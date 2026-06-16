package com.mysns.main.graphql.data

import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface SubscriptionRepository : JpaRepository<Subscription, Long> {
    fun findByOwnerId(ownerId: Long): Subscription?
    fun findAllByOwnerIdIn(ownerIds: Collection<Long>): List<Subscription>

    /** 자동 갱신 처리 대상 조회: ACTIVE 이고 현재 기간이 만료된 구독들. */
    fun findByStatusAndCurrentPeriodEndLessThanEqual(
        status: SubscriptionStatus,
        ts: OffsetDateTime,
    ): List<Subscription>
}
