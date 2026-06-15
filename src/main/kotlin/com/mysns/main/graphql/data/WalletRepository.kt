package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface WalletRepository : JpaRepository<Wallet, Long> {
    fun findByOwnerId(ownerId: Long): Wallet?
}

interface WalletTransactionRepository : JpaRepository<WalletTransaction, Long> {
    fun findByOwnerIdOrderByCreatedAtDesc(ownerId: Long, pageable: Pageable): List<WalletTransaction>
}
