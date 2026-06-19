package com.mysns.main.auth

import com.mysns.main.config.JwtProperties
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(private val props: JwtProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.secret.toByteArray())

    fun issueAccess(userId: Long, username: String, role: String): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plus(props.accessTtl)
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("role", role)
            .claim("type", TYPE_ACCESS)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return IssuedToken(token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
    }

    fun issueRefresh(userId: Long, username: String): IssuedToken {
        val now = Instant.now()
        val expiresAt = now.plus(props.refreshTtl)
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .claim("type", TYPE_REFRESH)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return IssuedToken(token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
    }

    fun parseAccess(token: String): ParsedToken = parse(token, TYPE_ACCESS)

    fun parseRefresh(token: String): ParsedToken = parse(token, TYPE_REFRESH)

    private fun parse(token: String, expectedType: String): ParsedToken {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        // 구버전 토큰(type 클레임 없음)은 access로 간주 — 점진 마이그레이션
        val type = claims["type", String::class.java] ?: TYPE_ACCESS
        if (type != expectedType) {
            throw JwtException("토큰 타입 불일치: expected=$expectedType actual=$type")
        }
        // 구버전 토큰(role 클레임 없음)은 USER로 간주 — 하위 호환
        val role = claims["role", String::class.java] ?: "USER"
        return ParsedToken(
            userId = claims.subject.toLong(),
            username = claims["username", String::class.java],
            role = role,
        )
    }

    data class ParsedToken(val userId: Long, val username: String, val role: String = "USER")
    data class IssuedToken(val token: String, val expiresAt: OffsetDateTime)

    companion object {
        private const val TYPE_ACCESS = "access"
        private const val TYPE_REFRESH = "refresh"
    }
}
