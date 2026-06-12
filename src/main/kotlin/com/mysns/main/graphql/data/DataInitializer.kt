package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Place
import com.mysns.main.graphql.model.Post
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
    private val followStore: FollowStore,
    private val messageStore: MessageStore,
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
        seedFollows(users)
        seedConversations(users)

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

    private fun seedFollows(users: Map<String, User>) {
        val alice = users.getValue("alice")
        val bob = users.getValue("bob")
        val charlie = users.getValue("charlie")
        // alice ↔ bob 상호 팔로우, alice → charlie, charlie → alice
        followStore.follow(alice.id, bob.id)
        followStore.follow(alice.id, charlie.id)
        followStore.follow(bob.id, alice.id)
        followStore.follow(charlie.id, alice.id)
    }

    private fun seedConversations(users: Map<String, User>) {
        val alice = users.getValue("alice")
        val bob = users.getValue("bob")
        val charlie = users.getValue("charlie")
        // alice ↔ bob 대화 (bob의 마지막 메시지 → alice에게 안읽음 1)
        messageStore.send(alice.id, bob.id, "안녕 Bob! 어제 그 카페 어디였어?", null)
        messageStore.send(bob.id, alice.id, "강남 스타벅스R점! 모카프라푸치노 추천 ☕", null)
        // alice → charlie 대화
        messageStore.send(alice.id, charlie.id, "charlie 산책 사진 좋더라 🌿", null)
    }

    private fun seedPosts(users: Map<String, User>): List<Post> {
        val base = OffsetDateTime.parse("2026-02-01T00:00:00Z")
        val alice = users.getValue("alice")
        val bob = users.getValue("bob")
        val charlie = users.getValue("charlie")
        return listOf(
            // #1 지출만 — 장소 정보 없이 액수/항목만
            postRepository.save(
                Post(
                    content = "오늘 점심: 김치찌개 정식",
                    authorId = alice.id,
                    createdAt = base,
                    imageUrls = mutableListOf("https://picsum.photos/seed/post1/600/600"),
                    item = "김치찌개 정식",
                    amount = 9000,
                    tag = "백반집",
                )
            ),
            // #2 지출 + 장소 (카카오 카페)
            postRepository.save(
                Post(
                    content = "스벅 모카프라푸치노 ☕",
                    authorId = alice.id,
                    createdAt = base.plusHours(1),
                    item = "모카프라푸치노",
                    amount = 6300,
                    place = Place(
                        latitude = 37.4979,
                        longitude = 127.0276,
                        name = "스타벅스 강남R점",
                        address = "서울 강남구 강남대로 390",
                        externalId = "8137464",
                        categoryName = "카페",
                        categoryCode = "CE7",
                    ),
                )
            ),
            // #3 지출만 (지하철)
            postRepository.save(
                Post(
                    content = "Bob here — 출퇴근 지하철",
                    authorId = bob.id,
                    createdAt = base.plusHours(2),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post3/600/600"),
                    item = "지하철 1회권",
                    amount = 1550,
                )
            ),
            // #4 지출 + 장소 (대형마트)
            postRepository.save(
                Post(
                    content = "Third post by alice — 마트 장보기",
                    authorId = alice.id,
                    createdAt = base.plusHours(3),
                    imageUrls = mutableListOf(
                        "https://picsum.photos/seed/post4a/600/600",
                        "https://picsum.photos/seed/post4b/600/600",
                    ),
                    item = "장보기",
                    amount = 47800,
                    place = Place(
                        latitude = 37.5025,
                        longitude = 127.0257,
                        name = "이마트 역삼점",
                        address = "서울 강남구 강남대로 354",
                        externalId = "8064290",
                        categoryName = "대형마트",
                        categoryCode = "MT1",
                    ),
                )
            ),
            // #5 지출만 (영화관 — 장소 미공개)
            postRepository.save(
                Post(
                    content = "lurking out — 영화 한 편",
                    authorId = charlie.id,
                    createdAt = base.plusHours(4),
                    item = "영화 1매",
                    amount = 15000,
                )
            ),
            // #6 지출 + 장소 (편의점)
            postRepository.save(
                Post(
                    content = "Bob's second — 편의점 야식",
                    authorId = bob.id,
                    createdAt = base.plusHours(5),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post6/600/600"),
                    item = "야식",
                    amount = 8400,
                    place = Place(
                        latitude = 37.5651,
                        longitude = 126.9784,
                        name = "CU 광화문점",
                        address = "서울 종로구 세종대로 175",
                        externalId = "1234567",
                        categoryName = "편의점",
                        categoryCode = "CS2",
                    ),
                )
            ),
            // #7 둘 다 없음 — 일상 글
            postRepository.save(
                Post(
                    content = "charlie 일기 — 오늘은 그냥 산책, 소비 없음",
                    authorId = charlie.id,
                    createdAt = base.plusHours(6),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post7/600/600"),
                    tag = "일상",
                )
            ),
            // #8 장소만 — 누가 사줘서 지출 없음, 장소만 공유
            postRepository.save(
                Post(
                    content = "alice — 친구가 사준 브런치, 장소 공유만!",
                    authorId = alice.id,
                    createdAt = base.plusHours(7),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post8/600/600"),
                    place = Place(
                        latitude = 37.5599,
                        longitude = 126.9255,
                        name = "연남 브런치 하우스",
                        address = "서울 마포구 동교로 234",
                        externalId = "27286472",
                        categoryName = "음식점",
                        categoryCode = "FD6",
                    ),
                )
            ),
            // #9 장소만 — 명소 핀 (관광/공원)
            postRepository.save(
                Post(
                    content = "bob — 한강 산책 좋다 🌊",
                    authorId = bob.id,
                    createdAt = base.plusHours(8),
                    imageUrls = mutableListOf("https://picsum.photos/seed/post9/600/600"),
                    place = Place(
                        latitude = 37.5283,
                        longitude = 126.9326,
                        name = "한강공원 반포지구",
                        address = "서울 서초구 신반포로11길 40",
                        externalId = "9876543",
                        categoryName = "관광명소",
                        categoryCode = "AT4",
                    ),
                )
            ),
        )
    }
}
