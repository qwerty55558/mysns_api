package com.mysns.main.upload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

class UploadCommitterAvatarTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var committer: UploadCommitter
    private val userId = 42L
    private val pub = "/uploads"

    @BeforeEach
    fun setUp() {
        val props = UploadProperties(
            dir = tempDir.toString(),
            publicPrefix = pub,
        )
        committer = UploadCommitter(props)
    }

    private fun createTempFile(filename: String): Path {
        val dir = tempDir.resolve("temp").resolve(userId.toString())
        Files.createDirectories(dir)
        val file = dir.resolve(filename)
        Files.writeString(file, "dummy")
        return file
    }

    @Test
    fun `temp 파일을 avatars 디렉터리로 이동하고 올바른 URL을 반환한다`() {
        val filename = "avatar.jpg"
        val src = createTempFile(filename)

        val result = committer.commitAvatar("$pub/temp/$userId/$filename", userId)

        assertEquals("$pub/avatars/$userId/$filename", result)
        assertFalse(Files.exists(src), "원본 temp 파일이 삭제되어야 한다")
        assertTrue(Files.exists(tempDir.resolve("avatars").resolve(userId.toString()).resolve(filename)), "avatars 경로에 파일이 있어야 한다")
    }

    @Test
    fun `이미 커밋된 avatars URL은 idempotent하게 그대로 반환한다`() {
        val url = "$pub/avatars/$userId/avatar.jpg"
        val result = committer.commitAvatar(url, userId)
        assertEquals(url, result)
    }

    @Test
    fun `seed 경로 URL은 그대로 반환한다`() {
        val url = "$pub/seed/default-avatar.png"
        val result = committer.commitAvatar(url, userId)
        assertEquals(url, result)
    }

    @Test
    fun `타인의 temp URL은 BAD_REQUEST 예외를 던진다`() {
        val otherUserId = 99L
        val url = "$pub/temp/$otherUserId/avatar.jpg"
        assertThrows<ResponseStatusException> {
            committer.commitAvatar(url, userId)
        }
    }

    @Test
    fun `완전히 다른 경로는 BAD_REQUEST 예외를 던진다`() {
        assertThrows<ResponseStatusException> {
            committer.commitAvatar("/some/other/path.jpg", userId)
        }
    }

    @Test
    fun `존재하지 않는 temp 파일은 NOT_FOUND 예외를 던진다`() {
        val url = "$pub/temp/$userId/nonexistent.jpg"
        assertThrows<ResponseStatusException> {
            committer.commitAvatar(url, userId)
        }
    }

    @Test
    fun `deleteUserAvatars 는 avatars 디렉터리를 재귀 삭제한다`() {
        val avatarDir = tempDir.resolve("avatars").resolve(userId.toString())
        Files.createDirectories(avatarDir)
        Files.writeString(avatarDir.resolve("avatar.jpg"), "dummy")

        committer.deleteUserAvatars(userId)

        assertFalse(Files.exists(avatarDir), "avatars 디렉터리가 삭제되어야 한다")
    }

    @Test
    fun `deleteUserAvatars 는 디렉터리가 없어도 예외 없이 완료된다`() {
        committer.deleteUserAvatars(999L)
    }
}
