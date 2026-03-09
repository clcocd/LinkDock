package app.linkdock.desktop.storage

import app.linkdock.desktop.domain.OsType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class EnvCheckStore(
    private val appName: String = "LinkDock"
) {
    private val filePath: Path by lazy {
        resolveAppDataDir()
            .resolve("env-check.properties")
    }

    fun load(): EnvCheckCache? {
        return try {
            if (!Files.exists(filePath)) return null

            val props = Properties()
            Files.newInputStream(filePath).use { props.load(it) }

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
        runCatching {
            Files.createDirectories(filePath.parent)

            val props = Properties().apply {
                setProperty("checkedAtEpochMillis", cache.checkedAtEpochMillis.toString())
                setProperty("osType", cache.osType.name)
                setProperty("hasStreamlink", cache.hasStreamlink.toString())
                setProperty("hasBrew", cache.hasBrew.toString())
                setProperty("hasWinget", cache.hasWinget.toString())
            }

            Files.newOutputStream(filePath).use { props.store(it, null) }
        }
    }

    private fun resolveAppDataDir(): Path {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            osName.contains("win") -> {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) {
                    Paths.get(appData, appName)
                } else {
                    Paths.get(userHome, "AppData", "Roaming", appName)
                }
            }

            osName.contains("mac") -> {
                Paths.get(userHome, "Library", "Application Support", appName)
            }

            else -> {
                Paths.get(userHome, ".$appName")
            }
        }
    }
}