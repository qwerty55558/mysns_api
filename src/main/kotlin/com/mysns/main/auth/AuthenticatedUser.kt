package com.mysns.main.auth

import org.springframework.security.core.context.SecurityContextHolder

data class AuthenticatedUser(
    val userId: Long,
    val username: String,
)

fun currentUser(): AuthenticatedUser? =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser
