package com.mysns.main.ocr

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mysns.ocr")
data class OcrProperties(
    val enabled: Boolean = true,
    val confidenceThreshold: Double = 0.7,
)
