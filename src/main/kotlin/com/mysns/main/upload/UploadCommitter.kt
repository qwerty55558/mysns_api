package com.mysns.main.upload

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class UploadCommitter(private val props: UploadProperties) {

    private val log = LoggerFactory.getLogger(UploadCommitter::class.java)

    fun commit(tempUrls: List<String>, postId: Long, userId: Long): List<String> {
        if (tempUrls.isEmpty()) return emptyList()
        val expectedPrefix = "${props.publicPrefix.trimEnd('/')}/temp/$userId/"
        val baseDir = Path.of(props.dir).toAbsolutePath().normalize()
        val postDir = baseDir.resolve("posts").resolve(postId.toString())
        Files.createDirectories(postDir)

        return tempUrls.map { url ->
            if (!url.startsWith(expectedPrefix)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "본인이 업로드한 임시 이미지만 사용할 수 있습니다",
                )
            }
            val filename = url.removePrefix(expectedPrefix)
            if (filename.isEmpty() || filename.contains('/') || filename.contains('\\') || filename.contains("..")) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 이미지 경로입니다")
            }
            val src = baseDir.resolve("temp").resolve(userId.toString()).resolve(filename).normalize()
            if (!src.startsWith(baseDir) || !Files.exists(src)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "업로드된 이미지를 찾을 수 없습니다")
            }
            val dst = postDir.resolve(filename)
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            "${props.publicPrefix.trimEnd('/')}/posts/$postId/$filename"
        }
    }

    /**
     * updatePost용. inputUrls는 (1) 이 사용자의 temp URL 또는 (2) 이 post의 기존 commit
     * URL을 섞어 보낼 수 있다. temp는 posts/{postId}/로 이동, 기존 commit URL은 그대로 유지.
     * currentUrls 중 inputUrls 어디에도 없는 파일은 디스크에서 삭제.
     */
    fun reconcile(currentUrls: List<String>, inputUrls: List<String>, postId: Long, userId: Long): List<String> {
        val tempPrefix = "${props.publicPrefix.trimEnd('/')}/temp/$userId/"
        val postPrefix = "${props.publicPrefix.trimEnd('/')}/posts/$postId/"
        val baseDir = Path.of(props.dir).toAbsolutePath().normalize()

        val toCommit = mutableListOf<String>()
        val toKeep = mutableListOf<String>()
        for (url in inputUrls) {
            when {
                url.startsWith(tempPrefix) -> toCommit += url
                url.startsWith(postPrefix) && url in currentUrls -> toKeep += url
                else -> throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "허용되지 않은 이미지 URL: 본인 temp 또는 이 post 의 기존 이미지만 사용 가능",
                )
            }
        }

        val committed = if (toCommit.isNotEmpty()) commit(toCommit, postId, userId) else emptyList()

        val finalUrls = toKeep + committed
        val toDelete = currentUrls - finalUrls.toSet()
        for (url in toDelete) {
            if (!url.startsWith(postPrefix)) continue
            val filename = url.removePrefix(postPrefix)
            if (filename.contains('/') || filename.contains('\\') || filename.contains("..")) continue
            val path = baseDir.resolve("posts").resolve(postId.toString()).resolve(filename).normalize()
            if (path.startsWith(baseDir)) Files.deleteIfExists(path)
        }
        return finalUrls
    }

    fun deletePostDir(postId: Long) {
        val baseDir = Path.of(props.dir).toAbsolutePath().normalize()
        val postDir = baseDir.resolve("posts").resolve(postId.toString()).normalize()
        if (!postDir.startsWith(baseDir) || !Files.exists(postDir)) return
        Files.walk(postDir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
        log.info("deleted post dir — postId={}", postId)
    }
}
