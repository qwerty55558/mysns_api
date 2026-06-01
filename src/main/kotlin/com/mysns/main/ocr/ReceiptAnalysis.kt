package com.mysns.main.ocr

data class ReceiptAnalysis(
    val amount: Int?,
    val item: String?,
    val tag: String?,
    val placeName: String?,
    val rawText: String,
    val confidence: Double,
)
