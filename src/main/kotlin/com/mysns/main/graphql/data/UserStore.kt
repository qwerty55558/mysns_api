package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.User
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
@Transactional(readOnly = true)
class UserStore(
    private val userRepository: UserRepository,
) {
    fun findById(id: Long): User? = userRepository.findById(id).orElse(null)

    fun findAllById(ids: Collection<Long>): List<User> =
        if (ids.isEmpty()) emptyList() else userRepository.findAllById(ids)

    fun findByUsername(username: String): User? = userRepository.findByUsername(username)

    fun first(): User? = userRepository.findFirstByOrderByIdAsc()

    fun findCredentialsByUsername(username: String): UserCredentials? {
        val user = userRepository.findByUsername(username) ?: return null
        return UserCredentials(
            userId = user.id,
            username = user.username,
            passwordHash = user.passwordHash,
        )
    }

    @Transactional
    fun create(username: String, displayName: String, passwordHash: String): User =
        userRepository.save(
            User(
                username = username,
                displayName = displayName,
                createdAt = OffsetDateTime.now(),
                passwordHash = passwordHash,
            )
        )
}
