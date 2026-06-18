package com.mysns.main.graphql.data

import com.mysns.main.graphql.model.Post
import com.mysns.main.graphql.model.User
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostStore::class)
class PostStoreDeleteTest @Autowired constructor(
    private val postStore: PostStore,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository,
) {

    @Test
    fun `delete 는 true 를 반환하고 DB에서 행을 실제로 제거한다`() {
        // arrange: author + post 저장
        val author = userRepository.save(
            User(username = "deleter", displayName = "deleter", createdAt = OffsetDateTime.now(), passwordHash = "x"),
        )
        val post = postRepository.save(
            Post(content = "삭제 테스트", authorId = author.id, createdAt = OffsetDateTime.now()),
        )
        val postId = post.id

        // act
        val result = postStore.delete(postId)

        // assert: 반환값
        assertTrue(result, "delete() 는 true 를 반환해야 한다")

        // DB round-trip: 캐시를 완전히 무시하고 실제 행 존재 여부 확인
        val found = postRepository.findById(postId)
        assertFalse(found.isPresent, "delete() 후 DB에서 게시글 행이 존재해서는 안 된다")
    }
}
