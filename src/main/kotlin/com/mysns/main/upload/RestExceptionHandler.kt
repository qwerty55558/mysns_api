package com.mysns.main.upload

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@RestControllerAdvice(basePackages = ["com.mysns.main.upload"])
class RestExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handle(ex: ResponseStatusException): ResponseEntity<ErrorBody> {
        val body = ErrorBody(
            timestamp = OffsetDateTime.now().toString(),
            status = ex.statusCode.value(),
            error = ex.statusCode.toString(),
            message = ex.reason ?: ex.statusCode.toString(),
        )
        return ResponseEntity.status(ex.statusCode).body(body)
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(ex: MaxUploadSizeExceededException): ResponseEntity<ErrorBody> {
        val body = ErrorBody(
            timestamp = OffsetDateTime.now().toString(),
            status = 413,
            error = "Payload Too Large",
            message = "업로드 가능한 최대 크기를 초과했습니다",
        )
        return ResponseEntity.status(413).body(body)
    }

    data class ErrorBody(
        val timestamp: String,
        val status: Int,
        val error: String,
        val message: String,
    )
}
