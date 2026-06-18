package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Comment
import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import com.mysns.main.upload.UploadCommitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import jakarta.persistence.EntityManager

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(UserDeletionServiceTest.MockConfig::class, UserDeletionService::class)
class UserDeletionServiceTest @Autowired constructor(
    private val userDeletionService: UserDeletionService,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val walletRepository: WalletRepository,
    private val followRepository: FollowRepository,
    private val em: EntityManager,
) {

    /** @TestConfiguration — Spring Boot 슬라이스 테스트에서 추가 빈을 등록하는 올바른 방법. */
    @TestConfiguration
    class MockConfig {
        @Bean
        fun uploadCommitter(): UploadCommitter = mock(UploadCommitter::class.java)
    }

    private fun newUser(name: String): User =
        userRepository.save(
            User(
                username = name,
                displayName = name,
                createdAt = OffsetDateTime.now(),
                passwordHash = "x",
            ),
        )

    private fun newPost(authorId: Long): Post =
        postRepository.save(
            Post(
                content = "test post",
                authorId = authorId,
                createdAt = OffsetDateTime.now(),
            ),
        )

    private fun newComment(authorId: Long, postId: Long): Comment =
        commentRepository.save(
            Comment(
                content = "test comment",
                authorId = authorId,
                postId = postId,
                createdAt = OffsetDateTime.now(),
            ),
        )

    /** 모든 pending 변경을 DB에 내려보내고 1차 캐시를 비워 이후 조회가 DB 실제 상태를 반영하게 한다. */
    private fun flushAndClear() {
        em.flush()
        em.clear()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 정상 탈퇴 시나리오
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `userA 탈퇴 시 자신 행이 삭제되고 userB 카운터가 보정된다`() {
        // arrange
        val userA = newUser("userA_del_happy")
        val userB = newUser("userB_del_happy")

        // userA follows userB: userB.followerCount=1, userA.followingCount=1
        userB.followerCount = 1
        userA.followingCount = 1
        userRepository.saveAll(listOf(userA, userB))

        // follow 행 저장
        followRepository.insertIfAbsent(userA.id, userB.id)

        // userB가 포스트 작성, likeCount=1, commentCount=1
        val post = newPost(userB.id)
        post.likeCount = 1
        post.commentCount = 1
        postRepository.save(post)

        // userA가 댓글 작성
        val comment = newComment(userA.id, post.id)
        comment.likeCount = 1
        commentRepository.save(comment)

        // userA가 포스트 좋아요
        likeRepository.insertIfAbsent(userA.id, post.id)

        // userA가 댓글 좋아요
        commentLikeRepository.insertIfAbsent(userA.id, comment.id)

        // 모든 변경을 먼저 flush
        flushAndClear()

        val userAId = userA.id
        val userBId = userB.id
        val postId = post.id
        val commentId = comment.id

        // act
        val result = userDeletionService.deleteMe(userAId)

        // deleteMe 내부의 변경(deleteById 포함)을 DB에 내리고 캐시 비우기
        flushAndClear()

        // assert
        assertTrue(result)

        // userA 행이 사라져야 함
        assertFalse(userRepository.findById(userAId).isPresent, "userA 행이 삭제되어야 한다")

        // userA의 댓글이 CASCADE FK (comments.author_id → users)로 삭제되어야 함
        assertFalse(commentRepository.findById(commentId).isPresent, "userA의 댓글이 cascade 삭제되어야 한다")

        // userA의 post 좋아요가 CASCADE FK (post_likes.user_id → users)로 삭제되어야 함
        assertFalse(likeRepository.existsByUserIdAndPostId(userAId, postId), "userA의 post like가 cascade 삭제되어야 한다")

        // userA의 comment 좋아요가 CASCADE FK (comment_likes.user_id → users)로 삭제되어야 함
        assertFalse(commentLikeRepository.existsByUserIdAndCommentId(userAId, commentId), "userA의 comment like가 cascade 삭제되어야 한다")

        // userB.followerCount 카운터 보정 확인 (userA가 팔로우했으므로 1→0)
        val updatedUserB = userRepository.findById(userBId).get()
        assertEquals(0, updatedUserB.followerCount, "userB.followerCount가 0으로 감소해야 한다")

        // post.likeCount / commentCount 보정 확인 (userA가 like·comment 했으므로 각 1→0)
        val updatedPost = postRepository.findById(postId).get()
        assertEquals(0, updatedPost.likeCount, "post.likeCount가 0으로 감소해야 한다")
        assertEquals(0, updatedPost.commentCount, "post.commentCount가 0으로 감소해야 한다")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 가드 테스트 — 잔액 있으면 탈퇴 차단
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `지갑 잔액이 있으면 탈퇴가 거부된다`() {
        val userA = newUser("userA_del_guard")
        walletRepository.save(Wallet(ownerId = userA.id, balance = 5000))
        flushAndClear()

        val userAId = userA.id

        val ex = assertThrows(IllegalStateException::class.java) {
            userDeletionService.deleteMe(userAId)
        }
        assertTrue(ex.message!!.contains("잔액"), "예외 메시지에 '잔액' 포함 필요")

        // 유저가 삭제되지 않아야 함
        flushAndClear()
        assertTrue(userRepository.findById(userAId).isPresent, "탈퇴 차단 후 유저 행이 남아 있어야 한다")
    }
}
