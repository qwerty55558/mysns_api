package com.mysns.main.auth

import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.AuthPayload
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

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
}
