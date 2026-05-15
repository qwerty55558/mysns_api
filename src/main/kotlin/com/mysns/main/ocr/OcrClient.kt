package com.mysns.main.ocr

import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.time.Duration

@Component
class OcrClient(private val props: OcrProperties) {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(props.baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(3))
            setReadTimeout(props.timeout)
        })
        .build()

    fun ocr(filename: String, bytes: ByteArray, contentType: String): OcrResponse {
        val resource = object : ByteArrayResource(bytes) {
            override fun getFilename(): String = filename
        }
        val body = LinkedMultiValueMap<String, Any>().apply {
            add("file", resource)
        }
        return restClient.post()
            .uri("/ocr")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .retrieve()
            .body<OcrResponse>()
            ?: throw IllegalStateException("OCR sidecar returned empty body")
    }

    data class OcrResponse(
        val lines: List<OcrLine> = emptyList(),
        val rawText: String = "",
        val confidence: Double = 0.0,
    )

    data class OcrLine(
        val text: String,
        val confidence: Double,
    )
}
