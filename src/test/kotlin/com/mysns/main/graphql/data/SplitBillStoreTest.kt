package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.SplitParticipantInput
import com.mysns.main.graphql.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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
@Import(WalletStore::class, SplitBillStore::class)
class SplitBillStoreTest @Autowired constructor(
    private val splitBillStore: SplitBillStore,
    private val walletStore: WalletStore,
    private val walletRepository: WalletRepository,
    private val participantRepository: SplitParticipantRepository,
    private val billRepository: SplitBillRepository,
    private val userRepository: UserRepository,
) {
    private fun newUser(name: String): Long =
        userRepository.save(
            User(username = name, displayName = name, createdAt = OffsetDateTime.now(), passwordHash = "x"),
        ).id

    private fun balanceOf(id: Long) = walletRepository.findByOwnerId(id)!!.balance
    private fun heldOf(id: Long) = walletRepository.findByOwnerId(id)!!.held
    private fun part(billId: Long, userId: Long) =
        participantRepository.findBySplitBillIdAndUserId(billId, userId)!!
    private fun billStatus(id: Long) = billRepository.findById(id).orElseThrow().status

    private fun tag(vararg ids: Long) = ids.map { SplitParticipantInput(userId = it.toString(), percent = null) }

    @Test
    fun `create 는 균등분배하고 잔돈은 개설자가 흡수한다`() {
        val c = newUser("c"); val p1 = newUser("p1"); val p2 = newUser("p2")
        val bill = splitBillStore.create(c, 30_000, "점심", null, tag(p1, p2))

        // 100/3 → 개설자 34%, 나머지 33%씩
        assertEquals(34, part(bill.id, c).percent)
        assertEquals(33, part(bill.id, p1).percent)
        assertEquals(33, part(bill.id, p2).percent)
        val total = part(bill.id, c).shareAmount + part(bill.id, p1).shareAmount + part(bill.id, p2).shareAmount
        assertEquals(30_000, total)
        assertTrue(part(bill.id, c).isCreator)
        assertEquals(SplitParticipantStatus.ACCEPTED, part(bill.id, c).status)
        assertEquals(SplitParticipantStatus.PENDING, part(bill.id, p1).status)
    }

    @Test
    fun `create 명시비율 합이 100이 아니면 실패한다`() {
        val c = newUser("c"); val p1 = newUser("p1")
        assertThrows(IllegalArgumentException::class.java) {
            splitBillStore.create(c, 10_000, null, 50, listOf(SplitParticipantInput(p1.toString(), 40)))
        }
    }

    @Test
    fun `accept 는 몫을 예치하고 개설자에게 아직 지급하지 않는다`() {
        val c = newUser("c"); val p1 = newUser("p1"); val p2 = newUser("p2")
        walletStore.getOrCreate(c)
        val bill = splitBillStore.create(c, 30_000, null, null, tag(p1, p2))
        val p1Share = part(bill.id, p1).shareAmount

        splitBillStore.accept(p1, bill.id)

        assertEquals(WalletStore.INITIAL_BALANCE - p1Share, balanceOf(p1))
        assertEquals(p1Share, heldOf(p1))
        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(c))
        assertEquals(SplitParticipantStatus.ACCEPTED, part(bill.id, p1).status)
        assertEquals(SplitBillStatus.OPEN, billStatus(bill.id))
    }

    @Test
    fun `전원 수락하면 예치금이 개설자에게 일괄 지급되고 SETTLED 된다`() {
        val c = newUser("c"); val p1 = newUser("p1"); val p2 = newUser("p2")
        val bill = splitBillStore.create(c, 30_000, null, null, tag(p1, p2))
        val p1Share = part(bill.id, p1).shareAmount
        val p2Share = part(bill.id, p2).shareAmount

        splitBillStore.accept(p1, bill.id)
        splitBillStore.accept(p2, bill.id)

        assertEquals(SplitBillStatus.SETTLED, billStatus(bill.id))
        assertEquals(WalletStore.INITIAL_BALANCE + p1Share + p2Share, balanceOf(c))
        assertEquals(0, heldOf(p1))
        assertEquals(0, heldOf(p2))
        assertEquals(WalletStore.INITIAL_BALANCE - p1Share, balanceOf(p1))
        assertEquals(WalletStore.INITIAL_BALANCE - p2Share, balanceOf(p2))
    }

    @Test
    fun `거절한 참가자는 지급하지 않고 나머지만 정산된다`() {
        val c = newUser("c"); val p1 = newUser("p1"); val p2 = newUser("p2")
        walletStore.getOrCreate(p2)
        val bill = splitBillStore.create(c, 30_000, null, null, tag(p1, p2))
        val p1Share = part(bill.id, p1).shareAmount

        splitBillStore.accept(p1, bill.id)
        splitBillStore.decline(p2, bill.id)

        assertEquals(SplitBillStatus.SETTLED, billStatus(bill.id))
        assertEquals(WalletStore.INITIAL_BALANCE + p1Share, balanceOf(c))
        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(p2))
        assertEquals(0, heldOf(p2))
    }

    @Test
    fun `취소하면 수락한 참가자에게 예치금을 환불한다`() {
        val c = newUser("c"); val p1 = newUser("p1"); val p2 = newUser("p2")
        walletStore.getOrCreate(c)
        val bill = splitBillStore.create(c, 30_000, null, null, tag(p1, p2))

        splitBillStore.accept(p1, bill.id)
        splitBillStore.cancel(c, bill.id)

        assertEquals(SplitBillStatus.CANCELLED, billStatus(bill.id))
        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(p1))
        assertEquals(0, heldOf(p1))
        assertEquals(WalletStore.INITIAL_BALANCE, balanceOf(c))
    }

    @Test
    fun `개설자만 취소할 수 있고 종료된 정산은 취소 불가`() {
        val c = newUser("c"); val p1 = newUser("p1")
        val bill = splitBillStore.create(c, 10_000, null, null, tag(p1))

        assertThrows(IllegalArgumentException::class.java) { splitBillStore.cancel(p1, bill.id) }

        splitBillStore.accept(p1, bill.id)
        assertEquals(SplitBillStatus.SETTLED, billStatus(bill.id))
        assertThrows(IllegalArgumentException::class.java) { splitBillStore.cancel(c, bill.id) }
    }
}
