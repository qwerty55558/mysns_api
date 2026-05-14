package com.mysns.main.graphql.stub

import com.mysns.main.graphql.model.User
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryUserStore {

    private val users: MutableMap<Long, User> = ConcurrentHashMap()

    init {
        val base = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        listOf(
            User(1, "alice", "Alice", "GraphQL fan", base),
            User(2, "bob", "Bob", null, base.plusDays(1)),
            User(3, "charlie", "Charlie", "lurker", base.plusDays(2)),
        ).forEach { users[it.id] = it }
    }

    fun findById(id: Long): User? = users[id]

    fun findAllById(ids: Collection<Long>): List<User> =
        ids.mapNotNull { users[it] }

    fun first(): User? = users.values.firstOrNull()
}
