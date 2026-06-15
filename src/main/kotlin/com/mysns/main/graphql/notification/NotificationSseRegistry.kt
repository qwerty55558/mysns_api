package com.mysns.main.graphql.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class NotificationSseRegistry {

    private val log = LoggerFactory.getLogger(NotificationSseRegistry::class.java)
    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun register(userId: Long): SseEmitter {
        val emitter = SseEmitter(1_800_000L)
        val list = emitters.computeIfAbsent(userId) { CopyOnWriteArrayList() }
        list.add(emitter)

        val removeEmitter: (SseEmitter) -> Unit = { e ->
            emitters[userId]?.remove(e)
        }

        emitter.onCompletion { removeEmitter(emitter) }
        emitter.onTimeout {
            emitter.complete()
            removeEmitter(emitter)
        }
        emitter.onError { removeEmitter(emitter) }

        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"))
        } catch (ex: Exception) {
            log.debug("Failed to send ready event to user $userId", ex)
        }

        return emitter
    }

    fun push(userId: Long, payload: Any) {
        val list = emitters[userId] ?: return
        val dead = mutableListOf<SseEmitter>()
        for (emitter in list) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(payload))
            } catch (ex: Exception) {
                dead.add(emitter)
                try {
                    emitter.completeWithError(ex)
                } catch (_: Exception) {
                }
            }
        }
        if (dead.isNotEmpty()) list.removeAll(dead)
    }

    @Scheduled(fixedRate = 20_000)
    fun heartbeat() {
        for ((userId, list) in emitters) {
            val dead = mutableListOf<SseEmitter>()
            for (emitter in list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"))
                } catch (ex: Exception) {
                    dead.add(emitter)
                    try {
                        emitter.completeWithError(ex)
                    } catch (_: Exception) {
                    }
                }
            }
            if (dead.isNotEmpty()) list.removeAll(dead)
        }
    }
}
