package app.linkdock.desktop.platform

import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.domain.ServiceType
import java.io.File

class PlatformResolver {

    fun detectOsType(): OsType {
        val osName = System.getProperty("os.name")
            ?.lowercase()
            .orEmpty()

        return when {
            osName.contains("mac") || osName.contains("darwin") -> OsType.MAC
            osName.contains("win") -> OsType.WINDOWS
            else -> OsType.UNSUPPORTED
        }
    }

    fun findCommandPath(
        osType: OsType,
        commandName: String
    ): String? {
        return when (osType) {
            OsType.MAC -> findMacCommandPath(commandName)
            OsType.WINDOWS -> findWindowsCommandPath(commandName)
            OsType.UNSUPPORTED -> null
        }
    }

    fun resolveStreamlinkExecutable(osType: OsType): String? {
        return when (osType) {
            OsType.MAC -> findCommandPath(osType, "streamlink") ?: "streamlink"
            OsType.WINDOWS -> findCommandPath(osType, "streamlink") ?: "streamlink"
            OsType.UNSUPPORTED -> null
        }
    }

    fun resolveAppDataDir(osType: OsType): String? {
        return when (osType) {
            OsType.MAC -> {
                val home = System.getProperty("user.home").orEmpty()
                if (home.isBlank()) null
                else "$home/Library/Application Support/LinkDock"
            }

            OsType.WINDOWS -> {
                val appData = System.getenv("APPDATA").orEmpty()
                if (appData.isBlank()) null
                else "$appData\\LinkDock"
            }

            OsType.UNSUPPORTED -> null
        }
    }

    fun resolveAppPluginDir(osType: OsType): String? {
        val appDataDir = resolveAppDataDir(osType) ?: return null
        return File(appDataDir, "plugins").path
    }

    fun resolveManagedPluginFile(
        osType: OsType,
        serviceType: ServiceType
    ): String? {
        val pluginDir = resolveAppPluginDir(osType) ?: return null

        val fileName = when (serviceType) {
            ServiceType.ZAN -> "zan.py"
            ServiceType.SPWN -> "spwn.py"
        }

        return File(pluginDir, fileName).path
    }

    fun resolveOutputDir(input: String): String {
        if (input.isNotBlank()) return input

        val home = System.getProperty("user.home").orEmpty()
        return if (home.isBlank()) "." else "$home${File.separator}Downloads"
    }

    fun ensureDirectoryExists(path: String) {
        File(path).mkdirs()
    }

    fun findMacCommandPath(commandName: String): String? {
        val candidates = when (commandName) {
            "brew" -> listOf(
                "/opt/homebrew/bin/brew",
                "/usr/local/bin/brew"
            )

            "streamlink" -> listOf(
                "/opt/homebrew/bin/streamlink",
                "/usr/local/bin/streamlink"
            )

            else -> emptyList()
        }

        return candidates.firstOrNull { File(it).canExecute() }
    }

    fun findWindowsCommandPath(commandName: String): String? {
        val localAppData = System.getenv("LOCALAPPDATA").orEmpty()
        val programData = System.getenv("ProgramData").orEmpty()
        val userProfile = System.getenv("USERPROFILE").orEmpty()

        val candidates = when (commandName.lowercase()) {
            "winget" -> listOf(
                "$localAppData\\Microsoft\\WindowsApps\\winget.exe"
            )

            "streamlink" -> listOf(
                "$localAppData\\Programs\\Streamlink\\bin\\streamlink.exe",
                "$localAppData\\Streamlink\\bin\\streamlink.exe",
                "$programData\\chocolatey\\bin\\streamlink.exe",
                "$userProfile\\scoop\\shims\\streamlink.exe"
            )

            else -> emptyList()
        }

        return candidates.firstOrNull { path ->
            path.isNotBlank() && File(path).canExecute()
        }
    }
}