package com.mysns.main.graphql.data

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/** 마감 기한이 지난 크라우드펀딩을 매시 정각에 정산하거나 실패 처리하는 스케줄러. */
@Component
class CrowdfundingDeadlineScheduler(
    private val store: CrowdfundingStore,
) {
    @Scheduled(cron = "\${mysns.crowdfunding.deadline-cron:0 0 * * * *}")
    fun process() {
        store.processDueDeadlines(OffsetDateTime.now())
    }
}
