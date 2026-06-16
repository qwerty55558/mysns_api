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
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

enum class SplitBillStatus { OPEN, SETTLED, CANCELLED }

enum class SplitParticipantStatus { PENDING, ACCEPTED, DECLINED }

/** N빵(더치페이) 정산 요청. 개설자(creator)가 미리 결제한 금액을 태그된 유저들에게 비율대로 나눠 받는다. */
@Entity
@Table(
    name = "split_bills",
    indexes = [Index(name = "idx_split_bills_creator", columnList = "creator_id, created_at")],
)
class SplitBill(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "creator_id", nullable = false)
    val creatorId: Long,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SplitBillStatus = SplitBillStatus.OPEN,

    @Column(length = 140)
    val memo: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

/** 정산 참가자. 개설자도 1건(isCreator=true, 표시용·차감 없음) 기록된다. */
@Entity
@Table(
    name = "split_participants",
    uniqueConstraints = [UniqueConstraint(name = "uq_split_participant", columnNames = ["split_bill_id", "user_id"])],
    indexes = [
        Index(name = "idx_split_participants_bill", columnList = "split_bill_id"),
        Index(name = "idx_split_participants_user", columnList = "user_id, status"),
    ],
)
class SplitParticipant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "split_bill_id", nullable = false)
    val splitBillId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    /** 부담 비율(%). 합계 100. */
    @Column(nullable = false)
    val percent: Int,

    /** 실제 부담 금액(원). 반올림 잔돈은 개설자에게 귀속. */
    @Column(name = "share_amount", nullable = false)
    val shareAmount: Int,

    @Column(name = "is_creator", nullable = false)
    val isCreator: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SplitParticipantStatus = SplitParticipantStatus.PENDING,

    @Column(name = "responded_at")
    var respondedAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)
