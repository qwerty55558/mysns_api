package com.mysns.main.config

import com.mysns.main.graphql.data.SubscriptionPlan
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mysns.subscription")
data class SubscriptionProperties(
    val monthly: Plan = Plan(price = 3800, months = 1),
    val yearly: Plan = Plan(price = 38000, months = 12),
    /** 자동 갱신 스케줄러 크론 표현식. */
    val renewCron: String = "0 0 * * * *",
) {
    data class Plan(val price: Int, val months: Int)

    fun planOf(plan: SubscriptionPlan): Plan = when (plan) {
        SubscriptionPlan.MONTHLY -> monthly
        SubscriptionPlan.YEARLY -> yearly
    }
}
