package com.mysns.main.graphql.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateCommentInput(
    @field:NotBlank(message = "댓글 내용을 입력해주세요")
    @field:Size(min = 1, max = 500, message = "댓글은 1~500자여야 합니다")
    val content: String,
)
