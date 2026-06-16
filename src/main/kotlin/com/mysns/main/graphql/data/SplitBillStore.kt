package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.SplitParticipantInput
import com.mysns.main.graphql.notification.NotificationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class SplitBillStore(
    private val billRepository: SplitBillRepository,
    private val participantRepository: SplitParticipantRepository,
    private val userRepository: UserRepository,
    private val walletStore: WalletStore,
    private val events: ApplicationEventPublisher,
) {
    companion object {
        const val MAX_PARTICIPANTS = 50
    }

    fun findBill(id: Long): SplitBill? = billRepository.findById(id).orElse(null)

    /** 상세 조회 — 개설자 또는 참가자만 볼 수 있다. */
    fun findBillVisibleTo(id: Long, viewerId: Long): SplitBill? {
        val bill = findBill(id) ?: return null
        if (bill.creatorId == viewerId) return bill
        val isParticipant = participantRepository.findBySplitBillIdAndUserId(id, viewerId) != null
        return if (isParticipant) bill else null
    }

    fun findBills(ids: Collection<Long>): List<SplitBill> =
        if (ids.isEmpty()) emptyList() else billRepository.findAllById(ids)

    fun createdBills(creatorId: Long, limit: Int, offset: Int): List<SplitBill> {
        val size = limit.coerceAtLeast(1)
        return billRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId, PageRequest.of(offset / size, size))
    }

    fun pendingRequests(userId: Long, limit: Int, offset: Int): List<SplitParticipant> {
        val size = limit.coerceAtLeast(1)
        return participantRepository.findPendingForOpenBills(
            userId,
            SplitParticipantStatus.PENDING,
            SplitBillStatus.OPEN,
            PageRequest.of(offset / size, size),
        )
    }

    fun participants(splitBillId: Long): List<SplitParticipant> =
        participantRepository.findBySplitBillId(splitBillId)

    fun participantsByBillIds(splitBillIds: Collection<Long>): List<SplitParticipant> =
        if (splitBillIds.isEmpty()) emptyList() else participantRepository.findBySplitBillIdIn(splitBillIds)

    @Transactional
    fun create(
        creatorId: Long,
        totalAmount: Int,
        memo: String?,
        creatorPercent: Int?,
        participants: List<SplitParticipantInput>,
    ): SplitBill {
        require(totalAmount > 0) { "금액은 0보다 커야 합니다." }
        require(totalAmount <= WalletStore.MAX_AMOUNT) { "한 번에 정산할 수 있는 한도를 초과했습니다." }
        require(participants.isNotEmpty()) { "정산할 대상을 1명 이상 태그하세요." }
        require(participants.size <= MAX_PARTICIPANTS) { "참가자가 너무 많습니다." }

        val taggedIds = participants.map { it.userId.toLong() }
        require(taggedIds.none { it == creatorId }) { "개설자는 태그 대상이 아닙니다." }
        require(taggedIds.toSet().size == taggedIds.size) { "중복 태그된 유저가 있습니다." }
        require(userRepository.findAllById(taggedIds).size == taggedIds.size) {
            "존재하지 않는 유저가 포함되어 있습니다."
        }

        // 개설자(맨 앞) + 태그된 유저 순으로 (userId, isCreator, 명시비율) 구성
        val entries = ArrayList<Triple<Long, Boolean, Int?>>()
        entries.add(Triple(creatorId, true, creatorPercent))
        participants.forEach { entries.add(Triple(it.userId.toLong(), false, it.percent)) }

        entries.forEach { (_, _, p) ->
            if (p != null) require(p in 0..100) { "비율은 0~100 사이여야 합니다." }
        }
        val explicitSum = entries.mapNotNull { it.third }.sum()
        require(explicitSum <= 100) { "비율 합이 100을 초과했습니다." }
        val nullCount = entries.count { it.third == null }

        val percents: List<Int> =
            if (nullCount == 0) {
                require(explicitSum == 100) { "비율 합이 100이 되어야 합니다." }
                entries.map { it.third!! }
            } else {
                val remaining = 100 - explicitSum
                val base = remaining / nullCount
                val extra = remaining % nullCount
                var unassignedIdx = 0
                entries.map { (_, _, p) ->
                    if (p != null) {
                        p
                    } else {
                        val v = base + if (unassignedIdx < extra) 1 else 0
                        unassignedIdx++
                        v
                    }
                }
            }

        // 비율 → 금액(내림). 반올림으로 떨어진 잔돈은 개설자(맨 앞)가 흡수.
        val shares = percents.map { (totalAmount.toLong() * it / 100).toInt() }.toMutableList()
        val leftover = totalAmount - shares.sum()
        shares[0] = shares[0] + leftover

        val bill = billRepository.save(
            SplitBill(
                creatorId = creatorId,
                totalAmount = totalAmount,
                memo = memo?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        entries.forEachIndexed { i, (uid, isCreator, _) ->
            participantRepository.save(
                SplitParticipant(
                    splitBillId = bill.id,
                    userId = uid,
                    percent = percents[i],
                    shareAmount = shares[i],
                    isCreator = isCreator,
                    status = if (isCreator) SplitParticipantStatus.ACCEPTED else SplitParticipantStatus.PENDING,
                    respondedAt = if (isCreator) bill.createdAt else null,
                ),
            )
        }
        entries.filter { !it.second }.forEach { (uid, _, _) ->
            events.publishEvent(
                NotificationEvent(
                    recipientId = uid,
                    actorId = creatorId,
                    type = NotificationType.SPLIT_REQUEST,
                    entityId = bill.id,
                )
            )
        }
        return bill
    }

    /** 정산 요청 수락 — 내 몫을 '묶인 잔액(held)'으로 예치한다. 실제 지급은 전원 응답 후 정산 확정 시점. */
    @Transactional
    fun accept(userId: Long, splitBillId: Long): SplitParticipant {
        // 같은 정산에 대한 동시 응답을 직렬화하기 위해 bill 행을 잠근다(정산 race 방지).
        val bill = billRepository.findByIdForUpdate(splitBillId) ?: throw IllegalArgumentException("정산 요청을 찾을 수 없습니다.")
        require(bill.status == SplitBillStatus.OPEN) { "이미 종료된 정산입니다." }
        val participant = participantRepository.findBySplitBillIdAndUserId(splitBillId, userId)
            ?: throw IllegalArgumentException("정산 대상이 아닙니다.")
        require(!participant.isCreator) { "개설자는 정산 응답 대상이 아닙니다." }
        require(participant.status == SplitParticipantStatus.PENDING) { "이미 응답한 정산입니다." }

        // 0원 몫(비율 0%)은 예치 없이 수락 처리.
        if (participant.shareAmount > 0) {
            walletStore.hold(
                ownerId = userId,
                amount = participant.shareAmount,
                counterpartyId = bill.creatorId,
                memo = bill.memo?.let { "1/N 예치: $it" } ?: "1/N 예치",
            )
        }
        // walletStore.hold 가 영속성 컨텍스트를 비울 수 있으므로 재조회 후 상태를 변경한다.
        val managed = participantRepository.findById(participant.id).orElseThrow()
        managed.status = SplitParticipantStatus.ACCEPTED
        managed.respondedAt = OffsetDateTime.now()
        participantRepository.save(managed)
        settleIfResolved(splitBillId)
        return managed
    }

    /** 정산 요청 거절. */
    @Transactional
    fun decline(userId: Long, splitBillId: Long): SplitParticipant {
        // 같은 정산에 대한 동시 응답을 직렬화하기 위해 bill 행을 잠근다(정산 race 방지).
        val bill = billRepository.findByIdForUpdate(splitBillId) ?: throw IllegalArgumentException("정산 요청을 찾을 수 없습니다.")
        require(bill.status == SplitBillStatus.OPEN) { "이미 종료된 정산입니다." }
        val participant = participantRepository.findBySplitBillIdAndUserId(splitBillId, userId)
            ?: throw IllegalArgumentException("정산 대상이 아닙니다.")
        require(!participant.isCreator) { "개설자는 정산 응답 대상이 아닙니다." }
        require(participant.status == SplitParticipantStatus.PENDING) { "이미 응답한 정산입니다." }

        participant.status = SplitParticipantStatus.DECLINED
        participant.respondedAt = OffsetDateTime.now()
        settleIfResolved(splitBillId)
        return participant
    }

    /** 정산 취소(개설자만, 진행 중일 때). 이미 수락(예치)한 참가자에게는 묶인 금액을 환불한다. */
    @Transactional
    fun cancel(userId: Long, splitBillId: Long): SplitBill {
        // 같은 정산에 대한 동시 응답을 직렬화하기 위해 bill 행을 잠근다(정산 race 방지).
        val bill = billRepository.findByIdForUpdate(splitBillId) ?: throw IllegalArgumentException("정산 요청을 찾을 수 없습니다.")
        require(bill.creatorId == userId) { "개설자만 취소할 수 있습니다." }
        require(bill.status == SplitBillStatus.OPEN) { "이미 종료된 정산입니다." }

        val refundMemo = bill.memo?.let { "1/N 취소 환불: $it" } ?: "1/N 취소 환불"
        participantRepository.findBySplitBillId(splitBillId)
            .filter { !it.isCreator && it.status == SplitParticipantStatus.ACCEPTED && it.shareAmount > 0 }
            .forEach { p -> walletStore.release(p.userId, p.shareAmount, bill.creatorId, refundMemo) }

        // walletStore.release 가 영속성 컨텍스트를 비울 수 있으므로 재조회 후 상태를 변경한다.
        val managedBill = billRepository.findById(splitBillId).orElseThrow()
        managedBill.status = SplitBillStatus.CANCELLED
        managedBill.updatedAt = OffsetDateTime.now()
        return billRepository.save(managedBill)
    }

    /**
     * 대기 중인 참가자가 없으면(전원 응답 완료) 예치된 몫을 개설자에게 일괄 지급하고 SETTLED로 종료한다.
     * 아직 PENDING이 남아 있으면 아무 것도 하지 않는다.
     */
    private fun settleIfResolved(splitBillId: Long) {
        val participants = participantRepository.findBySplitBillId(splitBillId)
        val stillPending = participants.any { !it.isCreator && it.status == SplitParticipantStatus.PENDING }
        if (stillPending) return

        val bill = billRepository.findById(splitBillId).orElseThrow()
        val settleMemo = bill.memo?.let { "1/N 정산: $it" } ?: "1/N 정산"
        participants
            .filter { !it.isCreator && it.status == SplitParticipantStatus.ACCEPTED && it.shareAmount > 0 }
            .forEach { p -> walletStore.settleHeld(p.userId, bill.creatorId, p.shareAmount, settleMemo) }

        // walletStore.settleHeld 가 영속성 컨텍스트를 비울 수 있으므로 재조회 후 상태를 변경한다.
        val managedBill = billRepository.findById(splitBillId).orElseThrow()
        managedBill.status = SplitBillStatus.SETTLED
        managedBill.updatedAt = OffsetDateTime.now()
        billRepository.save(managedBill)
    }
}
