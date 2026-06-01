package com.mysns.main.upload

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "mysns.upload")
data class UploadProperties(
    val dir: String = "./uploads",
    val publicPrefix: String = "/uploads",
    val tempTtl: Duration = Duration.ofHours(24),
    val cleanupCron: String = "0 0 * * * *",
)
