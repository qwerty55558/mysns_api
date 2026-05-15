package com.mysns.main.graphql.model

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RegisterInput(
    @field:Pattern(
        regexp = "^[A-Za-z0-9_]{3,32}$",
        message = "아이디는 영문/숫자/_ 조합으로 3~32자여야 합니다",
    )
    val username: String,

    @field:Size(min = 6, max = 128, message = "비밀번호는 6자 이상이어야 합니다")
    val password: String,

    @field:Size(min = 1, max = 32, message = "이름은 1~32자여야 합니다")
    val displayName: String,
)
