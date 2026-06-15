package com.mysns.main.graphql.data

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class WalletStore(
    private val walletRepository: WalletRepository,
    private val txRepository: WalletTransactionRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        /** 모의 시작 잔액 — 신규 지갑 최초 조회 시 자동 충전. */
        const val INITIAL_BALANCE = 1_000_000

        /** 단건 거래 상한 (모의 안전장치). */
        const val MAX_AMOUNT = 100_000_000
    }

    @Transactional
    fun getOrCreate(ownerId: Long): Wallet =
        walletRepository.findByOwnerId(ownerId)
            ?: walletRepository.save(Wallet(ownerId = ownerId, balance = INITIAL_BALANCE))

    fun transactions(ownerId: Long, limit: Int, offset: Int): List<WalletTransaction> {
        val size = limit.coerceAtLeast(1)
        return txRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(offset / size, size))
    }

    @Transactional
    fun topUp(ownerId: Long, amount: Int, memo: String?): Wallet {
        validateAmount(amount)
        val wallet = getOrCreate(ownerId)
        wallet.balance += amount
        wallet.updatedAt = OffsetDateTime.now()
        record(ownerId, WalletTransactionType.TOPUP, amount, wallet.balance, null, memo)
        return wallet
    }

    @Transactional
    fun withdraw(ownerId: Long, amount: Int, memo: String?): Wallet {
        validateAmount(amount)
        val wallet = getOrCreate(ownerId)
        require(wallet.balance >= amount) { "잔액이 부족합니다." }
        wallet.balance -= amount
        wallet.updatedAt = OffsetDateTime.now()
        record(ownerId, WalletTransactionType.WITHDRAW, amount, wallet.balance, null, memo)
        return wallet
    }

    /** P2P 송금. 양쪽 잔액을 한 트랜잭션에서 갱신하고 원장 2건 기록. 보낸 사람 거래(TRANSFER_OUT) 반환. */
    @Transactional
    fun transfer(senderId: Long, recipientId: Long, amount: Int, memo: String?): WalletTransaction {
        validateAmount(amount)
        require(senderId != recipientId) { "자기 자신에게는 송금할 수 없습니다." }
        require(userRepository.existsById(recipientId)) { "받는 사람을 찾을 수 없습니다." }
        val sender = getOrCreate(senderId)
        val recipient = getOrCreate(recipientId)
        require(sender.balance >= amount) { "잔액이 부족합니다." }

        val now = OffsetDateTime.now()
        sender.balance -= amount
        recipient.balance += amount
        sender.updatedAt = now
        recipient.updatedAt = now

        val out = record(senderId, WalletTransactionType.TRANSFER_OUT, amount, sender.balance, recipientId, memo)
        record(recipientId, WalletTransactionType.TRANSFER_IN, amount, recipient.balance, senderId, memo)
        return out
    }

    private fun record(
        ownerId: Long,
        type: WalletTransactionType,
        amount: Int,
        balanceAfter: Int,
        counterpartyId: Long?,
        memo: String?,
    ): WalletTransaction =
        txRepository.save(
            WalletTransaction(
                ownerId = ownerId,
                type = type,
                amount = amount,
                balanceAfter = balanceAfter,
                counterpartyId = counterpartyId,
                memo = memo?.trim()?.takeIf { it.isNotEmpty() },
            )
        )

    private fun validateAmount(amount: Int) {
        require(amount > 0) { "금액은 0보다 커야 합니다." }
        require(amount <= MAX_AMOUNT) { "한 번에 보낼 수 있는 한도를 초과했습니다." }
    }
}
