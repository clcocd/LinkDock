package app.linkdock.desktop.storage

import app.linkdock.desktop.platform.PlatformResolver
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.Path

class AppSettingsStore(
    private val platformResolver: PlatformResolver = PlatformResolver()
) {
    private val filePath: Path? by lazy {
        val osType = platformResolver.detectOsType()
        val appDataDir = platformResolver.resolveAppDataDir(osType) ?: return@lazy null
        Path(appDataDir).resolve("app-settings.properties")
    }

    fun load(): AppSettings? {
        val path = filePath ?: return null

        return try {
            if (!Files.exists(path)) return null

            val props = Properties()
            Files.newInputStream(path).use { props.load(it) }

            AppSettings(
                lastSavePath = props.getProperty("lastSavePath")
                    ?.takeIf { it.isNotBlank() },
                lastSeenReleaseNotesVersion = props.getProperty("lastSeenReleaseNotesVersion")
                    ?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            System.err.println("Failed to load app settings: $path")
            e.printStackTrace()
            null
        }
    }

    fun save(settings: AppSettings): Boolean {
        val path = filePath ?: return false

        return runCatching {
            Files.createDirectories(path.parent)

            val props = Properties().apply {
                settings.lastSavePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setProperty("lastSavePath", it) }

                settings.lastSeenReleaseNotesVersion
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setProperty("lastSeenReleaseNotesVersion", it) }
            }

            Files.newOutputStream(path).use { props.store(it, null) }
        }.onFailure { e ->
            System.err.println("Failed to save app settings: $path")
            e.printStackTrace()
        }.isSuccess
    }
}