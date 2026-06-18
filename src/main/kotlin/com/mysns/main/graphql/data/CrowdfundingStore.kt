package com.mysns.main.graphql.data

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class CrowdfundingStore(
    private val crowdfundingRepository: CrowdfundingRepository,
    private val backingRepository: BackingRepository,
    private val checklistItemRepository: ChecklistItemRepository,
    private val walletStore: WalletStore,
) {
    private val log = LoggerFactory.getLogger(CrowdfundingStore::class.java)

    // ─────────────────────────────────────────────
    // 조회 헬퍼
    // ─────────────────────────────────────────────

    fun findById(id: Long): Crowdfunding? = crowdfundingRepository.findById(id).orElse(null)

    fun findByPostId(postId: Long): Crowdfunding? = crowdfundingRepository.findByPostId(postId)

    fun list(limit: Int, offset: Int): List<Crowdfunding> {
        val size = limit.coerceAtLeast(1)
        return crowdfundingRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(offset / size, size))
    }

    fun myCrowdfundings(userId: Long, limit: Int, offset: Int): List<Crowdfunding> {
        val size = limit.coerceAtLeast(1)
        return crowdfundingRepository.findHistoryForUser(userId, PageRequest.of(offset / size, size))
    }

    fun activeBackingCount(crowdfundingId: Long): Long =
        backingRepository.countByCrowdfundingIdAndStatus(crowdfundingId, BackingStatus.ACTIVE)

    /** 해당 유저의 ACTIVE 후원, 없으면 가장 최근 후원을 반환. */
    fun viewerBacking(userId: Long, crowdfundingId: Long): Backing? =
        backingRepository.findByCrowdfundingIdAndUserIdAndStatus(crowdfundingId, userId, BackingStatus.ACTIVE)
            ?: backingRepository.findByCrowdfundingIdAndStatus(crowdfundingId, BackingStatus.ACTIVE)
                .firstOrNull { it.userId == userId }

    fun checklist(crowdfundingId: Long): List<ChecklistItem> =
        checklistItemRepository.findByCrowdfundingIdOrderByPositionAsc(crowdfundingId)

    // ─────────────────────────────────────────────
    // 쓰기 — 크라우드펀딩 생성 / 마감
    // ─────────────────────────────────────────────

    /** 크라우드펀딩 생성. goalAmount > 0, deadline 은 현재 이후여야 한다. */
    @Transactional
    fun create(creatorId: Long, postId: Long, goalAmount: Int, deadline: OffsetDateTime): Crowdfunding {
        val now = OffsetDateTime.now()
        require(goalAmount > 0) { "목표 금액은 0보다 커야 합니다." }
        require(deadline.isAfter(now)) { "마감 일시는 현재 이후여야 합니다." }
        return crowdfundingRepository.save(
            Crowdfunding(
                postId = postId,
                creatorId = creatorId,
                goalAmount = goalAmount,
                deadline = deadline,
            )
        )
    }

    /** 개설자가 목표 금액 달성 후 직접 마감. */
    @Transactional
    fun closeCrowdfunding(userId: Long, crowdfundingId: Long): Crowdfunding {
        val cf = crowdfundingRepository.findByIdForUpdate(crowdfundingId)
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
        require(cf.creatorId == userId) { "개설자만 마감할 수 있습니다." }
        require(cf.status == CrowdfundingStatus.OPEN) { "이미 종료된 크라우드펀딩입니다." }
        require(cf.currentAmount >= cf.goalAmount) { "목표 금액에 도달해야 마감할 수 있습니다." }
        val now = OffsetDateTime.now()
        settle(cf, now)
        return crowdfundingRepository.findById(crowdfundingId).orElseThrow()
    }

    // ─────────────────────────────────────────────
    // 쓰기 — 후원 / 후원 취소
    // ─────────────────────────────────────────────

    /**
     * 후원 등록.
     * 1) 비관적 잠금으로 중복 후원·경쟁 방지
     * 2) 지갑 예치(hold) 후 — PC 클리어 가능성 때문에 엔티티 dirty-check 금지
     * 3) @Modifying 쿼리로 currentAmount 증가
     */
    @Transactional
    fun back(userId: Long, crowdfundingId: Long, amount: Int): Backing {
        val cf = crowdfundingRepository.findByIdForUpdate(crowdfundingId)
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
        val now = OffsetDateTime.now()
        require(cf.status == CrowdfundingStatus.OPEN) { "모집 중인 크라우드펀딩이 아닙니다." }
        require(cf.deadline.isAfter(now)) { "마감된 크라우드펀딩입니다." }
        require(amount > 0) { "후원 금액은 0보다 커야 합니다." }
        require(userId != cf.creatorId) { "개설자는 자신의 크라우드펀딩에 후원할 수 없습니다." }
        require(
            backingRepository.findByCrowdfundingIdAndUserIdAndStatus(crowdfundingId, userId, BackingStatus.ACTIVE) == null
        ) { "이미 후원 중입니다." }

        // 지갑 예치 — @Modifying 으로 PC를 비울 수 있으므로 이후 엔티티 dirty-check 금지.
        walletStore.hold(
            ownerId = userId,
            amount = amount,
            counterpartyId = cf.creatorId,
            memo = "크라우드펀딩 후원 예치",
            type = WalletTransactionType.CROWDFUNDING_HOLD,
        )

        // PC 클리어 이후이므로 새 엔티티를 save 한다.
        val backing = backingRepository.save(
            Backing(
                crowdfundingId = crowdfundingId,
                userId = userId,
                amount = amount,
            )
        )

        // currentAmount 를 @Modifying 으로 원자 증가.
        crowdfundingRepository.incrementCurrentAmount(crowdfundingId, amount, OffsetDateTime.now())

        return backing
    }

    /**
     * 후원 취소 (OPEN & deadline 이전에만 가능).
     * 1) 비관적 잠금
     * 2) 지갑 환불(release) 후 — PC 클리어 가능성 때문에 엔티티 dirty-check 금지
     * 3) @Modifying 쿼리로 Backing 상태 CANCELLED, currentAmount 감소
     */
    @Transactional
    fun cancelBacking(userId: Long, crowdfundingId: Long): Boolean {
        val cf = crowdfundingRepository.findByIdForUpdate(crowdfundingId)
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
        val now = OffsetDateTime.now()
        require(cf.status == CrowdfundingStatus.OPEN) { "종료된 크라우드펀딩은 후원 취소가 불가능합니다." }
        require(cf.deadline.isAfter(now)) { "마감된 크라우드펀딩입니다." }

        val backing = backingRepository.findByCrowdfundingIdAndUserIdAndStatus(
            crowdfundingId, userId, BackingStatus.ACTIVE
        ) ?: return false

        // 지갑 환불 — @Modifying 으로 PC를 비울 수 있으므로 이후 엔티티 dirty-check 금지.
        walletStore.release(
            ownerId = userId,
            amount = backing.amount,
            counterpartyId = cf.creatorId,
            memo = "크라우드펀딩 후원 취소 환불",
            type = WalletTransactionType.CROWDFUNDING_REFUND,
        )

        // PC 클리어 이후이므로 @Modifying 쿼리로만 상태 변경.
        backingRepository.updateStatus(backing.id, BackingStatus.CANCELLED, OffsetDateTime.now())
        crowdfundingRepository.decrementCurrentAmount(crowdfundingId, backing.amount, OffsetDateTime.now())

        return true
    }

    // ─────────────────────────────────────────────
    // 내부 — 정산·실패 처리
    // ─────────────────────────────────────────────

    /**
     * 정산 확정: 모든 ACTIVE 후원자의 예치금을 개설자에게 지급하고 SUCCEEDED 로 전환.
     * walletStore.settleHeld 이후 PC가 클리어되므로 엔티티 dirty-check 절대 금지.
     */
    private fun settle(cf: Crowdfunding, now: OffsetDateTime) {
        val activeBackings = backingRepository.findByCrowdfundingIdAndStatus(cf.id, BackingStatus.ACTIVE)
        for (backing in activeBackings) {
            walletStore.settleHeld(
                payerId = backing.userId,
                payeeId = cf.creatorId,
                amount = backing.amount,
                memo = "크라우드펀딩 정산",
                type = WalletTransactionType.CROWDFUNDING_SETTLE_IN,
            )
        }
        // PC 클리어 이후이므로 @Modifying 쿼리만 사용.
        backingRepository.updateStatusForCrowdfunding(cf.id, BackingStatus.ACTIVE, BackingStatus.SETTLED, now)
        crowdfundingRepository.updateStatus(cf.id, CrowdfundingStatus.SUCCEEDED, now, now)
    }

    /**
     * 실패 처리: 모든 ACTIVE 후원자의 예치금을 환불하고 FAILED 로 전환.
     * walletStore.release 이후 PC가 클리어되므로 엔티티 dirty-check 절대 금지.
     */
    private fun fail(cf: Crowdfunding, now: OffsetDateTime) {
        val activeBackings = backingRepository.findByCrowdfundingIdAndStatus(cf.id, BackingStatus.ACTIVE)
        for (backing in activeBackings) {
            walletStore.release(
                ownerId = backing.userId,
                amount = backing.amount,
                counterpartyId = cf.creatorId,
                memo = "크라우드펀딩 실패 환불",
                type = WalletTransactionType.CROWDFUNDING_REFUND,
            )
        }
        // PC 클리어 이후이므로 @Modifying 쿼리만 사용.
        backingRepository.updateStatusForCrowdfunding(cf.id, BackingStatus.ACTIVE, BackingStatus.REFUNDED, now)
        crowdfundingRepository.updateStatus(cf.id, CrowdfundingStatus.FAILED, null, now)
    }

    // ─────────────────────────────────────────────
    // 스케줄러 진입점
    // ─────────────────────────────────────────────

    /**
     * 마감 기한이 지난 OPEN 크라우드펀딩을 일괄 처리.
     * goalAmount 달성 시 정산, 미달 시 실패·환불.
     * SubscriptionStore.processDueRenewals 와 동일한 배치 루프 패턴.
     */
    @Transactional
    fun processDueDeadlines(now: OffsetDateTime) {
        val due = crowdfundingRepository.findByStatusAndDeadlineLessThanEqual(CrowdfundingStatus.OPEN, now)
        if (due.isEmpty()) return

        for (cf in due) {
            // 각 항목을 재잠금하여 동시 요청과 직렬화한다.
            val locked = crowdfundingRepository.findByIdForUpdate(cf.id) ?: continue
            if (locked.status != CrowdfundingStatus.OPEN) continue // 이미 처리됨
            try {
                if (locked.currentAmount >= locked.goalAmount) {
                    settle(locked, now)
                    log.info("크라우드펀딩 정산 완료 — id={}", locked.id)
                } else {
                    fail(locked, now)
                    log.info("크라우드펀딩 실패 처리 — id={}", locked.id)
                }
            } catch (ex: Exception) {
                log.warn("크라우드펀딩 마감 처리 실패 — id={}", locked.id, ex)
            }
        }
    }

    // ─────────────────────────────────────────────
    // 체크리스트
    // ─────────────────────────────────────────────

    /** 체크리스트 항목 추가 (개설자만). */
    @Transactional
    fun addChecklistItem(userId: Long, crowdfundingId: Long, text: String): ChecklistItem {
        val cf = crowdfundingRepository.findById(crowdfundingId).orElse(null)
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
        require(cf.creatorId == userId) { "개설자만 체크리스트를 수정할 수 있습니다." }
        val position = checklistItemRepository.countByCrowdfundingId(crowdfundingId).toInt()
        return checklistItemRepository.save(
            ChecklistItem(
                crowdfundingId = crowdfundingId,
                text = text.trim().takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("텍스트를 입력하세요."),
                position = position,
            )
        )
    }

    /**
     * 체크리스트 항목 완료 토글 (개설자만).
     * 지갑 호출이 없으므로 dirty-check/save 방식이 안전하다.
     */
    @Transactional
    fun toggleChecklistItem(userId: Long, itemId: Long): ChecklistItem {
        val item = checklistItemRepository.findById(itemId).orElse(null)
            ?: throw IllegalArgumentException("체크리스트 항목을 찾을 수 없습니다.")
        val cf = crowdfundingRepository.findById(item.crowdfundingId).orElse(null)
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
        require(cf.creatorId == userId) { "개설자만 체크리스트를 수정할 수 있습니다." }
        item.done = !item.done
        return checklistItemRepository.save(item)
    }

    /** 체크리스트 항목 삭제 (개설자만). */
    @Transactional
    fun removeChecklistItem(userId: Long, itemId: Long): Boolean {
        val item = checklistItemRepository.findById(itemId).orElse(null) ?: return false
        val cf = crowdfundingRepository.findById(item.crowdfundingId).orElse(null)
            ?: throw IllegalArgumentException("크라우드펀딩을 찾을 수 없습니다.")
        require(cf.creatorId == userId) { "개설자만 체크리스트를 수정할 수 있습니다." }
        checklistItemRepository.deleteById(itemId)
        return true
    }
}
