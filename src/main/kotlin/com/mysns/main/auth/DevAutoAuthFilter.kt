package com.mysns.main.auth

import com.mysns.main.graphql.data.UserStore
import com.mysns.main.graphql.model.UserRole
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class DevAutoAuthFilter(private val userStore: UserStore) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val hasAuthHeader = request.getHeader("Authorization") != null
        if (!hasAuthHeader && SecurityContextHolder.getContext().authentication == null) {
            val devUser = userStore.first()
            if (devUser != null) {
                val principal = AuthenticatedUser(devUser.id, devUser.username)
                val authorities = if (devUser.role == UserRole.ADMIN)
                    listOf(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("ROLE_USER"))
                else
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                val auth = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities,
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
