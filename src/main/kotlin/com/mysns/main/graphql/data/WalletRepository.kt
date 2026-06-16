package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface WalletRepository : JpaRepository<Wallet, Long> {
    fun findByOwnerId(ownerId: Long): Wallet?

    /** 원자적 잔액 증가. 영향 행 수 반환. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance + :amount, w.updatedAt = :now WHERE w.ownerId = :ownerId")
    fun credit(
        @Param("ownerId") ownerId: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 원자적 잔액 차감. 잔액이 충분할 때만 갱신되며, 영향 행 수(0 또는 1)를 반환. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount, w.updatedAt = :now WHERE w.ownerId = :ownerId AND w.balance >= :amount")
    fun debit(
        @Param("ownerId") ownerId: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 정산 예치: 지출가능 잔액을 묶음(balance→held). 잔액이 충분할 때만. 영향 행 수(0 또는 1) 반환. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance - :amount, w.held = w.held + :amount, w.updatedAt = :now WHERE w.ownerId = :ownerId AND w.balance >= :amount")
    fun hold(
        @Param("ownerId") ownerId: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 정산 환불: 묶인 잔액을 지출가능 잔액으로 되돌림(held→balance). held가 충분할 때만. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.balance = w.balance + :amount, w.held = w.held - :amount, w.updatedAt = :now WHERE w.ownerId = :ownerId AND w.held >= :amount")
    fun release(
        @Param("ownerId") ownerId: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 정산 확정: 묶인 잔액에서만 차감(지출가능 잔액 변화 없음). 개설자 credit과 짝지어 사용. held가 충분할 때만. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Wallet w SET w.held = w.held - :amount, w.updatedAt = :now WHERE w.ownerId = :ownerId AND w.held >= :amount")
    fun settleHeld(
        @Param("ownerId") ownerId: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int
}

interface WalletTransactionRepository : JpaRepository<WalletTransaction, Long> {
    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: Long, pageable: Pageable): List<WalletTransaction>
}
