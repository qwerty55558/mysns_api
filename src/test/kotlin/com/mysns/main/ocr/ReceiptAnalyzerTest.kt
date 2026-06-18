package com.mysns.main.ocr

import com.mysns.main.upload.UploadProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path

class ReceiptAnalyzerTest {

    @TempDir
    lateinit var tempDir: Path

    private val publicPrefix = "/uploads"

    private lateinit var uploadProps: UploadProperties
    private lateinit var ocrProps: OcrProperties

    @BeforeEach
    fun setUp() {
        uploadProps = UploadProperties(dir = tempDir.toString(), publicPrefix = publicPrefix)
        ocrProps = OcrProperties(enabled = true, confidenceThreshold = 0.7)
    }

    private fun createFakeImage(subPath: String): String {
        val file = tempDir.resolve(subPath)
        Files.createDirectories(file.parent)
        Files.write(file, byteArrayOf(0xFF.toByte(), 0xD8.toByte())) // fake JPEG bytes
        return "$publicPrefix/$subPath"
    }

    // --- helpers ---

    private fun stubEngine(result: OcrResult) = object : OcrEngine {
        var capturedImages: List<OcrImage> = emptyList()
        override fun analyze(images: List<OcrImage>): OcrResult {
            capturedImages = images
            return result
        }
    }

    // --- tests ---

    @Test
    fun `여러 imageUrls → 엔진에 동일 개수 OcrImage 전달, 신뢰도 충분 시 결과 그대로 반환`() {
        val url1 = createFakeImage("posts/user1/receipt1.jpg")
        val url2 = createFakeImage("posts/user1/receipt2.jpg")

        val stubResult = OcrResult(
            amount = 12000,
            item = "스타벅스 아메리카노",
            tag = "카페",
            placeName = "스타벅스 강남점",
            rawText = "STARBUCKS\n12000",
            confidence = 0.9,
        )
        val engine = stubEngine(stubResult)
        val analyzer = ReceiptAnalyzer(ocrProps, uploadProps, engine)

        val analysis = analyzer.analyze(listOf(url1, url2))

        assertEquals(2, engine.capturedImages.size)
        assertEquals(12000, analysis.amount)
        assertEquals("스타벅스 아메리카노", analysis.item)
        assertEquals("카페", analysis.tag)
        assertEquals("스타벅스 강남점", analysis.placeName)
        assertEquals("STARBUCKS\n12000", analysis.rawText)
        assertEquals(0.9, analysis.confidence)
    }

    @Test
    fun `신뢰도가 threshold 미만이면 amount·item·tag·placeName null, rawText·confidence 유지`() {
        val url1 = createFakeImage("posts/user1/blurry.jpg")

        val lowConfResult = OcrResult(
            amount = 5000,
            item = "불명확",
            tag = "기타",
            placeName = "알수없음",
            rawText = "????? 5000",
            confidence = 0.4,
        )
        val engine = stubEngine(lowConfResult)
        val analyzer = ReceiptAnalyzer(ocrProps, uploadProps, engine)

        val analysis = analyzer.analyze(listOf(url1))

        assertNull(analysis.amount)
        assertNull(analysis.item)
        assertNull(analysis.tag)
        assertNull(analysis.placeName)
        assertEquals("????? 5000", analysis.rawText)
        assertEquals(0.4, analysis.confidence)
    }

    @Test
    fun `빈 리스트 → BAD_REQUEST 예외`() {
        val engine = stubEngine(OcrResult.EMPTY)
        val analyzer = ReceiptAnalyzer(ocrProps, uploadProps, engine)

        val ex = assertThrows<ResponseStatusException> {
            analyzer.analyze(emptyList())
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `6장 초과 → BAD_REQUEST 예외`() {
        val urls = (1..6).map { createFakeImage("posts/user1/img$it.jpg") }
        val engine = stubEngine(OcrResult.EMPTY)
        val analyzer = ReceiptAnalyzer(ocrProps, uploadProps, engine)

        val ex = assertThrows<ResponseStatusException> {
            analyzer.analyze(urls)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `정확히 5장은 통과`() {
        val urls = (1..5).map { createFakeImage("posts/user1/img$it.jpg") }
        val engine = stubEngine(OcrResult.EMPTY)
        val analyzer = ReceiptAnalyzer(ocrProps, uploadProps, engine)

        // should not throw; engine returns EMPTY so confidence 0.0 < threshold → all nulled
        val analysis = analyzer.analyze(urls)
        assertEquals(5, engine.capturedImages.size)
        assertNull(analysis.amount)
    }

    @Test
    fun `OCR 비활성화 시 SERVICE_UNAVAILABLE 예외`() {
        val disabledProps = OcrProperties(enabled = false)
        val engine = stubEngine(OcrResult.EMPTY)
        val analyzer = ReceiptAnalyzer(disabledProps, uploadProps, engine)

        val url = createFakeImage("posts/user1/img.jpg")
        val ex = assertThrows<ResponseStatusException> {
            analyzer.analyze(listOf(url))
        }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.statusCode)
    }
}
