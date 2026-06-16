package com.mysns.main.graphql.data

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime

/** 모의 지갑 거래 종류. 충전/출금은 counterparty 없음, 송금/정산은 상대 user 참조. */
enum class WalletTransactionType {
    TOPUP, WITHDRAW, TRANSFER_OUT, TRANSFER_IN,
    // N빵 정산: 예치(잠금) / 환불(해제) / 정산 확정 수금.
    SPLIT_HOLD, SPLIT_REFUND, SPLIT_SETTLE_IN
}

@Entity
@Table(
    name = "wallets",
    indexes = [Index(name = "idx_wallets_owner", columnList = "owner_id", unique = true)],
)
class Wallet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "owner_id", nullable = false, unique = true)
    val ownerId: Long,

    /** 모의 잔액 (원 단위 정수, 지출 가능분). */
    @Column(nullable = false)
    var balance: Int = 0,

    /** 정산 완료 전까지 묶인(예치된) 잔액. 지출 불가, 정산 확정 시 개설자에게 지급되거나 취소 시 balance로 환불. */
    @Column(nullable = false)
    var held: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(
    name = "wallet_transactions",
    indexes = [Index(name = "idx_wallet_tx_owner_created", columnList = "owner_id, created_at")],
)
class WalletTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 이 거래가 속한 지갑 주인 (송금이면 양쪽에 1건씩 기록). */
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val type: WalletTransactionType,

    /** 항상 양수. 방향은 type으로 구분. */
    @Column(nullable = false)
    val amount: Int,

    @Column(name = "balance_after", nullable = false)
    val balanceAfter: Int,

    /** 송금 상대 user id (충전/출금은 null). */
    @Column(name = "counterparty_id")
    val counterpartyId: Long? = null,

    @Column(length = 140)
    val memo: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
