package com.mysns.main.graphql.data

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class FollowRequestStore(
    private val repository: FollowRequestRepository,
) {
    @Transactional
    fun create(requesterId: Long, targetId: Long): Boolean =
        repository.insertIfAbsent(requesterId, targetId) > 0

    fun findById(id: Long): FollowRequest? = repository.findById(id).orElse(null)

    @Transactional
    fun delete(request: FollowRequest) = repository.delete(request)

    fun exists(requesterId: Long, targetId: Long): Boolean =
        repository.existsByRequesterIdAndTargetId(requesterId, targetId)

    /** "내가 이 target들에게 보낸 요청"이 있는 ID 집합 — viewerHasRequestedFollow BatchMapping 용 */
    fun requesterPendingToTargets(requesterId: Long, targetIds: Collection<Long>): Set<Long> {
        if (targetIds.isEmpty()) return emptySet()
        return repository.findByRequesterIdAndTargetIdIn(requesterId, targetIds)
            .mapTo(HashSet()) { it.targetId }
    }

    fun incoming(targetId: Long, limit: Int, offset: Int): List<FollowRequest> {
        val page = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
        return repository.findByTargetIdOrderByCreatedAtDesc(targetId, page)
    }

    fun outgoing(requesterId: Long, limit: Int, offset: Int): List<FollowRequest> {
        val page = PageRequest.of(offset / limit.coerceAtLeast(1), limit.coerceAtLeast(1))
        return repository.findByRequesterIdOrderByCreatedAtDesc(requesterId, page)
    }

    fun incomingCount(targetId: Long): Int = repository.countByTargetId(targetId)
}
