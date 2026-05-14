package com.mysns.main.auth

import com.mysns.main.graphql.model.AuthPayload
import com.mysns.main.graphql.stub.InMemoryUserStore
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userStore: InMemoryUserStore,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
) {

    fun login(username: String, rawPassword: String): AuthPayload {
        val creds = userStore.findCredentialsByUsername(username)
            ?: throw BadCredentialsException("invalid username or password")
        if (!passwordEncoder.matches(rawPassword, creds.passwordHash)) {
            throw BadCredentialsException("invalid username or password")
        }
        val user = userStore.findById(creds.userId)
            ?: throw IllegalStateException("user missing for credentials")
        val token = jwtProvider.issueAccess(user.id, user.username)
        return AuthPayload(accessToken = token, user = user)
    }
}
