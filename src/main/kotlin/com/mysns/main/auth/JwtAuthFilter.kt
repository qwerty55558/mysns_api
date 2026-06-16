package com.mysns.main.auth

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(private val jwtProvider: JwtProvider) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            // Bearer header path — existing behavior unchanged (401 on parse failure)
            val token = header.removePrefix("Bearer ").trim()
            try {
                val parsed = jwtProvider.parseAccess(token)
                val principal = AuthenticatedUser(parsed.userId, parsed.username)
                val auth = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                SecurityContextHolder.getContext().authentication = auth
                filterChain.doFilter(request, response)
            } catch (e: ExpiredJwtException) {
                log.debug("expired JWT: {}", e.message)
                writeUnauthorized(response, "토큰이 만료되었습니다", "TOKEN_EXPIRED")
            } catch (e: JwtException) {
                log.debug("invalid JWT: {}", e.message)
                writeUnauthorized(response, "유효하지 않은 토큰입니다", "TOKEN_INVALID")
            }
            return
        }

        // Query-param fallback for SSE clients (EventSource cannot send Authorization headers)
        val queryToken = request.getParameter("token")
        if (queryToken != null) {
            try {
                val parsed = jwtProvider.parseAccess(queryToken)
                val principal = AuthenticatedUser(parsed.userId, parsed.username)
                val auth = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER")),
                )
                SecurityContextHolder.getContext().authentication = auth
            } catch (e: Exception) {
                log.debug("query-param token parse failed, continuing unauthenticated: {}", e.message)
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(response: HttpServletResponse, message: String, code: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json;charset=UTF-8"
        val body = """{"errors":[{"message":"$message","extensions":{"classification":"UNAUTHORIZED","code":"$code"}}],"data":null}"""
        response.writer.write(body)
        response.writer.flush()
    }

    companion object {
        private val log = LoggerFactory.getLogger(JwtAuthFilter::class.java)
    }
}
