package com.mysns.main.graphql.notification

import com.mysns.main.auth.currentUser
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter


@RestController
class NotificationSseController(
    private val registry: NotificationSseRegistry,
) {
    @GetMapping("/notifications/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter = register()

    @GetMapping("/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(): SseEmitter = register()

    // 경로는 permitAll(SecurityConfig) — 인증은 여기서 동기적으로 처리해 미인증/만료를 깔끔한 401 로 반환한다.
    // AuthorizationFilter 가 async 디스패치에서 AccessDeniedException 을 던져 ERROR 스택으로 도배되던 문제 회피.
    private fun register(): SseEmitter {
        val user = currentUser()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required")
        return registry.register(user.userId)
    }
}
