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
        getOrCreate(ownerId)
        walletRepository.credit(ownerId, amount, OffsetDateTime.now())
        val wallet = walletRepository.findByOwnerId(ownerId)!!
        record(ownerId, WalletTransactionType.TOPUP, amount, wallet.balance, null, memo)
        return wallet
    }

    @Transactional
    fun withdraw(ownerId: Long, amount: Int, memo: String?): Wallet {
        validateAmount(amount)
        getOrCreate(ownerId)
        val updated = walletRepository.debit(ownerId, amount, OffsetDateTime.now())
        require(updated == 1) { "잔액이 부족합니다." }
        val wallet = walletRepository.findByOwnerId(ownerId)!!
        record(ownerId, WalletTransactionType.WITHDRAW, amount, wallet.balance, null, memo)
        return wallet
    }

    /** P2P 송금. 양쪽 잔액을 한 트랜잭션에서 원자적으로 갱신하고 원장 2건 기록. 보낸 사람 거래(TRANSFER_OUT) 반환. */
    @Transactional
    fun transfer(senderId: Long, recipientId: Long, amount: Int, memo: String?): WalletTransaction {
        validateAmount(amount)
        require(senderId != recipientId) { "자기 자신에게는 송금할 수 없습니다." }
        require(userRepository.existsById(recipientId)) { "받는 사람을 찾을 수 없습니다." }
        getOrCreate(senderId)
        getOrCreate(recipientId)

        val now = OffsetDateTime.now()
        // 데드락 방지: owner_id 오름차순으로 UPDATE를 발행해 행 잠금 순서를 고정한다.
        if (senderId < recipientId) {
            require(walletRepository.debit(senderId, amount, now) == 1) { "잔액이 부족합니다." }
            walletRepository.credit(recipientId, amount, now)
        } else {
            walletRepository.credit(recipientId, amount, now)
            require(walletRepository.debit(senderId, amount, now) == 1) { "잔액이 부족합니다." }
        }

        val sender = walletRepository.findByOwnerId(senderId)!!
        val recipient = walletRepository.findByOwnerId(recipientId)!!
        val out = record(senderId, WalletTransactionType.TRANSFER_OUT, amount, sender.balance, recipientId, memo)
        record(recipientId, WalletTransactionType.TRANSFER_IN, amount, recipient.balance, senderId, memo)
        return out
    }

    /** 구독 결제 — 지출가능 잔액에서 금액을 차감하고 거래를 기록한다. 잔액 부족이면 실패. */
    @Transactional
    fun charge(ownerId: Long, amount: Int, memo: String?): WalletTransaction {
        validateAmount(amount)
        getOrCreate(ownerId)
        require(walletRepository.debit(ownerId, amount, OffsetDateTime.now()) == 1) { "잔액이 부족합니다." }
        val wallet = walletRepository.findByOwnerId(ownerId)!!
        return record(ownerId, WalletTransactionType.SUBSCRIPTION_CHARGE, amount, wallet.balance, null, memo)
    }

    /** 정산 예치 — 참가자의 지출가능 잔액을 묶는다(held). 잔액 부족이면 실패. */
    @Transactional
    fun hold(ownerId: Long, amount: Int, counterpartyId: Long, memo: String?): WalletTransaction {
        validateAmount(amount)
        getOrCreate(ownerId)
        require(walletRepository.hold(ownerId, amount, OffsetDateTime.now()) == 1) { "잔액이 부족합니다." }
        val wallet = walletRepository.findByOwnerId(ownerId)!!
        return record(ownerId, WalletTransactionType.SPLIT_HOLD, amount, wallet.balance, counterpartyId, memo)
    }

    /** 정산 환불 — 묶인 잔액을 지출가능 잔액으로 되돌린다. */
    @Transactional
    fun release(ownerId: Long, amount: Int, counterpartyId: Long, memo: String?): WalletTransaction {
        require(amount > 0) { "환불 금액이 올바르지 않습니다." }
        getOrCreate(ownerId)
        require(walletRepository.release(ownerId, amount, OffsetDateTime.now()) == 1) { "환불할 예치 잔액이 없습니다." }
        val wallet = walletRepository.findByOwnerId(ownerId)!!
        return record(ownerId, WalletTransactionType.SPLIT_REFUND, amount, wallet.balance, counterpartyId, memo)
    }

    /** 정산 확정 지급 — 참가자의 묶인 잔액을 개설자(수금자)에게 한 트랜잭션에서 원자적으로 지급한다. */
    @Transactional
    fun settleHeld(payerId: Long, payeeId: Long, amount: Int, memo: String?): WalletTransaction {
        require(amount > 0) { "정산 금액이 올바르지 않습니다." }
        getOrCreate(payerId)
        getOrCreate(payeeId)
        val now = OffsetDateTime.now()
        // 데드락 방지: owner_id 오름차순으로 UPDATE를 발행해 행 잠금 순서를 고정한다.
        if (payerId < payeeId) {
            require(walletRepository.settleHeld(payerId, amount, now) == 1) { "예치 잔액이 부족합니다." }
            walletRepository.credit(payeeId, amount, now)
        } else {
            walletRepository.credit(payeeId, amount, now)
            require(walletRepository.settleHeld(payerId, amount, now) == 1) { "예치 잔액이 부족합니다." }
        }
        val payee = walletRepository.findByOwnerId(payeeId)!!
        return record(payeeId, WalletTransactionType.SPLIT_SETTLE_IN, amount, payee.balance, payerId, memo)
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
