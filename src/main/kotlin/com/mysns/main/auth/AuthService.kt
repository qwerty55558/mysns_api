package com.mysns.main.auth

import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.AuthPayload
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    private val userStore: UserStore,
    private val passwordEncoder: PasswordEncoder,
    private val jwtProvider: JwtProvider,
) {

    fun login(username: String, rawPassword: String): AuthPayload {
        val creds = userStore.findCredentialsByUsername(username)
            ?: throw BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다")
        if (!passwordEncoder.matches(rawPassword, creds.passwordHash)) {
            throw BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다")
        }
        val user = userStore.findById(creds.userId)
            ?: throw IllegalStateException("user missing for credentials")
        val token = jwtProvider.issueAccess(user.id, user.username)
        return AuthPayload(accessToken = token, user = user)
    }

    fun register(username: String, rawPassword: String, displayName: String): AuthPayload {
        if (userStore.findByUsername(username) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다")
        }
        val hash = passwordEncoder.encode(rawPassword)
            ?: throw IllegalStateException("password encoding failed")
        val user = userStore.create(
            username = username,
            displayName = displayName,
            passwordHash = hash,
        )
        val token = jwtProvider.issueAccess(user.id, user.username)
        return AuthPayload(accessToken = token, user = user)
    }
}
