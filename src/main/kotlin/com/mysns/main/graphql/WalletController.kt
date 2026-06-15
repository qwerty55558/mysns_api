package com.mysns.main.graphql

import com.mysns.main.auth.requireCurrentUser
import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.data.Wallet
import com.mysns.main.graphql.data.WalletStore
import com.mysns.main.graphql.data.WalletTransaction
import com.mysns.main.graphql.model.TransferInput
import com.mysns.main.graphql.model.User
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class WalletController(
    private val walletStore: WalletStore,
    private val userStore: UserStore,
) {

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun myWallet(): Wallet = walletStore.getOrCreate(requireCurrentUser().userId)

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun walletTransactions(@Argument limit: Int, @Argument offset: Int): List<WalletTransaction> =
        walletStore.transactions(requireCurrentUser().userId, limit, offset)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun topUpWallet(@Argument amount: Int, @Argument memo: String?): Wallet =
        walletStore.topUp(requireCurrentUser().userId, amount, memo)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun withdrawWallet(@Argument amount: Int, @Argument memo: String?): Wallet =
        walletStore.withdraw(requireCurrentUser().userId, amount, memo)

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    fun transfer(@Argument input: TransferInput): WalletTransaction =
        walletStore.transfer(
            senderId = requireCurrentUser().userId,
            recipientId = input.recipientId.toLong(),
            amount = input.amount,
            memo = input.memo,
        )

    @SchemaMapping(typeName = "Wallet", field = "owner")
    fun walletOwner(wallet: Wallet): User = userStore.findById(wallet.ownerId)!!

    @BatchMapping(typeName = "WalletTransaction", field = "counterparty")
    fun counterparty(txs: List<WalletTransaction>): Map<WalletTransaction, User> {
        val ids = txs.mapNotNull { it.counterpartyId }.toSet()
        if (ids.isEmpty()) return emptyMap()
        val byId = userStore.findAllById(ids).associateBy { it.id }
        val result = HashMap<WalletTransaction, User>()
        for (tx in txs) {
            val cid = tx.counterpartyId ?: continue
            byId[cid]?.let { result[tx] = it }
        }
        return result
    }
}
