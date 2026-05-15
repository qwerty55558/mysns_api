package com.mysns.main.upload

import com.mysns.main.auth.requireCurrentUser
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@RestController
class UploadController(private val props: UploadProperties) {

    private val log = LoggerFactory.getLogger(UploadController::class.java)

    @PostMapping("/upload")
    fun upload(@RequestPart("file") file: MultipartFile): UploadResponse {
        val user = requireCurrentUser()

        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다")
        }
        val contentType = file.contentType
        val ext = EXT_BY_CONTENT_TYPE[contentType]
            ?: throw ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "지원하지 않는 이미지 형식입니다 (png, jpg, webp 만 허용)",
            )

        val dir = Path.of(props.dir).toAbsolutePath().normalize()
        Files.createDirectories(dir)

        val filename = "${UUID.randomUUID()}.$ext"
        val target = dir.resolve(filename)
        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        log.info("upload ok — user={} filename={} size={}", user.userId, filename, file.size)
        return UploadResponse(
            url = "${props.publicPrefix}/$filename",
            size = file.size,
            contentType = contentType ?: "application/octet-stream",
        )
    }

    data class UploadResponse(
        val url: String,
        val size: Long,
        val contentType: String,
    )

    companion object {
        private val EXT_BY_CONTENT_TYPE = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/jpg" to "jpg",
            "image/webp" to "webp",
        )
    }
}
