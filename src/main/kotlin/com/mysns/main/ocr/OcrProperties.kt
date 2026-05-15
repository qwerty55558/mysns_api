package com.mysns.main.ocr

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "mysns.ocr")
data class OcrProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "http://localhost:8001",
    val timeout: Duration = Duration.ofSeconds(15),
)
