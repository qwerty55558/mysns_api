package com.mysns.main.graphql

import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.SplitBill
import com.mysns.main.graphql.data.SplitBillStore
import com.mysns.main.graphql.data.SplitParticipant
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.CreateSplitInput
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class SplitBillController(
    private val splitBillStore: SplitBillStore,
    private val userStore: UserStore,
) {

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun splitBill(@Argument id: String): SplitBill? =
        splitBillStore.findBillVisibleTo(id.toLong(), requireCurrentUser().userId)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun mySplitBills(@Argument limit: Int, @Argument offset: Int): List<SplitBill> =
        splitBillStore.createdBills(requireCurrentUser().userId, limit, offset)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun settlementHistory(@Argument limit: Int, @Argument offset: Int): List<SplitBill> =
        splitBillStore.history(requireCurrentUser().userId, limit, offset)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun pendingSplitRequests(@Argument limit: Int, @Argument offset: Int): List<SplitParticipant> =
        splitBillStore.pendingRequests(requireCurrentUser().userId, limit, offset)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun createSplit(@Argument input: CreateSplitInput): SplitBill =
        splitBillStore.create(
            creatorId = requireCurrentUser().userId,
            totalAmount = input.totalAmount,
            memo = input.memo,
            creatorPercent = input.creatorPercent,
            participants = input.participants,
        )

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun acceptSplit(@Argument splitBillId: String): SplitParticipant =
        splitBillStore.accept(requireCurrentUser().userId, splitBillId.toLong())

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun declineSplit(@Argument splitBillId: String): SplitParticipant =
        splitBillStore.decline(requireCurrentUser().userId, splitBillId.toLong())

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun cancelSplit(@Argument splitBillId: String): SplitBill =
        splitBillStore.cancel(requireCurrentUser().userId, splitBillId.toLong())

    @SchemaMapping(typeName = "SplitBill", field = "creator")
    fun splitBillCreator(bill: SplitBill): User = userStore.findById(bill.creatorId)!!

    @BatchMapping(typeName = "SplitBill", field = "participants")
    fun splitBillParticipants(bills: List<SplitBill>): Map<SplitBill, List<SplitParticipant>> {
        val byBill = splitBillStore.participantsByBillIds(bills.map { it.id }).groupBy { it.splitBillId }
        return bills.associateWith { byBill[it.id] ?: emptyList() }
    }

    @BatchMapping(typeName = "SplitParticipant", field = "user")
    fun splitParticipantUser(participants: List<SplitParticipant>): Map<SplitParticipant, User> {
        val byId = userStore.findAllById(participants.map { it.userId }.toSet()).associateBy { it.id }
        val result = HashMap<SplitParticipant, User>()
        for (p in participants) {
            byId[p.userId]?.let { result[p] = it }
        }
        return result
    }

    @BatchMapping(typeName = "SplitParticipant", field = "bill")
    fun splitParticipantBill(participants: List<SplitParticipant>): Map<SplitParticipant, SplitBill> {
        val byId = splitBillStore.findBills(participants.map { it.splitBillId }.toSet()).associateBy { it.id }
        val result = HashMap<SplitParticipant, SplitBill>()
        for (p in participants) {
            byId[p.splitBillId]?.let { result[p] = it }
        }
        return result
    }
}
