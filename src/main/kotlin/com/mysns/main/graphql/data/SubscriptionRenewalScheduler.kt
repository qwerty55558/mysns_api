package com.mysns.main.graphql.data

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/** 만료된 구독을 매시 정각에 갱신 또는 만료 처리하는 스케줄러. */
@Component
class SubscriptionRenewalScheduler(
    private val store: SubscriptionStore,
) {
    @Scheduled(cron = "\${mysns.subscription.renew-cron:0 0 * * * *}")
    fun processRenewals() {
        store.processDueRenewals(OffsetDateTime.now())
    }
}
