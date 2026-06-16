package com.mysns.main.graphql.data

import com.mysns.main.config.SubscriptionProperties
import com.mysns.main.graphql.notification.NotificationSseRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class SubscriptionStore(
    private val subscriptionRepository: SubscriptionRepository,
    private val walletStore: WalletStore,
    private val sseRegistry: NotificationSseRegistry,
    private val props: SubscriptionProperties,
) {
    private val log = LoggerFactory.getLogger(SubscriptionStore::class.java)

    /** 내 구독 정보 조회. */
    fun mySubscription(ownerId: Long): Subscription? =
        subscriptionRepository.findByOwnerId(ownerId)

    /**
     * 배치용 — ownerIds 중 ACTIVE 구독만 반환.
     * key: ownerId, value: Subscription.
     */
    fun activeByOwnerIds(ownerIds: Collection<Long>): Map<Long, Subscription> {
        if (ownerIds.isEmpty()) return emptyMap()
        return subscriptionRepository.findAllByOwnerIdIn(ownerIds)
            .filter { it.status == SubscriptionStatus.ACTIVE }
            .associateBy { it.ownerId }
    }

    /**
     * 구독 신청.
     * 이미 ACTIVE이면 실패. EXPIRED/CANCELLED이면 재활성화, 없으면 신규 생성.
     * 결제는 WalletStore.charge 를 통해 지출가능 잔액에서 차감.
     */
    @Transactional
    fun subscribe(ownerId: Long, plan: SubscriptionPlan, theme: ThemePreset): Subscription {
        val existing = subscriptionRepository.findByOwnerId(ownerId)
        require(existing == null || existing.status != SubscriptionStatus.ACTIVE) { "이미 구독 중입니다." }

        val planConfig = props.planOf(plan)

        // 구독 결제
        walletStore.charge(ownerId, planConfig.price, "프리미엄 구독 결제")

        val now = OffsetDateTime.now()
        val periodEnd = now.plusMonths(planConfig.months.toLong())

        return if (existing != null) {
            // 재활성화
            existing.status = SubscriptionStatus.ACTIVE
            existing.plan = plan
            existing.theme = theme
            existing.price = planConfig.price
            existing.autoRenew = true
            existing.currentPeriodEnd = periodEnd
            existing.updatedAt = now
            subscriptionRepository.save(existing)
        } else {
            // 신규 생성
            subscriptionRepository.save(
                Subscription(
                    ownerId = ownerId,
                    plan = plan,
                    theme = theme,
                    price = planConfig.price,
                    startedAt = now,
                    currentPeriodEnd = periodEnd,
                )
            )
        }
    }

    /**
     * 테마 변경 (ACTIVE 구독자만).
     */
    @Transactional
    fun changeTheme(ownerId: Long, theme: ThemePreset): Subscription {
        val subscription = subscriptionRepository.findByOwnerId(ownerId)
        require(subscription != null && subscription.status == SubscriptionStatus.ACTIVE) { "활성 구독이 없습니다." }
        subscription.theme = theme
        subscription.updatedAt = OffsetDateTime.now()
        return subscriptionRepository.save(subscription)
    }

    /**
     * 구독 해지 (자동 갱신 비활성화).
     * status는 ACTIVE 유지 — 기간 만료 시 EXPIRED로 전환됨.
     */
    @Transactional
    fun cancel(ownerId: Long): Subscription {
        val subscription = subscriptionRepository.findByOwnerId(ownerId)
        require(subscription != null && subscription.status == SubscriptionStatus.ACTIVE) { "활성 구독이 없습니다." }
        subscription.autoRenew = false
        subscription.updatedAt = OffsetDateTime.now()
        return subscriptionRepository.save(subscription)
    }

    /**
     * 만료된 구독 자동 갱신/만료 처리.
     * ACTIVE이면서 currentPeriodEnd <= now 인 구독을 조회.
     * - autoRenew=true: 결제 시도 → 성공 시 기간 연장, 실패 시 EXPIRED.
     * - autoRenew=false: 바로 EXPIRED.
     */
    @Transactional
    fun processDueRenewals(now: OffsetDateTime) {
        val due = subscriptionRepository.findByStatusAndCurrentPeriodEndLessThanEqual(
            SubscriptionStatus.ACTIVE,
            now,
        )
        if (due.isEmpty()) return

        val expired = mutableListOf<Long>()
        val renewed = mutableListOf<Long>()

        for (sub in due) {
            if (sub.autoRenew) {
                try {
                    val planConfig = props.planOf(sub.plan)
                    walletStore.charge(sub.ownerId, planConfig.price, "프리미엄 구독 자동 갱신")
                    sub.price = planConfig.price
                    sub.currentPeriodEnd = sub.currentPeriodEnd.plusMonths(planConfig.months.toLong())
                    sub.updatedAt = now
                    subscriptionRepository.save(sub)
                    renewed.add(sub.ownerId)
                    log.info("구독 갱신 성공 — ownerId={}", sub.ownerId)
                } catch (ex: IllegalArgumentException) {
                    log.info("구독 갱신 실패(잔액부족) — ownerId={}", sub.ownerId)
                    sub.status = SubscriptionStatus.EXPIRED
                    sub.updatedAt = now
                    subscriptionRepository.save(sub)
                    expired.add(sub.ownerId)
                } catch (ex: IllegalStateException) {
                    log.warn("구독 갱신 실패(상태오류) — ownerId={}", sub.ownerId, ex)
                    sub.status = SubscriptionStatus.EXPIRED
                    sub.updatedAt = now
                    subscriptionRepository.save(sub)
                    expired.add(sub.ownerId)
                }
            } else {
                sub.status = SubscriptionStatus.EXPIRED
                sub.updatedAt = now
                subscriptionRepository.save(sub)
                expired.add(sub.ownerId)
                log.info("구독 만료(자동갱신 해지) — ownerId={}", sub.ownerId)
            }
        }

        // SSE 실시간 알림
        for (ownerId in expired) {
            sseRegistry.push(ownerId, "subscription", mapOf("status" to "EXPIRED"))
        }
        for (ownerId in renewed) {
            sseRegistry.push(ownerId, "subscription", mapOf("status" to "RENEWED"))
        }
    }
}
