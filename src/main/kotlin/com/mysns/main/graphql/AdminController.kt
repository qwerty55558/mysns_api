package com.mysns.main.graphql

import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class AdminController {

    // 관리자 전용 — ROLE_ADMIN 권한 검증용 핑.
    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun adminPing(): String = "pong:admin"
}
