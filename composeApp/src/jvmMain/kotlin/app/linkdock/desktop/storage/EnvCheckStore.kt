package app.linkdock.desktop.storage

import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.platform.PlatformResolver
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.Path

class EnvCheckStore(
    private val platformResolver: PlatformResolver = PlatformResolver()
) {
    private val filePath: Path? by lazy {
        val osType = platformResolver.detectOsType()
        val appDataDir = platformResolver.resolveAppDataDir(osType) ?: return@lazy null
        Path(appDataDir).resolve("env-check.properties")
    }

    fun load(): EnvCheckCache? {
        val path = filePath ?: return null

        return try {
            if (!Files.exists(path)) return null

            val props = Properties()
            Files.newInputStream(path).use { props.load(it) }

            val checkedAt = props.getProperty("checkedAtEpochMillis")?.toLongOrNull() ?: return null
            val osType = runCatching {
                OsType.valueOf(props.getProperty("osType") ?: return null)
            }.getOrNull() ?: return null

            EnvCheckCache(
                checkedAtEpochMillis = checkedAt,
                osType = osType,
                hasStreamlink = props.getProperty("hasStreamlink")?.toBoolean() ?: false,
                hasBrew = props.getProperty("hasBrew")?.toBoolean() ?: false,
                hasWinget = props.getProperty("hasWinget")?.toBoolean() ?: false
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(cache: EnvCheckCache) {
        val path = filePath ?: return

        runCatching {
            Files.createDirectories(path.parent)

            val props = Properties().apply {
                setProperty("checkedAtEpochMillis", cache.checkedAtEpochMillis.toString())
                setProperty("osType", cache.osType.name)
                setProperty("hasStreamlink", cache.hasStreamlink.toString())
                setProperty("hasBrew", cache.hasBrew.toString())
                setProperty("hasWinget", cache.hasWinget.toString())
            }

            Files.newOutputStream(path).use { props.store(it, null) }
        }
    }
}