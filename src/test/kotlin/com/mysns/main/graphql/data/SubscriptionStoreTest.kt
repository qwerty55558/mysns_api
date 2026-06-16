package com.mysns.main.graphql.data

import com.mysns.main.config.SubscriptionProperties
import com.mysns.main.graphql.model.User
import com.mysns.main.graphql.notification.NotificationSseRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(
    WalletStore::class,
    SubscriptionStore::class,
    SubscriptionStoreTest.StoreTestConfig::class,
)
class SubscriptionStoreTest @Autowired constructor(
    private val subscriptionStore: SubscriptionStore,
    private val walletStore: WalletStore,
    private val walletRepository: WalletRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
) {
    /** @TestConfiguration — Spring Boot 슬라이스 테스트에서 추가 빈을 등록하는 올바른 방법. */
    @TestConfiguration
    class StoreTestConfig {
        @Bean
        fun notificationSseRegistry(): NotificationSseRegistry = mock(NotificationSseRegistry::class.java)

        @Bean
        fun subscriptionProperties(): SubscriptionProperties = SubscriptionProperties(
            monthly = SubscriptionProperties.Plan(price = 3800, months = 1),
            yearly = SubscriptionProperties.Plan(price = 38000, months = 12),
            renewCron = "0 0 * * * *",
        )
    }

    private fun newUser(name: String): Long =
        userRepository.save(
            User(username = name, displayName = name, createdAt = OffsetDateTime.now(), passwordHash = "x"),
        ).id

    private fun balanceOf(id: Long) = walletRepository.findByOwnerId(id)!!.balance

    // ─────────────────────────────────────────────
    // subscribe — MONTHLY
    // ─────────────────────────────────────────────

    @Test
    fun `subscribe MONTHLY 는 3800원 차감하고 1개월 뒤 만료되는 ACTIVE 구독을 생성한다`() {
        val u = newUser("u1")
        walletStore.getOrCreate(u)

        val sub = subscriptionStore.subscribe(u, SubscriptionPlan.MONTHLY, ThemePreset.OCEAN)

        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertEquals(SubscriptionPlan.MONTHLY, sub.plan)
        assertEquals(ThemePreset.OCEAN, sub.theme)
        assertEquals(3800, sub.price)
        assertTrue(sub.currentPeriodEnd.isAfter(OffsetDateTime.now()))
        // 대략 1개월 후 (±1일 허용)
        assertTrue(sub.currentPeriodEnd.isBefore(OffsetDateTime.now().plusMonths(1).plusDays(1)))
        assertEquals(WalletStore.INITIAL_BALANCE - 3800, balanceOf(u))
    }

    // ─────────────────────────────────────────────
    // subscribe — YEARLY
    // ─────────────────────────────────────────────

    @Test
    fun `subscribe YEARLY 는 38000원 차감하고 12개월 뒤 만료되는 ACTIVE 구독을 생성한다`() {
        val u = newUser("u2")
        walletStore.getOrCreate(u)

        val sub = subscriptionStore.subscribe(u, SubscriptionPlan.YEARLY, ThemePreset.SUNSET)

        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
        assertEquals(SubscriptionPlan.YEARLY, sub.plan)
        assertEquals(38000, sub.price)
        assertTrue(sub.currentPeriodEnd.isAfter(OffsetDateTime.now().plusMonths(11)))
        assertEquals(WalletStore.INITIAL_BALANCE - 38000, balanceOf(u))
    }

    @Test
    fun `subscribe 잔액이 부족하면 예외`() {
        val u = newUser("u3")
        walletStore.getOrCreate(u)
        // 잔액 전액 소진
        walletStore.withdraw(u, WalletStore.INITIAL_BALANCE, null)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            subscriptionStore.subscribe(u, SubscriptionPlan.MONTHLY, ThemePreset.SUNSET)
        }
        assertEquals("잔액이 부족합니다.", ex.message)
    }

    @Test
    fun `이미 ACTIVE 구독이면 subscribe 실패`() {
        val u = newUser("u4")
        walletStore.getOrCreate(u)
        subscriptionStore.subscribe(u, SubscriptionPlan.MONTHLY, ThemePreset.FOREST)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            subscriptionStore.subscribe(u, SubscriptionPlan.YEARLY, ThemePreset.AURORA)
        }
        assertEquals("이미 구독 중입니다.", ex.message)
    }

    // ─────────────────────────────────────────────
    // changeTheme
    // ─────────────────────────────────────────────

    @Test
    fun `비구독자가 changeTheme 호출하면 예외`() {
        val u = newUser("u5")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            subscriptionStore.changeTheme(u, ThemePreset.MONO)
        }
        assertEquals("활성 구독이 없습니다.", ex.message)
    }

    @Test
    fun `구독자는 changeTheme 성공`() {
        val u = newUser("u6")
        walletStore.getOrCreate(u)
        subscriptionStore.subscribe(u, SubscriptionPlan.MONTHLY, ThemePreset.OCEAN)

        val sub = subscriptionStore.changeTheme(u, ThemePreset.ROSE)

        assertEquals(ThemePreset.ROSE, sub.theme)
    }

    // ─────────────────────────────────────────────
    // cancel
    // ─────────────────────────────────────────────

    @Test
    fun `cancel 은 autoRenew 를 false 로 바꾸고 status 는 ACTIVE 유지`() {
        val u = newUser("u7")
        walletStore.getOrCreate(u)
        subscriptionStore.subscribe(u, SubscriptionPlan.MONTHLY, ThemePreset.OCEAN)

        val sub = subscriptionStore.cancel(u)

        assertFalse(sub.autoRenew)
        assertEquals(SubscriptionStatus.ACTIVE, sub.status)
    }

    // ─────────────────────────────────────────────
    // processDueRenewals
    // ─────────────────────────────────────────────

    @Test
    fun `processDueRenewals MONTHLY autoRenew=true 이고 잔액 충분하면 1개월 기간 연장`() {
        val u = newUser("u8")
        walletStore.getOrCreate(u)
        val past = OffsetDateTime.now().minusHours(1)
        val sub = subscriptionRepository.save(
            Subscription(
                ownerId = u,
                plan = SubscriptionPlan.MONTHLY,
                theme = ThemePreset.OCEAN,
                price = 3800,
                startedAt = past.minusMonths(1),
                currentPeriodEnd = past,
                autoRenew = true,
            )
        )

        subscriptionStore.processDueRenewals(OffsetDateTime.now())

        val updated = subscriptionRepository.findById(sub.id).orElseThrow()
        assertEquals(SubscriptionStatus.ACTIVE, updated.status)
        // 1개월 연장: past + 1개월 이후여야 함
        assertTrue(updated.currentPeriodEnd.isAfter(OffsetDateTime.now()))
        // 갱신 요금(3800) 차감 확인
        assertEquals(WalletStore.INITIAL_BALANCE - 3800, balanceOf(u))
        // price 스냅샷 갱신
        assertEquals(3800, updated.price)
    }

    @Test
    fun `processDueRenewals YEARLY autoRenew=true 이고 잔액 충분하면 12개월 기간 연장`() {
        val u = newUser("u9")
        walletStore.getOrCreate(u)
        val past = OffsetDateTime.now().minusHours(1)
        val sub = subscriptionRepository.save(
            Subscription(
                ownerId = u,
                plan = SubscriptionPlan.YEARLY,
                theme = ThemePreset.SUNSET,
                price = 38000,
                startedAt = past.minusMonths(12),
                currentPeriodEnd = past,
                autoRenew = true,
            )
        )

        subscriptionStore.processDueRenewals(OffsetDateTime.now())

        val updated = subscriptionRepository.findById(sub.id).orElseThrow()
        assertEquals(SubscriptionStatus.ACTIVE, updated.status)
        // 12개월 연장: 11개월 이후여야 함
        assertTrue(updated.currentPeriodEnd.isAfter(OffsetDateTime.now().plusMonths(11)))
        assertEquals(WalletStore.INITIAL_BALANCE - 38000, balanceOf(u))
        assertEquals(38000, updated.price)
    }

    @Test
    fun `processDueRenewals 잔액부족이면 EXPIRED`() {
        val u = newUser("u10")
        walletStore.getOrCreate(u)
        walletStore.withdraw(u, WalletStore.INITIAL_BALANCE, null) // 잔액 0으로

        val past = OffsetDateTime.now().minusHours(1)
        val sub = subscriptionRepository.save(
            Subscription(
                ownerId = u,
                plan = SubscriptionPlan.MONTHLY,
                theme = ThemePreset.FOREST,
                price = 3800,
                startedAt = past.minusMonths(1),
                currentPeriodEnd = past,
                autoRenew = true,
            )
        )

        subscriptionStore.processDueRenewals(OffsetDateTime.now())

        val updated = subscriptionRepository.findById(sub.id).orElseThrow()
        assertEquals(SubscriptionStatus.EXPIRED, updated.status)
    }

    @Test
    fun `processDueRenewals autoRenew=false 이면 EXPIRED`() {
        val u = newUser("u11")
        walletStore.getOrCreate(u)

        val past = OffsetDateTime.now().minusHours(1)
        val sub = subscriptionRepository.save(
            Subscription(
                ownerId = u,
                plan = SubscriptionPlan.YEARLY,
                theme = ThemePreset.AURORA,
                price = 38000,
                startedAt = past.minusMonths(12),
                currentPeriodEnd = past,
                autoRenew = false,
            )
        )

        subscriptionStore.processDueRenewals(OffsetDateTime.now())

        val updated = subscriptionRepository.findById(sub.id).orElseThrow()
        assertEquals(SubscriptionStatus.EXPIRED, updated.status)
    }
}
