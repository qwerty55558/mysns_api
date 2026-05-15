package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.PostCategory
import com.mysns.main.graphql.model.User
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class DataInitializer(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val followRepository: FollowRepository,
    private val likeRepository: LikeRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    private val log = LoggerFactory.getLogger(DataInitializer::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun initialize() {
        if (userRepository.count() > 0) {
            log.info("data init skipped — users already present ({})", userRepository.count())
            return
        }
        log.info("data init — empty DB, seeding minimal demo data")

        val users = seedUsers()
        val posts = seedPosts(users)

        log.info(
            "data init done — users={}, posts={}, follows={}, likes={}, bookmarks={}, comments={}",
            userRepository.count(),
            postRepository.count(),
            followRepository.count(),
            likeRepository.count(),
            bookmarkRepository.count(),
            commentRepository.count(),
        )
        @Suppress("UNUSED_VARIABLE") val _kept = posts
    }

    private fun seedUsers(): Map<String, User> {
        val base = OffsetDateTime.parse("2026-01-01T00:00:00Z")
        val hash: String = passwordEncoder.encode("password")!!
        val alice = userRepository.save(
            User(
                username = "alice",
                displayName = "Alice",
                bio = "GraphQL fan",
                createdAt = base,
                avatarUrl = "https://i.pravatar.cc/150?u=alice",
                passwordHash = hash,
            )
        )
        val bob = userRepository.save(
            User(
                username = "bob",
                displayName = "Bob",
                bio = null,
                createdAt = base.plusDays(1),
                avatarUrl = "https://i.pravatar.cc/150?u=bob",
                passwordHash = hash,
            )
        )
        val charlie = userRepository.save(
            User(
                username = "charlie",
                displayName = "Charlie",
                bio = "lurker",
                createdAt = base.plusDays(2),
                avatarUrl = "https://i.pravatar.cc/150?u=charlie",
                passwordHash = hash,
            )
        )
        return mapOf("alice" to alice, "bob" to bob, "charlie" to charlie)
    }

    private fun seedPosts(users: Map<String, User>): List<Post> {
        val base = OffsetDateTime.parse("2026-02-01T00:00:00Z")
        val alice = users.getValue("alice")
        val bob = users.getValue("bob")
        val charlie = users.getValue("charlie")
        return listOf(
            postRepository.save(
                Post(
                    content = "오늘 점심: 김치찌개 정식",
                    authorId = alice.id,
                    createdAt = base,
                    imageUrls = mutableListOf("https://picsum.photos/seed/post1/600/600"),
                    amount = 9000,
                    category = PostCategory.FOOD,
                    tag = "백반집",
                )
            ),
            postRepository.save(
                Post(
                    content = "스벅 모카프라푸치노 ☕",
                    authorId = alice.id,
                    createdAt = base.plusHours(1),
                    amount = 6300,
                    category = PostCategory.CAFE,
                    tag = "스타벅스 강남점",
                )
            ),
            postRepository.save(
                Post(
                    content = "Bob here — 출퇴근 지하철",
                    authorId = bob.id,
                    createdAt = base.plusHours(2),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post3/600/600"),
                    amount = 1550,
                    category = PostCategory.TRANSPORT,
                )
            ),
            postRepository.save(
                Post(
                    content = "Third post by alice — 마트 장보기",
                    authorId = alice.id,
                    createdAt = base.plusHours(3),
                    imageUrls = mutableListOf(
                        "https://picsum.photos/seed/post4a/600/600",
                        "https://picsum.photos/seed/post4b/600/600",
                    ),
                    amount = 47800,
                    category = PostCategory.GROCERY,
                    tag = "이마트",
                )
            ),
            postRepository.save(
                Post(
                    content = "lurking out — 영화 한 편",
                    authorId = charlie.id,
                    createdAt = base.plusHours(4),
                    amount = 15000,
                    category = PostCategory.ENTERTAINMENT,
                )
            ),
            postRepository.save(
                Post(
                    content = "Bob's second — 일반 글, 소비 없음",
                    authorId = bob.id,
                    createdAt = base.plusHours(5),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post6/600/600"),
                    tag = "Payflow",
                )
            ),
        )
    }
}
