package com.mysns.main.graphql.data

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ConversationRepository : JpaRepository<Conversation, Long> {

    fun findByUser1IdAndUser2Id(user1Id: Long, user2Id: Long): Conversation?

    @Query(
        "SELECT c FROM Conversation c " +
            "WHERE c.user1Id = :userId OR c.user2Id = :userId " +
            "ORDER BY c.updatedAt DESC",
    )
    fun findForUser(@Param("userId") userId: Long, pageable: Pageable): List<Conversation>
}
