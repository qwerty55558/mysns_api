package com.mysns.main.ocr

import com.mysns.main.upload.UploadProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

@Service
class ReceiptAnalyzer(
    private val ocrProps: OcrProperties,
    private val uploadProps: UploadProperties,
    private val ocrClient: OcrClient,
    private val amountExtractor: AmountExtractor,
    private val categoryClassifier: CategoryClassifier,
) {
    private val log = LoggerFactory.getLogger(ReceiptAnalyzer::class.java)

    fun analyze(imageUrl: String): ReceiptAnalysis {
        if (!ocrProps.enabled) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR 기능이 비활성화되어 있습니다")
        }

        val path = resolveUploadPath(imageUrl)
        val bytes = Files.readAllBytes(path)
        val filename = path.fileName.toString()
        val contentType = inferContentType(filename)

        val ocr = try {
            ocrClient.ocr(filename, bytes, contentType)
        } catch (e: ResourceAccessException) {
            log.warn("OCR sidecar 연결 실패: {}", e.message)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OCR 서비스에 연결할 수 없습니다 (sidecar 가 떠있는지 확인)",
            )
        } catch (e: HttpClientErrorException) {
            log.warn("OCR sidecar 4xx: {}", e.message)
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "이미지를 인식할 수 없습니다 (지원되는 PNG/JPEG 인지 확인)",
            )
        } catch (e: RestClientException) {
            log.warn("OCR sidecar 5xx 또는 기타: {}", e.message)
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "OCR 처리 중 오류가 발생했습니다",
            )
        }

        return ReceiptAnalysis(
            amount = amountExtractor.extract(ocr.rawText),
            category = categoryClassifier.classify(ocr.rawText),
            rawText = ocr.rawText,
            confidence = ocr.confidence,
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
        if (relative.isEmpty() || relative.contains("..") || relative.contains('/') || relative.contains('\\')) {
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
