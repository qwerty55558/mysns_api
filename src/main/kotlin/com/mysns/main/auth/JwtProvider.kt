package com.mysns.main.auth

import com.mysns.main.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(private val props: JwtProperties) {

    private val key: SecretKey = Keys.hmacShaKeyFor(props.secret.toByteArray())

    fun issueAccess(userId: Long, username: String): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("username", username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(props.accessTtl)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): ParsedToken {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return ParsedToken(
            userId = claims.subject.toLong(),
            username = claims["username", String::class.java],
        )
    }

    data class ParsedToken(val userId: Long, val username: String)
}
