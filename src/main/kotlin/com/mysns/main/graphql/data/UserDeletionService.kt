package com.mysns.main.graphql.data

import com.mysns.main.upload.UploadCommitter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 셀프 탈퇴(hard-delete) 서비스.
 * DB의 ON DELETE CASCADE FK가 자식 행을 모두 제거하지만,
 * 생존 행의 역정규화 카운터(followerCount 등)는 CASCADE 대상이 아니므로 수동 보정한다.
 */
@Component
class UserDeletionService(
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val splitBillRepository: SplitBillRepository,
    private val splitParticipantRepository: SplitParticipantRepository,
    private val followRepository: FollowRepository,
    private val postRepository: PostRepository,
    private val likeRepository: LikeRepository,
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val uploadCommitter: UploadCommitter,
    private val crowdfundingRepository: CrowdfundingRepository,
    private val backingRepository: BackingRepository,
) {

    @Transactional
    fun deleteMe(userId: Long): Boolean {
        // ── 1. 금융 가드 ──────────────────────────────────────────────────────

        // 지갑 잔액 > 0 이면 탈퇴 차단
        val wallet = walletRepository.findByOwnerId(userId)
        if (wallet != null && wallet.balance > 0) {
            throw IllegalStateException(
                "잔액(${wallet.balance}원)이 남아 있어 탈퇴할 수 없습니다. 먼저 출금하세요.",
            )
        }

        // 내가 개설자인 OPEN 정산이 있으면 탈퇴 차단
        val hasOpenBill = splitBillRepository.existsByCreatorIdAndStatusNotIn(
            userId,
            listOf(SplitBillStatus.SETTLED, SplitBillStatus.CANCELLED),
        )
        require(!hasOpenBill) { "진행 중인 정산(N빵)이 있어 탈퇴할 수 없습니다." }

        // 내가 참가자로서 PENDING 상태(예치 중)인 정산이 있으면 탈퇴 차단
        val hasPendingParticipation = splitParticipantRepository
            .existsByUserIdAndStatusAndIsCreatorFalse(userId, SplitParticipantStatus.PENDING)
        require(!hasPendingParticipation) { "예치 중인 정산(N빵) 참가 건이 있어 탈퇴할 수 없습니다." }

        // 내가 개설자인 OPEN 크라우드펀딩에 예치금이 있으면 탈퇴 차단
        val hasOpenCrowdfunding = crowdfundingRepository
            .existsByCreatorIdAndStatusAndCurrentAmountGreaterThan(userId, CrowdfundingStatus.OPEN, 0)
        require(!hasOpenCrowdfunding) { "진행 중인 크라우드펀딩(예치된 후원)이 있어 탈퇴할 수 없습니다." }

        // 내가 후원자로서 ACTIVE 상태(예치 중)인 후원이 있으면 탈퇴 차단
        val hasActiveBacking = backingRepository.existsByUserIdAndStatus(userId, BackingStatus.ACTIVE)
        require(!hasActiveBacking) { "예치 중인 크라우드펀딩 후원이 있어 탈퇴할 수 없습니다." }

        // ── 2. 카운터 보정 ────────────────────────────────────────────────────

        // 내가 팔로우하는 유저들의 followerCount -1
        followRepository.decrementFollowerCountForFollowees(userId)

        // 나를 팔로우하는 유저들의 followingCount -1
        followRepository.decrementFollowingCountForFollowers(userId)

        // 내가 좋아요한 포스트들의 likeCount -1
        likeRepository.decrementLikeCountForPostsLikedByUser(userId)

        // 내가 좋아요한 댓글들의 likeCount -1
        commentLikeRepository.decrementLikeCountForCommentsLikedByUser(userId)

        // 내가 작성한 댓글들의 포스트 commentCount 보정
        commentRepository.decrementCommentCountForPostsByAuthor(userId)

        // ── 3. 게시글 ID 수집 (파일 삭제용, 유저 삭제 전에) ─────────────────

        val postIds = postRepository.findIdsByAuthorId(userId)

        // ── 4. 유저 행 삭제 (CASCADE로 모든 자식 행 제거) ────────────────────

        userRepository.deleteById(userId)

        // ── 5. 파일 삭제 ──────────────────────────────────────────────────────

        uploadCommitter.deleteUserAvatars(userId)

        for (postId in postIds) {
            uploadCommitter.deletePostDir(postId)
        }

        return true
    }
}
