package com.mysns.main.graphql.notification

import com.mysns.main.auth.requireCurrentUser
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter


@RestController
class NotificationSseController(
    private val registry: NotificationSseRegistry,
) {
    @GetMapping("/notifications/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(): SseEmitter = registry.register(requireCurrentUser().userId)

    @GetMapping("/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(): SseEmitter = registry.register(requireCurrentUser().userId)
}
