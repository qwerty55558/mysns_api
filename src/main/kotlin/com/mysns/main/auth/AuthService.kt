package com.mysns.main.auth

import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.AuthPayload
import com.mysns.main.graphql.model.User
import io.jsonwebtoken.JwtException
import io.micrometer.core.instrument.MeterRegistry
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
    private val meterRegistry: MeterRegistry,
) {

    fun login(username: String, rawPassword: String): AuthPayload {
        val creds = userStore.findCredentialsByUsername(username)
            ?: run {
                meterRegistry.counter("mysns.login", "result", "bad_credentials").increment()
                throw BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다")
            }
        if (!passwordEncoder.matches(rawPassword, creds.passwordHash)) {
            meterRegistry.counter("mysns.login", "result", "bad_credentials").increment()
            throw BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다")
        }
        val user = userStore.findById(creds.userId)
            ?: throw IllegalStateException("user missing for credentials")
        meterRegistry.counter("mysns.login", "result", "success").increment()
        return buildAuthPayload(user)
    }

    fun register(username: String, rawPassword: String, displayName: String): AuthPayload {
        if (userStore.findByUsername(username) != null) {
            meterRegistry.counter("mysns.signup", "result", "conflict").increment()
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다")
        }
        val hash = passwordEncoder.encode(rawPassword)
            ?: throw IllegalStateException("password encoding failed")
        val user = userStore.create(
            username = username,
            displayName = displayName,
            passwordHash = hash,
        )
        meterRegistry.counter("mysns.signup", "result", "success").increment()
        return buildAuthPayload(user)
    }

    fun refresh(refreshToken: String): AuthPayload {
        val parsed = try {
            jwtProvider.parseRefresh(refreshToken)
        } catch (e: JwtException) {
            meterRegistry.counter("mysns.refresh", "result", "invalid").increment()
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, e.message ?: "리프레시 토큰이 유효하지 않습니다")
        }
        val user = userStore.findById(parsed.userId)
            ?: run {
                meterRegistry.counter("mysns.refresh", "result", "user_missing").increment()
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다")
            }
        meterRegistry.counter("mysns.refresh", "result", "success").increment()
        return buildAuthPayload(user)
    }

    private fun buildAuthPayload(user: User): AuthPayload {
        val access = jwtProvider.issueAccess(user.id, user.username)
        val refresh = jwtProvider.issueRefresh(user.id, user.username)
        return AuthPayload(
            accessToken = access.token,
            refreshToken = refresh.token,
            accessTokenExpiresAt = access.expiresAt,
            user = user,
        )
    }
}
