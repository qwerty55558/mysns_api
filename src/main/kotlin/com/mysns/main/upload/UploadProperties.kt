package com.mysns.main.upload

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mysns.upload")
data class UploadProperties(
    val dir: String = "./uploads",
    val publicPrefix: String = "/uploads",
)
