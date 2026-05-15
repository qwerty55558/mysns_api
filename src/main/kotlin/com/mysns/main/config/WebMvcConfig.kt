package com.mysns.main.config

import com.mysns.main.upload.UploadProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.nio.file.Path

@Configuration
class WebMvcConfig(private val uploadProps: UploadProperties) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absDir = Path.of(uploadProps.dir).toAbsolutePath().normalize()
        Files.createDirectories(absDir)
        val pattern = "${uploadProps.publicPrefix.trimEnd('/')}/**"
        registry.addResourceHandler(pattern)
            .addResourceLocations(absDir.toUri().toString())
    }
}
