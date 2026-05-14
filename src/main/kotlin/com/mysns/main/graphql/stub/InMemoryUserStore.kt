package com.mysns.main.graphql.stub

import com.mysns.main.graphql.model.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryUserStore(passwordEncoder: PasswordEncoder) {

    private val users: MutableMap<Long, User> = ConcurrentHashMap()
    private val credentialsByUsername: MutableMap<String, UserCredentials> = ConcurrentHashMap()

    init {
        val base = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        val dummyHash: String = passwordEncoder.encode("password")!!
        listOf(
            User(1, "alice", "Alice", "GraphQL fan", base),
            User(2, "bob", "Bob", null, base.plusDays(1)),
            User(3, "charlie", "Charlie", "lurker", base.plusDays(2)),
        ).forEach {
            users[it.id] = it
            credentialsByUsername[it.username] = UserCredentials(
                userId = it.id,
                username = it.username,
                passwordHash = dummyHash,
            )
        }
    }

    fun findById(id: Long): User? = users[id]

    fun findAllById(ids: Collection<Long>): List<User> =
        ids.mapNotNull { users[it] }

    fun first(): User? = users.values.firstOrNull()

    fun findCredentialsByUsername(username: String): UserCredentials? =
        credentialsByUsername[username]
}
