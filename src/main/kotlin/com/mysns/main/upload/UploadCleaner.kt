package com.mysns.main.upload

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "mysns.upload", name = ["cleanup-enabled"], havingValue = "true", matchIfMissing = true)
class UploadCleaner(private val props: UploadProperties) {

    private val log = LoggerFactory.getLogger(UploadCleaner::class.java)

    @Scheduled(cron = "\${mysns.upload.cleanup-cron}")
    fun cleanupTemp() {
        val tempRoot = Path.of(props.dir).toAbsolutePath().normalize().resolve("temp")
        if (!Files.exists(tempRoot)) return

        val cutoff = Instant.now().minus(props.tempTtl)
        var deletedFiles = 0
        var deletedDirs = 0

        Files.walk(tempRoot).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path == tempRoot) return@forEach
                try {
                    if (Files.isRegularFile(path)) {
                        if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                            Files.deleteIfExists(path)
                            deletedFiles++
                        }
                    } else if (Files.isDirectory(path)) {
                        // 비어 있으면 청소 (유저 폴더가 다 비면 같이 지움)
                        val entries = Files.list(path).use { it.findAny().isPresent }
                        if (!entries) {
                            Files.deleteIfExists(path)
                            deletedDirs++
                        }
                    }
                } catch (e: Exception) {
                    log.warn("temp cleanup 경로 처리 실패: path={} err={}", path, e.message)
                }
            }
        }

        if (deletedFiles > 0 || deletedDirs > 0) {
            log.info("temp cleanup — files={} dirs={} cutoff={}", deletedFiles, deletedDirs, cutoff)
        }
    }
}
