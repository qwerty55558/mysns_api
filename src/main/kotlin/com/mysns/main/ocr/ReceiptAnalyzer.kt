package com.mysns.main.ocr

import com.mysns.main.upload.UploadProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

@Service
class ReceiptAnalyzer(
    private val ocrProps: OcrProperties,
    private val uploadProps: UploadProperties,
    private val ocrEngine: OcrEngine,
) {
    private val log = LoggerFactory.getLogger(ReceiptAnalyzer::class.java)

    fun analyze(imageUrls: List<String>): ReceiptAnalysis {
        if (!ocrProps.enabled) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR 기능이 비활성화되어 있습니다")
        }
        if (imageUrls.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "분석할 이미지가 없습니다")
        }
        if (imageUrls.size > 5) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "영수증 이미지는 최대 5장까지 분석합니다")
        }

        val images = imageUrls.map { url ->
            val path = resolveUploadPath(url)
            OcrImage(path = path, contentType = inferContentType(path.fileName.toString()))
        }

        val result = ocrEngine.analyze(images)

        val trustworthy = result.confidence >= ocrProps.confidenceThreshold
        if (!trustworthy) {
            log.info(
                "OCR confidence {} < threshold {} → amount=null (rawText 길이={})",
                result.confidence, ocrProps.confidenceThreshold, result.rawText.length,
            )
        }
        return ReceiptAnalysis(
            amount = if (trustworthy) result.amount else null,
            item = if (trustworthy) result.item else null,
            tag = if (trustworthy) result.tag else null,
            placeName = if (trustworthy) result.placeName else null,
            rawText = result.rawText,
            confidence = result.confidence,
        )
    }

    private fun resolveUploadPath(imageUrl: String): Path {
        val prefix = uploadProps.publicPrefix.trimEnd('/') + "/"
        if (!imageUrl.startsWith(prefix)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "내부 업로드 경로(${uploadProps.publicPrefix}/...)만 지원합니다",
            )
        }
        val relative = imageUrl.removePrefix(prefix).removePrefix("/")
        if (relative.isEmpty() || relative.contains("..") || relative.contains('\\')) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 이미지 경로입니다")
        }
        val dir = Path.of(uploadProps.dir).toAbsolutePath().normalize()
        val target = dir.resolve(relative).normalize()
        if (!target.startsWith(dir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 이미지 경로입니다")
        }
        if (!Files.exists(target)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다")
        }
        return target
    }

    private fun inferContentType(filename: String): String = when (filename.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }
}
