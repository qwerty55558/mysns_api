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
class PostStoreVisibilityTest @Autowired constructor(
    private val postStore: PostStore,
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val followRepository: FollowRepository,
) {

    private fun newUser(name: String, private_: Boolean = false): User =
        userRepository.save(
            User(
                username = name,
                displayName = name,
                createdAt = OffsetDateTime.now(),
                passwordHash = "x",
                privateAccount = private_,
            ),
        )

    private fun newPost(author: User): Post =
        postRepository.save(
            Post(content = "test post by ${author.username}", authorId = author.id, createdAt = OffsetDateTime.now()),
        )

    private fun follow(followerId: Long, followeeId: Long) {
        followRepository.save(Follow(followerId = followerId, followeeId = followeeId))
    }

    // ─── feed: public author ───────────────────────────────────────────────

    @Test
    fun `public author post is visible to anonymous viewer`() {
        val author = newUser("pub-anon-author", private_ = false)
        val post = newPost(author)

        val feed = postStore.feed(viewerId = null, limit = 20, offset = 0)
        assertTrue(feed.any { it.id == post.id }, "public post should appear in anonymous feed")
    }

    @Test
    fun `public author post is visible to a random logged-in user`() {
        val author = newUser("pub-random-author", private_ = false)
        val viewer = newUser("pub-random-viewer")
        val post = newPost(author)

        val feed = postStore.feed(viewerId = viewer.id, limit = 20, offset = 0)
        assertTrue(feed.any { it.id == post.id }, "public post should appear in viewer feed")
    }

    @Test
    fun `public author post is visible to the author themselves`() {
        val author = newUser("pub-self-author", private_ = false)
        val post = newPost(author)

        val feed = postStore.feed(viewerId = author.id, limit = 20, offset = 0)
        assertTrue(feed.any { it.id == post.id }, "public post should appear in author's own feed")
    }

    // ─── feed: private author ──────────────────────────────────────────────

    @Test
    fun `private author post is hidden from anonymous viewer`() {
        val author = newUser("priv-anon-author", private_ = true)
        val post = newPost(author)

        val feed = postStore.feed(viewerId = null, limit = 20, offset = 0)
        assertFalse(feed.any { it.id == post.id }, "private post must not appear for anonymous viewer")
    }

    @Test
    fun `private author post is hidden from non-following user`() {
        val author = newUser("priv-nf-author", private_ = true)
        val stranger = newUser("priv-nf-stranger")
        val post = newPost(author)

        val feed = postStore.feed(viewerId = stranger.id, limit = 20, offset = 0)
        assertFalse(feed.any { it.id == post.id }, "private post must not appear for non-follower")
    }

    @Test
    fun `private author post is visible to an approved follower`() {
        val author = newUser("priv-follower-author", private_ = true)
        val follower = newUser("priv-follower-viewer")
        follow(follower.id, author.id)
        val post = newPost(author)

        val feed = postStore.feed(viewerId = follower.id, limit = 20, offset = 0)
        assertTrue(feed.any { it.id == post.id }, "private post should appear for an approved follower")
    }

    @Test
    fun `private author post is visible to the author themselves`() {
        val author = newUser("priv-self-author", private_ = true)
        val post = newPost(author)

        val feed = postStore.feed(viewerId = author.id, limit = 20, offset = 0)
        assertTrue(feed.any { it.id == post.id }, "private post should appear in author's own feed")
    }

    // ─── search: public author ─────────────────────────────────────────────

    @Test
    fun `search public author post is visible to anonymous viewer`() {
        val author = newUser("srch-pub-anon-author", private_ = false)
        postRepository.save(Post(content = "visibility_keyword_pub_anon", authorId = author.id, createdAt = OffsetDateTime.now()))

        val results = postStore.search("visibility_keyword_pub_anon", viewerId = null, limit = 20, offset = 0)
        assertFalse(results.isEmpty(), "public post should appear in anonymous search")
    }

    @Test
    fun `search public author post is visible to random viewer`() {
        val author = newUser("srch-pub-rand-author", private_ = false)
        val viewer = newUser("srch-pub-rand-viewer")
        postRepository.save(Post(content = "visibility_keyword_pub_rand", authorId = author.id, createdAt = OffsetDateTime.now()))

        val results = postStore.search("visibility_keyword_pub_rand", viewerId = viewer.id, limit = 20, offset = 0)
        assertFalse(results.isEmpty(), "public post should appear for random viewer search")
    }

    // ─── search: private author ────────────────────────────────────────────

    @Test
    fun `search private author post is hidden from anonymous viewer`() {
        val author = newUser("srch-priv-anon-author", private_ = true)
        postRepository.save(Post(content = "visibility_keyword_priv_anon", authorId = author.id, createdAt = OffsetDateTime.now()))

        val results = postStore.search("visibility_keyword_priv_anon", viewerId = null, limit = 20, offset = 0)
        assertTrue(results.isEmpty(), "private post must not appear in anonymous search")
    }

    @Test
    fun `search private author post is hidden from non-following user`() {
        val author = newUser("srch-priv-nf-author", private_ = true)
        val stranger = newUser("srch-priv-nf-stranger")
        postRepository.save(Post(content = "visibility_keyword_priv_nf", authorId = author.id, createdAt = OffsetDateTime.now()))

        val results = postStore.search("visibility_keyword_priv_nf", viewerId = stranger.id, limit = 20, offset = 0)
        assertTrue(results.isEmpty(), "private post must not appear for non-follower search")
    }

    @Test
    fun `search private author post is visible to an approved follower`() {
        val author = newUser("srch-priv-fol-author", private_ = true)
        val follower = newUser("srch-priv-fol-viewer")
        follow(follower.id, author.id)
        postRepository.save(Post(content = "visibility_keyword_priv_fol", authorId = author.id, createdAt = OffsetDateTime.now()))

        val results = postStore.search("visibility_keyword_priv_fol", viewerId = follower.id, limit = 20, offset = 0)
        assertFalse(results.isEmpty(), "private post should appear for approved follower search")
    }

    @Test
    fun `search private author post is visible to the author themselves`() {
        val author = newUser("srch-priv-self-author", private_ = true)
        postRepository.save(Post(content = "visibility_keyword_priv_self", authorId = author.id, createdAt = OffsetDateTime.now()))

        val results = postStore.search("visibility_keyword_priv_self", viewerId = author.id, limit = 20, offset = 0)
        assertFalse(results.isEmpty(), "private post should appear in author's own search")
    }
}
