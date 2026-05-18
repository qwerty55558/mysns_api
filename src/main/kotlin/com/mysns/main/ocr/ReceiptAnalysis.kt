package com.mysns.main.ocr

data class ReceiptAnalysis(
    val amount: Int?,
    val rawText: String,
    val confidence: Double,
)
