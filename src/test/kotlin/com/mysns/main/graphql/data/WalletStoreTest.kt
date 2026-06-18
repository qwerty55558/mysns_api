package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(WalletStore::class)
class WalletStoreTest @Autowired constructor(
    private val walletStore: WalletStore,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
) {
    private fun newUser(name: String): Long =
        userRepository.save(
            User(username = name, displayName = name, createdAt = OffsetDateTime.now(), passwordHash = "x"),
        ).id

    private fun balanceOf(id: Long) = walletRepository.findByOwnerId(id)!!.balance
    private fun heldOf(id: Long) = walletRepository.findByOwnerId(id)!!.held

    @Test
    fun `topUp 은 잔액을 늘린다`() {
        val u = newUser("u")
        walletStore.getOrCreate(u)
        walletStore.topUp(u, 5_000, "충전")
        assertEquals(WalletStore.INITIAL_BALANCE + 5_000, balanceOf(u))
    }

    @Test
    fun `withdraw 는 잔액을 줄이고 잔액부족이면 실패한다`() {
        val u = newUser("u")
        walletStore.getOrCreate(u)
        walletStore.withdraw(u, 5_000, null)
        assertEquals(WalletStore.INITIAL_BALANCE - 5_000, balanceOf(u))
        val ex = assertThrows(IllegalArgumentException::class.java) {
            walletStore.withdraw(u, WalletStore.INITIAL_BALANCE, null)
        }
        assertEquals("잔액이 부족합니다.", ex.message)
    }

    @Test
    fun `transfer 는 양쪽 잔액을 원자적으로 옮긴다`() {
        val a = newUser("a")
        val b = newUser("b")
        walletStore.getOrCreate(a)
        walletStore.getOrCreate(b)
        walletStore.transfer(a, b, 10_000, "송금")
        assertEquals(WalletStore.INITIAL_BALANCE - 10_000, balanceOf(a))
        assertEquals(WalletStore.INITIAL_BALANCE + 10_000, balanceOf(b))
    }

    @Test
    fun `transfer 자기송금과 잔액부족은 실패한다`() {
        val a = newUser("a")
        val b = newUser("b")
        walletStore.getOrCreate(a)
        walletStore.getOrCreate(b)
        assertThrows(IllegalArgumentException::class.java) { walletStore.transfer(a, a, 1_000, null) }
        assertThrows(IllegalArgumentException::class.java) {
            walletStore.transfer(a, b, WalletStore.INITIAL_BALANCE + 1, null)
        }
    }

    @Test
    fun `hold 는 balance 를 held 로 묶고 잔액부족이면 실패한다`() {
        val u = newUser("u")
        val counterparty = newUser("counterparty-hold")
        walletStore.getOrCreate(u)
        walletStore.hold(u, 30_000, counterpartyId = counterparty, memo = "예치")
        assertEquals(WalletStore.INITIAL_BALANCE - 30_000, balanceOf(u))
        assertEquals(30_000, heldOf(u))
        assertThrows(IllegalArgumentException::class.java) {
            walletStore.hold(u, WalletStore.INITIAL_BALANCE, counterparty, null)
        }
    }

    @Test
    fun `release 는 held 를 balance 로 되돌린다`() {
        val u = newUser("u")
        val counterparty = newUser("counterparty-release")
        walletStore.getOrCreate(u)
        walletStore.hold(u, 30_000, counterparty, null)
        walletStore.release(u, 30_000, counterparty, "환불")
        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(u))
        assertEquals(0, heldOf(u))
    }

    @Test
    fun `settleHeld 는 held 를 수금자에게 지급한다`() {
        val payer = newUser("payer")
        val payee = newUser("payee")
        walletStore.getOrCreate(payer)
        walletStore.getOrCreate(payee)
        walletStore.hold(payer, 30_000, payee, null)
        walletStore.settleHeld(payer, payee, 30_000, "정산")
        assertEquals(WalletStore.INITIAL_BALANCE - 30_000, balanceOf(payer))
        assertEquals(0, heldOf(payer))
        assertEquals(WalletStore.INITIAL_BALANCE + 30_000, balanceOf(payee))
    }
}
