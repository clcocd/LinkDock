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
                    hasFfmpeg = false,
                    logs = logs
                )
            }
        }
    }

    private fun inspectMac(logs: MutableList<String>): EnvironmentInspectionResult {
        val streamlinkPath = platformResolver.findCommandPath(OsType.MAC, "streamlink")
        val streamlink = commandRunner.runCommandWithFallback(
            "streamlink",
            streamlinkPath,
            "--version"
        )
        appendToolStatus(logs, "Streamlink", streamlink.success, streamlink.firstLine)

        val ffmpegPath = platformResolver.findCommandPath(OsType.MAC, "ffmpeg")
        val ffmpeg = commandRunner.runCommandWithFallback(
            "ffmpeg",
            ffmpegPath,
            "-version"
        )
        appendToolStatus(logs, "FFmpeg", ffmpeg.success, ffmpeg.firstLine)

        val brewPath = platformResolver.findCommandPath(OsType.MAC, "brew")
        val brew = commandRunner.runCommandWithFallback(
            "brew",
            brewPath,
            "--version"
        )
        appendToolStatus(logs, "Homebrew", brew.success, brew.firstLine)

        return EnvironmentInspectionResult(
            osType = OsType.MAC,
            hasStreamlink = streamlink.success,
            hasFfmpeg = ffmpeg.success,
            hasBrew = brew.success,
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

        val ffmpegPath = platformResolver.findCommandPath(OsType.WINDOWS, "ffmpeg")
        val ffmpeg = commandRunner.runCommandWithFallback("ffmpeg", ffmpegPath, "-version")
        appendToolStatus(logs, "FFmpeg", ffmpeg.success, ffmpeg.firstLine)

        return EnvironmentInspectionResult(
            osType = OsType.WINDOWS,
            hasStreamlink = streamlink.success,
            hasFfmpeg = ffmpeg.success,
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
        logs += if (success) {
            formatInstalledToolLine(label, firstLine)
        } else {
            "$label 없음"
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