package com.mysns.main.ocr

import java.nio.file.Path

data class OcrImage(val path: Path, val contentType: String)

interface OcrEngine {
    fun analyze(images: List<OcrImage>): OcrResult
}

data class OcrResult(
    val amount: Int?,
    val item: String?,
    val tag: String?,
    val placeName: String?,
    val rawText: String,
    val confidence: Double,
) {
    companion object {
        val EMPTY = OcrResult(
            amount = null,
            item = null,
            tag = null,
            placeName = null,
            rawText = "",
            confidence = 0.0,
        )
    }
}
