package com.mysns.main.ocr

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Base64

@Component
class GeminiOcrEngine(
    @Value("\${mysns.gemini.api-key:}") private val apiKey: String,
    @Value("\${mysns.gemini.model:gemini-3.1-flash-lite}") private val model: String,
    @Value("\${mysns.gemini.base-url:https://generativelanguage.googleapis.com}") baseUrl: String,
    @Value("\${mysns.gemini.timeout:30s}") timeout: Duration,
    private val objectMapper: ObjectMapper,
) : OcrEngine {

    private val log = LoggerFactory.getLogger(GeminiOcrEngine::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(timeout)
        })
        .build()

    override fun analyze(image: Path, contentType: String): OcrResult {
        if (apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY 미설정 — OCR 빈 결과 반환 (FE에서 수동입력 유도)")
            return OcrResult.EMPTY
        }

        val base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(image))
        val body = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("inline_data" to mapOf("mime_type" to contentType, "data" to base64)),
                        mapOf("text" to PROMPT),
                    ),
                ),
            ),
            "generationConfig" to mapOf(
                "response_mime_type" to "application/json",
                "response_schema" to RESPONSE_SCHEMA,
                "temperature" to 0.1,
            ),
        )

        val responseBytes = try {
            restClient.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body<ByteArray>()
                ?: run {
                    log.warn("Gemini 응답 본문이 비어있음")
                    return OcrResult.EMPTY
                }
        } catch (e: RestClientException) {
            log.warn("Gemini 호출 실패: {}", e.message)
            return OcrResult.EMPTY
        }

        return parseResponse(responseBytes)
    }

    private fun parseResponse(bytes: ByteArray): OcrResult {
        val root = try {
            objectMapper.readTree(bytes)
        } catch (e: Exception) {
            log.warn("Gemini 응답 JSON 파싱 실패: {}", e.message)
            return OcrResult.EMPTY
        }

        val text = root.path("candidates").path(0)
            .path("content").path("parts").path(0)
            .path("text").asString(null)
            ?: run {
                log.warn("Gemini 응답에서 candidates[0].content.parts[0].text 없음")
                return OcrResult.EMPTY
            }

        return try {
            objectMapper.readValue(text, GeminiOcrPayload::class.java).toResult()
        } catch (e: Exception) {
            log.warn("Gemini 구조화 응답 디시리얼라이즈 실패: {}", e.message)
            OcrResult.EMPTY
        }
    }

    data class GeminiOcrPayload(
        val amount: Int? = null,
        val item: String? = null,
        val tag: String? = null,
        val placeName: String? = null,
        val rawText: String = "",
        val confidence: Double = 0.0,
    ) {
        fun toResult() = OcrResult(
            amount = amount,
            item = item?.takeIf { it.isNotBlank() },
            tag = tag?.takeIf { it.isNotBlank() },
            placeName = placeName?.takeIf { it.isNotBlank() },
            rawText = rawText,
            confidence = confidence.coerceIn(0.0, 1.0),
        )
    }

    companion object {
        private const val PROMPT = """
이 한국어 영수증 이미지를 분석해서 다음을 JSON으로 반환해.

- amount: 최종 결제 금액 (정수, 원 단위). 콤마/공백 없이. 못 찾으면 null.
- item: 지출 항목을 한 줄로 요약 (예: "이마트 식료품", "스타벅스 아메리카노 2잔"). 가맹점 + 핵심 품목.
- tag: 다음 중 하나의 카테고리 — 식비, 카페, 교통, 쇼핑, 의료, 문화, 생활, 통신, 기타
- placeName: 영수증에 표시된 매장/가맹점 이름 (예: "이마트 일산점", "스타벅스 강남점")
- rawText: OCR 된 영수증 전체 텍스트 (줄바꿈 유지)
- confidence: 위 항목들에 대한 전체 추출 신뢰도 0.0 ~ 1.0

영수증이 아니거나 판독 불가하면 모든 필드 null/빈문자열, confidence 0.0.
"""
        private val RESPONSE_SCHEMA = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "amount" to mapOf("type" to "integer", "nullable" to true),
                "item" to mapOf("type" to "string", "nullable" to true),
                "tag" to mapOf("type" to "string", "nullable" to true),
                "placeName" to mapOf("type" to "string", "nullable" to true),
                "rawText" to mapOf("type" to "string"),
                "confidence" to mapOf("type" to "number"),
            ),
            "required" to listOf("rawText", "confidence"),
        )
    }
}
