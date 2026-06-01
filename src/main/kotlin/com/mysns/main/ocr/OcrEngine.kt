package com.mysns.main.ocr

import java.nio.file.Path

interface OcrEngine {
    fun analyze(image: Path, contentType: String): OcrResult
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
