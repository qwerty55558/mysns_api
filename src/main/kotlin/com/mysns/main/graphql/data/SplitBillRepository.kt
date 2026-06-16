package com.mysns.main.graphql.data

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SplitBillRepository : JpaRepository<SplitBill, Long> {
    fun findByCreatorIdOrderByCreatedAtDesc(creatorId: Long, pageable: Pageable): List<SplitBill>

    /** 정산 응답(수락/거절/취소)을 같은 bill에 대해 직렬화하기 위한 비관적 쓰기 잠금 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM SplitBill b WHERE b.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): SplitBill?
}

interface SplitParticipantRepository : JpaRepository<SplitParticipant, Long> {
    fun findBySplitBillId(splitBillId: Long): List<SplitParticipant>

    fun findBySplitBillIdAndUserId(splitBillId: Long, userId: Long): SplitParticipant?

    fun findBySplitBillIdIn(splitBillIds: Collection<Long>): List<SplitParticipant>

    fun findByUserIdAndStatusOrderByCreatedAtDesc(
        userId: Long,
        status: SplitParticipantStatus,
        pageable: Pageable,
    ): List<SplitParticipant>

    @Query(
        "select p from SplitParticipant p, SplitBill b " +
            "where b.id = p.splitBillId and p.userId = :userId " +
            "and p.status = :participantStatus and b.status = :billStatus " +
            "order by p.createdAt desc",
    )
    fun findPendingForOpenBills(
        @Param("userId") userId: Long,
        @Param("participantStatus") participantStatus: SplitParticipantStatus,
        @Param("billStatus") billStatus: SplitBillStatus,
        pageable: Pageable,
    ): List<SplitParticipant>
}
