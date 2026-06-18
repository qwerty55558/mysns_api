package com.mysns.main.graphql.data

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface CrowdfundingRepository : JpaRepository<Crowdfunding, Long> {

    fun existsByCreatorIdAndStatusAndCurrentAmountGreaterThan(
        creatorId: Long,
        status: CrowdfundingStatus,
        currentAmount: Int,
    ): Boolean


    /** 동시 후원/마감 요청을 직렬화하기 위한 비관적 쓰기 잠금 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Crowdfunding c WHERE c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Crowdfunding?

    fun findByPostId(postId: Long): Crowdfunding?

    /** 스케줄러용 — 지정 상태이면서 deadline 이 now 이하인 항목. */
    fun findByStatusAndDeadlineLessThanEqual(
        status: CrowdfundingStatus,
        deadline: OffsetDateTime,
    ): List<Crowdfunding>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): List<Crowdfunding>

    /** 내가 개설하거나 후원한 크라우드펀딩을 최신순으로. */
    @Query(
        "SELECT c FROM Crowdfunding c " +
            "WHERE c.creatorId = :userId " +
            "OR EXISTS (SELECT 1 FROM Backing b WHERE b.crowdfundingId = c.id AND b.userId = :userId) " +
            "ORDER BY c.createdAt DESC",
    )
    fun findHistoryForUser(@Param("userId") userId: Long, pageable: Pageable): List<Crowdfunding>

    /** 현재 모집 금액 원자적 증가. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Crowdfunding c SET c.currentAmount = c.currentAmount + :amount, c.updatedAt = :now " +
            "WHERE c.id = :id",
    )
    fun incrementCurrentAmount(
        @Param("id") id: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 현재 모집 금액 원자적 감소 — currentAmount >= amount 일 때만 갱신. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Crowdfunding c SET c.currentAmount = c.currentAmount - :amount, c.updatedAt = :now " +
            "WHERE c.id = :id AND c.currentAmount >= :amount",
    )
    fun decrementCurrentAmount(
        @Param("id") id: Long,
        @Param("amount") amount: Int,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 상태·정산시각 갱신. settledAt 은 SUCCEEDED 시점에만 값을 넣고, FAILED 시 null 로 전달. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Crowdfunding c SET c.status = :status, c.settledAt = :settledAt, c.updatedAt = :now " +
            "WHERE c.id = :id",
    )
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: CrowdfundingStatus,
        @Param("settledAt") settledAt: OffsetDateTime?,
        @Param("now") now: OffsetDateTime,
    ): Int
}

interface BackingRepository : JpaRepository<Backing, Long> {

    fun findByCrowdfundingIdAndUserIdAndStatus(
        crowdfundingId: Long,
        userId: Long,
        status: BackingStatus,
    ): Backing?

    fun findByCrowdfundingIdAndStatus(crowdfundingId: Long, status: BackingStatus): List<Backing>

    fun countByCrowdfundingIdAndStatus(crowdfundingId: Long, status: BackingStatus): Long

    fun findByUserIdAndStatus(userId: Long, status: BackingStatus): List<Backing>

    fun existsByUserIdAndStatus(userId: Long, status: BackingStatus): Boolean

    /** 특정 크라우드펀딩의 후원 상태를 일괄 변경. 영향 행 수 반환. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE Backing b SET b.status = :to, b.updatedAt = :now " +
            "WHERE b.crowdfundingId = :crowdfundingId AND b.status = :from",
    )
    fun updateStatusForCrowdfunding(
        @Param("crowdfundingId") crowdfundingId: Long,
        @Param("from") from: BackingStatus,
        @Param("to") to: BackingStatus,
        @Param("now") now: OffsetDateTime,
    ): Int

    /** 단건 후원 상태 변경. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Backing b SET b.status = :status, b.updatedAt = :now WHERE b.id = :id")
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: BackingStatus,
        @Param("now") now: OffsetDateTime,
    ): Int
}

interface ChecklistItemRepository : JpaRepository<ChecklistItem, Long> {

    fun findByCrowdfundingIdOrderByPositionAsc(crowdfundingId: Long): List<ChecklistItem>

    fun countByCrowdfundingId(crowdfundingId: Long): Long
}
