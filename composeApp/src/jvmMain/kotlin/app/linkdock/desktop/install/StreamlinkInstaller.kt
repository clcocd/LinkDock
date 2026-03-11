package app.linkdock.desktop.install

import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.command.CommandResult
import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.platform.PlatformResolver

class StreamlinkInstaller(
    private val platformResolver: PlatformResolver,
    private val commandRunner: CommandRunner
) {

    fun installOrUpdate(
        state: AppUiState,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationResult {
        val osType = state.osType ?: platformResolver.detectOsType()

        return when (osType) {
            OsType.MAC -> installOrUpdateOnMac(onLine, onProgressLine)
            OsType.WINDOWS -> installOrUpdateOnWindows(onLine, onProgressLine)
            OsType.UNSUPPORTED -> InstallationResult(
                success = false,
                outcome = InstallationOutcome.UNSUPPORTED_OS,
                completionMessage = "현재 운영체제에서는 설치/업데이트를 지원하지 않습니다."
            )
        }
    }

    private fun runPackageManagerCommand(
        startLabel: String,
        toolLabel: String,
        alreadyInstalled: Boolean,
        command: List<String>,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): CommandResult {
        val action = if (alreadyInstalled) "업데이트" else "설치"
        onLine("$startLabel $toolLabel $action 중...")

        val result = commandRunner.runStreamingCommand(
            command = command,
            onLine = onLine,
            onProgressLine = onProgressLine
        )

        onProgressLine(null)
        return result
    }

    private fun installOrUpdateOnMac(
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationResult {
        val brewExecutable = platformResolver.findCommandPath(OsType.MAC, "brew")
        val brewProbe = commandRunner.runCommandWithFallback("brew", brewExecutable, "--version")

        if (!brewProbe.success) {
            return InstallationResult(
                success = false,
                outcome = InstallationOutcome.PREREQUISITE_MISSING,
                completionMessage = "Homebrew 없음"
            )
        }

        val streamlinkInstalled = commandRunner
            .runCommandWithFallback(
                "streamlink",
                platformResolver.findCommandPath(OsType.MAC, "streamlink"),
                "--version"
            )
            .success

        val ffmpegInstalled = commandRunner
            .runCommandWithFallback(
                "ffmpeg",
                platformResolver.findCommandPath(OsType.MAC, "ffmpeg"),
                "-version"
            )
            .success

        val resolvedBrew = brewExecutable ?: "brew"

        val streamlinkOutcome = runMacPackage(
            brewExecutable = resolvedBrew,
            packageName = "streamlink",
            toolLabel = "Streamlink",
            alreadyInstalled = streamlinkInstalled,
            onLine = onLine,
            onProgressLine = onProgressLine
        ) ?: return InstallationResult(
            success = false,
            outcome = InstallationOutcome.FAILED,
            completionMessage = if (streamlinkInstalled) {
                "Streamlink 업데이트 실패"
            } else {
                "Streamlink 설치 실패"
            }
        )

        val ffmpegOutcome = runMacPackage(
            brewExecutable = resolvedBrew,
            packageName = "ffmpeg",
            toolLabel = "FFmpeg",
            alreadyInstalled = ffmpegInstalled,
            onLine = onLine,
            onProgressLine = onProgressLine
        ) ?: return InstallationResult(
            success = false,
            outcome = InstallationOutcome.FAILED,
            completionMessage = if (ffmpegInstalled) {
                "FFmpeg 업데이트 실패"
            } else {
                "FFmpeg 설치 실패"
            }
        )

        return buildCombinedSuccessResult(streamlinkOutcome, ffmpegOutcome)
    }

    private fun runMacPackage(
        brewExecutable: String,
        packageName: String,
        toolLabel: String,
        alreadyInstalled: Boolean,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationOutcome? {
        val command = if (alreadyInstalled) {
            listOf(brewExecutable, "upgrade", packageName)
        } else {
            listOf(brewExecutable, "install", packageName)
        }

        val result = runPackageManagerCommand(
            startLabel = "Homebrew로",
            toolLabel = toolLabel,
            alreadyInstalled = alreadyInstalled,
            command = command,
            onLine = onLine,
            onProgressLine = onProgressLine
        )

        return classifyBrewResult(alreadyInstalled, result)
    }

    private fun classifyBrewResult(
        alreadyInstalled: Boolean,
        result: CommandResult
    ): InstallationOutcome? {
        val output = result.fullOutput.lowercase()

        val alreadyLatest = alreadyInstalled && (
                "already installed and up-to-date" in output ||
                        "no packages to upgrade" in output ||
                        ("warning:" in output && "already installed" in output) ||
                        "already up-to-date" in output
                )

        if (alreadyLatest) {
            return InstallationOutcome.ALREADY_LATEST
        }

        if (result.exitCode == 0) {
            return if (alreadyInstalled) {
                InstallationOutcome.UPDATED
            } else {
                InstallationOutcome.INSTALLED
            }
        }

        return null
    }

    private fun installOrUpdateOnWindows(
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationResult {
        val wingetExecutable = platformResolver.findCommandPath(OsType.WINDOWS, "winget")
        val wingetProbe = commandRunner.runCommandWithFallback(
            "winget",
            wingetExecutable,
            "--version"
        )

        if (!wingetProbe.success) {
            return InstallationResult(
                success = false,
                outcome = InstallationOutcome.PREREQUISITE_MISSING,
                completionMessage = "WinGet 없음"
            )
        }

        val streamlinkInstalled = commandRunner
            .runCommandWithFallback(
                "streamlink",
                platformResolver.findCommandPath(OsType.WINDOWS, "streamlink"),
                "--version"
            )
            .success

        val ffmpegInstalled = commandRunner
            .runCommandWithFallback(
                "ffmpeg",
                platformResolver.findCommandPath(OsType.WINDOWS, "ffmpeg"),
                "-version"
            )
            .success

        val resolvedWinget = wingetExecutable ?: "winget"

        val streamlinkOutcome = runWindowsPackage(
            wingetExecutable = resolvedWinget,
            packageId = "Streamlink.Streamlink",
            toolLabel = "Streamlink",
            alreadyInstalled = streamlinkInstalled,
            onLine = onLine,
            onProgressLine = onProgressLine
        ) ?: return InstallationResult(
            success = false,
            outcome = InstallationOutcome.FAILED,
            completionMessage = if (streamlinkInstalled) {
                "WinGet으로 Streamlink 업데이트 실패"
            } else {
                "WinGet으로 Streamlink 설치 실패"
            }
        )

        val ffmpegOutcome = runWindowsPackage(
            wingetExecutable = resolvedWinget,
            packageId = "Gyan.FFmpeg",
            toolLabel = "FFmpeg",
            alreadyInstalled = ffmpegInstalled,
            onLine = onLine,
            onProgressLine = onProgressLine
        ) ?: return InstallationResult(
            success = false,
            outcome = InstallationOutcome.FAILED,
            completionMessage = if (ffmpegInstalled) {
                "WinGet으로 FFmpeg 업데이트 실패"
            } else {
                "WinGet으로 FFmpeg 설치 실패"
            }
        )

        return buildCombinedSuccessResult(streamlinkOutcome, ffmpegOutcome)
    }

    private fun runWindowsPackage(
        wingetExecutable: String,
        packageId: String,
        toolLabel: String,
        alreadyInstalled: Boolean,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationOutcome? {
        val commonArgs = listOf(
            "-e",
            "--id", packageId,
            "--source", "winget",
            "--accept-source-agreements",
            "--accept-package-agreements",
            "--disable-interactivity"
        )

        val command = if (alreadyInstalled) {
            listOf(wingetExecutable, "upgrade") + commonArgs
        } else {
            listOf(wingetExecutable, "install") + commonArgs
        }

        val result = runPackageManagerCommand(
            startLabel = "WinGet으로",
            toolLabel = toolLabel,
            alreadyInstalled = alreadyInstalled,
            command = command,
            onLine = onLine,
            onProgressLine = onProgressLine
        )

        return classifyWingetResult(alreadyInstalled, result)
    }

    private fun classifyWingetResult(
        alreadyInstalled: Boolean,
        result: CommandResult
    ): InstallationOutcome? {
        val output = result.fullOutput.lowercase()

        if (
            alreadyInstalled &&
            (
                    "no available upgrade found" in output ||
                            "no newer package versions are available" in output ||
                            "no applicable upgrade found" in output
                    )
        ) {
            return InstallationOutcome.ALREADY_LATEST
        }

        if (result.exitCode == 0) {
            return if (alreadyInstalled) {
                InstallationOutcome.UPDATED
            } else {
                InstallationOutcome.INSTALLED
            }
        }

        return null
    }

    private fun buildCombinedSuccessResult(
        streamlinkOutcome: InstallationOutcome,
        ffmpegOutcome: InstallationOutcome
    ): InstallationResult {
        val lines = listOf(
            describeToolResult("Streamlink", streamlinkOutcome),
            describeToolResult("FFmpeg", ffmpegOutcome)
        )

        val overallOutcome = when {
            streamlinkOutcome == InstallationOutcome.INSTALLED ||
                    ffmpegOutcome == InstallationOutcome.INSTALLED ->
                InstallationOutcome.INSTALLED

            streamlinkOutcome == InstallationOutcome.UPDATED ||
                    ffmpegOutcome == InstallationOutcome.UPDATED ->
                InstallationOutcome.UPDATED

            else ->
                InstallationOutcome.ALREADY_LATEST
        }

        return InstallationResult(
            success = true,
            outcome = overallOutcome,
            completionMessage = lines.joinToString("\n"),
            didChangeStreamlink = streamlinkOutcome != InstallationOutcome.ALREADY_LATEST
        )
    }

    private fun describeToolResult(
        toolLabel: String,
        outcome: InstallationOutcome
    ): String {
        return when (outcome) {
            InstallationOutcome.INSTALLED -> "$toolLabel 설치 완료"
            InstallationOutcome.UPDATED -> "$toolLabel 업데이트 완료"
            InstallationOutcome.ALREADY_LATEST -> "$toolLabel 는 이미 최신 상태입니다."
            InstallationOutcome.PREREQUISITE_MISSING -> "$toolLabel 설치 전제 조건 부족"
            InstallationOutcome.UNSUPPORTED_OS -> "$toolLabel 지원하지 않는 운영체제"
            InstallationOutcome.FAILED -> "$toolLabel 처리 실패"
        }
    }
}