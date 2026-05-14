package com.mysns.main.auth

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder

data class AuthenticatedUser(
    val userId: Long,
    val username: String,
)

fun currentUser(): AuthenticatedUser? =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedUser

fun requireCurrentUser(): AuthenticatedUser =
    currentUser() ?: throw AccessDeniedException("authentication required")
