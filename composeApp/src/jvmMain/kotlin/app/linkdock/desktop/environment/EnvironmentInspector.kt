package app.linkdock.desktop.environment

import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.platform.PlatformResolver

class EnvironmentInspector(
    private val platformResolver: PlatformResolver,
    private val commandRunner: CommandRunner
) {

    fun inspect(): EnvironmentInspectionResult {
        val osType = platformResolver.detectOsType()
        val logs = mutableListOf<String>()
        logs += "운영체제 감지: $osType"

        return when (osType) {
            OsType.MAC -> inspectMac(logs)
            OsType.WINDOWS -> inspectWindows(logs)
            OsType.UNSUPPORTED -> {
                logs += "이 운영체제는 현재 지원하지 않습니다."
                EnvironmentInspectionResult(
                    osType = OsType.UNSUPPORTED,
                    hasStreamlink = false,
                    logs = logs
                )
            }
        }
    }

    private fun inspectMac(logs: MutableList<String>): EnvironmentInspectionResult {
        val streamlinkPath = platformResolver.findCommandPath(OsType.MAC, "streamlink")
        val streamlink = if (streamlinkPath != null) {
            commandRunner.runCommand(streamlinkPath, "--version")
        } else {
            null
        }

        appendToolStatus(
            logs = logs,
            label = "Streamlink",
            success = streamlink?.success == true,
            firstLine = streamlink?.firstLine
        )

        val brewPath = platformResolver.findCommandPath(OsType.MAC, "brew")
        val brew = if (brewPath != null) {
            commandRunner.runCommand(brewPath, "--version")
        } else {
            null
        }

        appendToolStatus(
            logs = logs,
            label = "Homebrew",
            success = brew?.success == true,
            firstLine = brew?.firstLine
        )

        return EnvironmentInspectionResult(
            osType = OsType.MAC,
            hasStreamlink = streamlink?.success == true,
            hasBrew = brew?.success == true,
            logs = logs
        )
    }

    private fun inspectWindows(logs: MutableList<String>): EnvironmentInspectionResult {
        val wingetPath = platformResolver.findCommandPath(OsType.WINDOWS, "winget")
        val winget = commandRunner.runCommandWithFallback("winget", wingetPath, "--version")
        appendToolStatus(logs, "WinGet", winget.success, winget.firstLine)

        val streamlinkPath = platformResolver.findCommandPath(OsType.WINDOWS, "streamlink")
        val streamlink = commandRunner.runCommandWithFallback("streamlink", streamlinkPath, "--version")
        appendToolStatus(logs, "Streamlink", streamlink.success, streamlink.firstLine)

        return EnvironmentInspectionResult(
            osType = OsType.WINDOWS,
            hasStreamlink = streamlink.success,
            hasWinget = winget.success,
            logs = logs
        )
    }

    private fun appendToolStatus(
        logs: MutableList<String>,
        label: String,
        success: Boolean,
        firstLine: String?
    ) {
        if (success) {
            logs += formatInstalledToolLine(label, firstLine)
        } else {
            logs += "$label 없음"
        }
    }

    private fun formatInstalledToolLine(label: String, raw: String?): String {
        val line = raw?.trim().orEmpty()

        if (line.isBlank()) {
            return "$label 설치됨"
        }

        return if (line.startsWith(label, ignoreCase = true)) {
            val suffix = line.substring(label.length).trim()
            if (suffix.isBlank()) label else "$label $suffix"
        } else {
            "$label $line"
        }
    }
}