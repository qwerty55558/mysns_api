package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
@Import(CrowdfundingStore::class, WalletStore::class)
class CrowdfundingStoreTest @Autowired constructor(
    private val crowdfundingStore: CrowdfundingStore,
    private val walletStore: WalletStore,
    private val walletRepository: WalletRepository,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val crowdfundingRepository: CrowdfundingRepository,
    private val backingRepository: BackingRepository,
    private val em: EntityManager,
) {

    private fun newUser(name: String): Long =
        userRepository.save(
            User(username = name, displayName = name, createdAt = OffsetDateTime.now(), passwordHash = "x"),
        ).id

    private fun balanceOf(id: Long) = walletRepository.findByOwnerId(id)!!.balance
    private fun heldOf(id: Long) = walletRepository.findByOwnerId(id)!!.held

    private fun futureDeadline() = OffsetDateTime.now().plusDays(7)
    private fun pastDeadline() = OffsetDateTime.now().minusDays(1)

    /** 테스트에서 deadline 을 과거로 강제 설정 (JPQL UPDATE). */
    private fun expireCf(cfId: Long) {
        em.createQuery(
            "UPDATE Crowdfunding c SET c.deadline = :past WHERE c.id = :id"
        )
            .setParameter("past", pastDeadline())
            .setParameter("id", cfId)
            .executeUpdate()
        em.flush()
        em.clear()
    }

    /** posts 행을 직접 저장해 postId 반환 (PostStore 의존 없이). */
    private fun newPost(authorId: Long): Long =
        postRepository.save(
            Post(
                content = "crowdfunding test post",
                authorId = authorId,
                createdAt = OffsetDateTime.now(),
            )
        ).id

    /** crowdfundings 행을 직접 저장 (CrowdfundingStore.create 는 deadline 검사가 있어 미래만 허용). */
    private fun saveCf(
        creatorId: Long,
        postId: Long,
        goal: Int = 10_000,
        deadline: OffsetDateTime = futureDeadline(),
        status: CrowdfundingStatus = CrowdfundingStatus.OPEN,
    ): Crowdfunding =
        crowdfundingRepository.save(
            Crowdfunding(
                postId = postId,
                creatorId = creatorId,
                goalAmount = goal,
                deadline = deadline,
                status = status,
            )
        )

    // ─── back ──────────────────────────────────────────────────────────────────

    @Test
    fun `back 는 후원자 잔액을 예치하고 currentAmount 와 backerCount 를 증가시킨다`() {
        val creator = newUser("creator-back")
        val backer = newUser("backer-back")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        val cf = saveCf(creator, newPost(creator), goal = 5_000)

        crowdfundingStore.back(backer, cf.id, 3_000)

        assertEquals(WalletStore.INITIAL_BALANCE - 3_000, balanceOf(backer))
        assertEquals(3_000, heldOf(backer))
        val updated = crowdfundingRepository.findById(cf.id).orElseThrow()
        assertEquals(3_000, updated.currentAmount)
        assertEquals(1L, crowdfundingStore.activeBackingCount(cf.id))
    }

    // ─── cancelBacking ─────────────────────────────────────────────────────────

    @Test
    fun `cancelBacking 은 예치금을 환불하고 CANCELLED 상태로 변경하고 currentAmount 를 감소시킨다`() {
        val creator = newUser("creator-cancel")
        val backer = newUser("backer-cancel")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        val cf = saveCf(creator, newPost(creator), goal = 5_000)

        crowdfundingStore.back(backer, cf.id, 3_000)
        crowdfundingStore.cancelBacking(backer, cf.id)

        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(backer))
        assertEquals(0, heldOf(backer))
        val updated = crowdfundingRepository.findById(cf.id).orElseThrow()
        assertEquals(0, updated.currentAmount)
        assertEquals(0L, crowdfundingStore.activeBackingCount(cf.id))
        val cancelled = backingRepository.findByCrowdfundingIdAndStatus(cf.id, BackingStatus.CANCELLED)
        assertEquals(1, cancelled.size)
    }

    // ─── closeCrowdfunding ─────────────────────────────────────────────────────

    @Test
    fun `closeCrowdfunding 목표 달성 시 각 후원자 예치금을 개설자에게 정산하고 SUCCEEDED 가 된다`() {
        val creator = newUser("creator-close")
        val backer1 = newUser("backer1-close")
        val backer2 = newUser("backer2-close")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer1)
        walletStore.getOrCreate(backer2)
        val cf = saveCf(creator, newPost(creator), goal = 5_000)

        crowdfundingStore.back(backer1, cf.id, 3_000)
        crowdfundingStore.back(backer2, cf.id, 2_000)

        crowdfundingStore.closeCrowdfunding(creator, cf.id)

        val settled = crowdfundingRepository.findById(cf.id).orElseThrow()
        assertEquals(CrowdfundingStatus.SUCCEEDED, settled.status)
        assertNotNull(settled.settledAt)

        // 개설자 잔액 = 초기값 + 5_000
        assertEquals(WalletStore.INITIAL_BALANCE + 5_000, balanceOf(creator))
        // 후원자 예치금 소멸
        assertEquals(0, heldOf(backer1))
        assertEquals(0, heldOf(backer2))
        // 후원 상태 SETTLED
        val settledBackings = backingRepository.findByCrowdfundingIdAndStatus(cf.id, BackingStatus.SETTLED)
        assertEquals(2, settledBackings.size)
    }

    @Test
    fun `closeCrowdfunding 목표 미달 시 예외가 발생한다`() {
        val creator = newUser("creator-fail-close")
        walletStore.getOrCreate(creator)
        val cf = saveCf(creator, newPost(creator), goal = 10_000)

        assertThrows(IllegalArgumentException::class.java) {
            crowdfundingStore.closeCrowdfunding(creator, cf.id)
        }
    }

    // ─── processDueDeadlines ───────────────────────────────────────────────────

    @Test
    fun `processDueDeadlines — 목표 달성 시 SUCCEEDED 정산`() {
        val creator = newUser("creator-due-ok")
        val backer = newUser("backer-due-ok")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        // deadline 은 미래로 생성해야 back() 가능 → back 후 deadline 을 과거로 당긴다
        val cf = saveCf(creator, newPost(creator), goal = 1_000)
        crowdfundingStore.back(backer, cf.id, 1_000)
        expireCf(cf.id)

        crowdfundingStore.processDueDeadlines(OffsetDateTime.now())

        val result = crowdfundingRepository.findById(cf.id).orElseThrow()
        assertEquals(CrowdfundingStatus.SUCCEEDED, result.status)
        assertNotNull(result.settledAt)
        assertEquals(WalletStore.INITIAL_BALANCE + 1_000, balanceOf(creator))
        assertEquals(0, heldOf(backer))
    }

    @Test
    fun `processDueDeadlines — 목표 미달 시 FAILED 환불`() {
        val creator = newUser("creator-due-fail")
        val backer = newUser("backer-due-fail")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        val cf = saveCf(creator, newPost(creator), goal = 10_000)
        crowdfundingStore.back(backer, cf.id, 1_000)
        expireCf(cf.id)

        crowdfundingStore.processDueDeadlines(OffsetDateTime.now())

        val result = crowdfundingRepository.findById(cf.id).orElseThrow()
        assertEquals(CrowdfundingStatus.FAILED, result.status)
        assertNull(result.settledAt)
        // 후원자 환불
        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(backer))
        assertEquals(0, heldOf(backer))
        val refunded = backingRepository.findByCrowdfundingIdAndStatus(cf.id, BackingStatus.REFUNDED)
        assertEquals(1, refunded.size)
    }

    // ─── Guards ────────────────────────────────────────────────────────────────

    @Test
    fun `개설자는 자신의 크라우드펀딩에 후원할 수 없다`() {
        val creator = newUser("creator-selfback")
        walletStore.getOrCreate(creator)
        val cf = saveCf(creator, newPost(creator), goal = 5_000)

        assertThrows(IllegalArgumentException::class.java) {
            crowdfundingStore.back(creator, cf.id, 1_000)
        }
    }

    @Test
    fun `이미 ACTIVE 후원이 있으면 중복 후원은 실패한다`() {
        val creator = newUser("creator-dup")
        val backer = newUser("backer-dup")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        val cf = saveCf(creator, newPost(creator), goal = 5_000)

        crowdfundingStore.back(backer, cf.id, 1_000)
        assertThrows(IllegalArgumentException::class.java) {
            crowdfundingStore.back(backer, cf.id, 1_000)
        }
    }

    @Test
    fun `OPEN 이 아닌 크라우드펀딩에는 후원할 수 없다`() {
        val creator = newUser("creator-notopen")
        val backer = newUser("backer-notopen")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        val cf = saveCf(creator, newPost(creator), goal = 10_000, status = CrowdfundingStatus.FAILED)

        assertThrows(IllegalArgumentException::class.java) {
            crowdfundingStore.back(backer, cf.id, 1_000)
        }
    }

    @Test
    fun `마감된 크라우드펀딩에는 후원할 수 없다`() {
        val creator = newUser("creator-expired")
        val backer = newUser("backer-expired")
        walletStore.getOrCreate(creator)
        walletStore.getOrCreate(backer)
        val cf = saveCf(creator, newPost(creator), goal = 5_000, deadline = pastDeadline())

        assertThrows(IllegalArgumentException::class.java) {
            crowdfundingStore.back(backer, cf.id, 1_000)
        }
    }
}
