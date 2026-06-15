package com.mysns.main.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "mysns.notification")
data class NotificationProperties(
    val cooldown: Duration = Duration.ofHours(12),
)
