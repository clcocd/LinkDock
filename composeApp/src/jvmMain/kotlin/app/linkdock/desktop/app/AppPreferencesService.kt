package app.linkdock.desktop.app

import app.linkdock.desktop.platform.PlatformResolver
import app.linkdock.desktop.release.AppReleaseNotes
import app.linkdock.desktop.release.ReleaseNoteEntry
import app.linkdock.desktop.storage.AppSettings
import app.linkdock.desktop.storage.AppSettingsStore
import java.io.File

data class OutputDirectoryRestoreResult(
    val visiblePath: String,
    val warningLog: String? = null
)

class AppPreferencesService(
    private val platformResolver: PlatformResolver = PlatformResolver(),
    private val appSettingsStore: AppSettingsStore = AppSettingsStore(platformResolver)
) {
    fun getStartupReleaseNote(): ReleaseNoteEntry? {
        val currentVersion = AppInfo.version
        val currentReleaseNote = AppReleaseNotes.find(currentVersion) ?: return null
        val settings = appSettingsStore.load() ?: AppSettings()

        return if (settings.lastSeenReleaseNotesVersion == currentVersion) {
            null
        } else {
            currentReleaseNote
        }
    }

    fun getCurrentReleaseNote(): ReleaseNoteEntry? {
        return AppReleaseNotes.find(AppInfo.version)
    }

    fun markCurrentReleaseNotesAsSeen(): Boolean {
        val currentVersion = AppInfo.version
        val currentSettings = appSettingsStore.load() ?: AppSettings()

        return appSettingsStore.save(
            currentSettings.copy(
                lastSeenReleaseNotesVersion = currentVersion
            )
        )
    }

    fun restoreOutputDirectory(): OutputDirectoryRestoreResult {
        val savedPath = appSettingsStore.load()?.lastSavePath
        val defaultPath = platformResolver.resolveOutputDir("")

        val savedPathInvalid =
            !savedPath.isNullOrBlank() && !isUsableDirectory(savedPath)

        val visiblePath = when {
            !savedPath.isNullOrBlank() && !savedPathInvalid -> savedPath
            else -> defaultPath
        }

        val warningLog = if (savedPathInvalid) {
            "저장된 경로를 사용할 수 없어 기본 다운로드 폴더를 사용합니다."
        } else {
            null
        }

        return OutputDirectoryRestoreResult(
            visiblePath = visiblePath,
            warningLog = warningLog
        )
    }

    fun saveOutputDirectory(path: String): Boolean {
        val currentSettings = appSettingsStore.load() ?: AppSettings()

        return appSettingsStore.save(
            currentSettings.copy(
                lastSavePath = path
            )
        )
    }

    private fun isUsableDirectory(path: String): Boolean {
        return runCatching {
            val dir = File(path)
            dir.exists() && dir.isDirectory && dir.canWrite()
        }.getOrDefault(false)
    }
}