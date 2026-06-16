package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.SplitParticipantInput
import com.mysns.main.graphql.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class WalletConcurrencyTest @Autowired constructor(
    private val walletStore: WalletStore,
    private val splitBillStore: SplitBillStore,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val billRepository: SplitBillRepository,
    private val participantRepository: SplitParticipantRepository,
) {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun datasource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
        }
    }

    private val seq = AtomicInteger(0)

    private fun newUser(): Long =
        userRepository.save(
            User(
                username = "ct-${seq.incrementAndGet()}-${System.nanoTime()}",
                displayName = "ct",
                createdAt = OffsetDateTime.now(),
                passwordHash = "x",
            ),
        ).id

    /** threads개 스레드를 래치로 동시에 출발시키고 (성공 수, 실패 수)를 반환. */
    private fun runConcurrently(threads: Int, task: (Int) -> Unit): Pair<Int, Int> {
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val success = AtomicInteger(0)
        val failure = AtomicInteger(0)
        repeat(threads) { i ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    task(i)
                    success.incrementAndGet()
                } catch (e: Exception) {
                    failure.incrementAndGet()
                }
            }
        }
        ready.await()
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(60, TimeUnit.SECONDS)
        return success.get() to failure.get()
    }

    @Test
    fun `동시 출금 - 잔액 이상으로 빠지지 않고 정확히 가능한 만큼만 성공한다`() {
        val u = newUser()
        walletStore.getOrCreate(u)
        val (success, failure) = runConcurrently(20) { walletStore.withdraw(u, 100_000, null) }
        assertEquals(10, success)
        assertEquals(10, failure)
        assertEquals(0, walletRepository.findByOwnerId(u)!!.balance)
    }

    @Test
    fun `동시 충전 - lost update 없이 모두 반영된다`() {
        val u = newUser()
        walletStore.getOrCreate(u)
        val (success, _) = runConcurrently(50) { walletStore.topUp(u, 1_000, null) }
        assertEquals(50, success)
        assertEquals(WalletStore.INITIAL_BALANCE + 50_000, walletRepository.findByOwnerId(u)!!.balance)
    }

    @Test
    fun `양방향 동시 송금 - 데드락 없이 완료되고 총액이 보존된다`() {
        val a = newUser()
        val b = newUser()
        walletStore.getOrCreate(a)
        walletStore.getOrCreate(b)
        val total = walletRepository.findByOwnerId(a)!!.balance + walletRepository.findByOwnerId(b)!!.balance
        val (success, failure) = runConcurrently(40) { i ->
            if (i % 2 == 0) walletStore.transfer(a, b, 1_000, null)
            else walletStore.transfer(b, a, 1_000, null)
        }
        assertEquals(40, success)
        assertEquals(0, failure)
        val sum = walletRepository.findByOwnerId(a)!!.balance + walletRepository.findByOwnerId(b)!!.balance
        assertEquals(total, sum)
    }

    @Test
    fun `동시 수락 - 정산은 정확히 한 번 일어나고 개설자는 합계만 받는다`() {
        val c = newUser()
        val parts = (1..8).map { newUser() }
        walletStore.getOrCreate(c)
        val bill = splitBillStore.create(
            creatorId = c,
            totalAmount = 80_000,
            memo = null,
            creatorPercent = 0,
            participants = parts.map { SplitParticipantInput(it.toString(), null) },
        )
        val shareSum = parts.sumOf { participantRepository.findBySplitBillIdAndUserId(bill.id, it)!!.shareAmount }

        val (success, failure) = runConcurrently(parts.size) { i -> splitBillStore.accept(parts[i], bill.id) }

        assertEquals(parts.size, success)
        assertEquals(0, failure)
        assertEquals(SplitBillStatus.SETTLED, billRepository.findById(bill.id).orElseThrow().status)
        assertEquals(WalletStore.INITIAL_BALANCE + shareSum, walletRepository.findByOwnerId(c)!!.balance)
        parts.forEach { assertEquals(0, walletRepository.findByOwnerId(it)!!.held) }
    }
}
