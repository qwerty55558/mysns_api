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

    /** 프로필 부분 업데이트. null은 변경 없음. */
    @Transactional
    fun update(
        userId: Long,
        displayName: String? = null,
        bio: String? = null,
        privateAccount: Boolean? = null,
    ): User {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalStateException("user not found: $userId")
        }
        if (displayName != null) user.displayName = displayName
        if (bio != null) user.bio = bio.ifBlank { null }
        if (privateAccount != null) user.privateAccount = privateAccount
        return userRepository.save(user)
    }
}
