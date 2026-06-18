package com.mysns.main.ocr

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller

@Controller
class ReceiptAnalysisController(private val analyzer: ReceiptAnalyzer) {

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    fun analyzeReceipt(@Argument imageUrls: List<String>): ReceiptAnalysis =
        analyzer.analyze(imageUrls)
}
