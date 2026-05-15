package com.mysns.main.ocr

import com.mysns.main.graphql.model.PostCategory

data class ReceiptAnalysis(
    val amount: Int?,
    val category: PostCategory?,
    val rawText: String,
    val confidence: Double,
)
