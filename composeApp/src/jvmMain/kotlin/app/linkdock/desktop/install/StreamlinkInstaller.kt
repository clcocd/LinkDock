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

    private enum class MacPackageState {
        NOT_INSTALLED,
        INSTALLED_BY_BREW,
        INSTALLED_OUTSIDE_BREW
    }

    private enum class WindowsPackageState {
        NOT_INSTALLED,
        INSTALLED_BY_WINGET,
        INSTALLED_OUTSIDE_WINGET
    }

    private data class ToolInstallResult(
        val success: Boolean,
        val changed: Boolean,
        val message: String,
        val outcome: InstallationOutcome? = null
    )

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

    private fun toFailureInstallationResultOrNull(
        result: ToolInstallResult
    ): InstallationResult? {
        return if (!result.success) {
            InstallationResult(
                success = false,
                outcome = InstallationOutcome.FAILED,
                completionMessage = result.message
            )
        } else {
            null
        }
    }

    private fun resolveOverallOutcome(
        streamlinkResult: ToolInstallResult,
        ffmpegResult: ToolInstallResult
    ): InstallationOutcome {
        return when {
            streamlinkResult.outcome == InstallationOutcome.INSTALLED ||
                    ffmpegResult.outcome == InstallationOutcome.INSTALLED ->
                InstallationOutcome.INSTALLED

            streamlinkResult.outcome == InstallationOutcome.UPDATED ||
                    ffmpegResult.outcome == InstallationOutcome.UPDATED ->
                InstallationOutcome.UPDATED

            else ->
                InstallationOutcome.ALREADY_LATEST
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

        val resolvedBrew = brewExecutable ?: "brew"

        val streamlinkResult = processMacTool(
            brewExecutable = resolvedBrew,
            commandName = "streamlink",
            packageName = "streamlink",
            toolLabel = "Streamlink",
            versionArg = "--version",
            onLine = onLine,
            onProgressLine = onProgressLine
        )
        toFailureInstallationResultOrNull(streamlinkResult)?.let { return it }

        val ffmpegResult = processMacTool(
            brewExecutable = resolvedBrew,
            commandName = "ffmpeg",
            packageName = "ffmpeg",
            toolLabel = "FFmpeg",
            versionArg = "-version",
            onLine = onLine,
            onProgressLine = onProgressLine
        )
        toFailureInstallationResultOrNull(ffmpegResult)?.let { return it }

        val overallOutcome = resolveOverallOutcome(streamlinkResult, ffmpegResult)

        return InstallationResult(
            success = true,
            outcome = overallOutcome,
            completionMessage = listOf(
                streamlinkResult.message,
                ffmpegResult.message
            ).joinToString("\n"),
            didChangeStreamlink = streamlinkResult.changed
        )
    }

    private fun processMacTool(
        brewExecutable: String,
        commandName: String,
        packageName: String,
        toolLabel: String,
        versionArg: String,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): ToolInstallResult {
        return when (
            detectMacPackageState(
                brewExecutable = brewExecutable,
                commandName = commandName,
                packageName = packageName,
                versionArg = versionArg
            )
        ) {
            MacPackageState.NOT_INSTALLED -> {
                val result = runPackageManagerCommand(
                    startLabel = "Homebrew로",
                    toolLabel = toolLabel,
                    alreadyInstalled = false,
                    command = listOf(brewExecutable, "install", packageName),
                    onLine = onLine,
                    onProgressLine = onProgressLine
                )

                val outcome = classifyBrewResult(alreadyInstalled = false, result = result)
                if (outcome == null) {
                    ToolInstallResult(
                        success = false,
                        changed = false,
                        message = "$toolLabel 설치 실패"
                    )
                } else {
                    ToolInstallResult(
                        success = true,
                        changed = outcome != InstallationOutcome.ALREADY_LATEST,
                        message = describeToolResult(toolLabel, outcome),
                        outcome = outcome
                    )
                }
            }

            MacPackageState.INSTALLED_BY_BREW -> {
                val result = runPackageManagerCommand(
                    startLabel = "Homebrew로",
                    toolLabel = toolLabel,
                    alreadyInstalled = true,
                    command = listOf(brewExecutable, "upgrade", packageName),
                    onLine = onLine,
                    onProgressLine = onProgressLine
                )

                val outcome = classifyBrewResult(alreadyInstalled = true, result = result)
                if (outcome == null) {
                    ToolInstallResult(
                        success = false,
                        changed = false,
                        message = "$toolLabel 업데이트 실패"
                    )
                } else {
                    ToolInstallResult(
                        success = true,
                        changed = outcome != InstallationOutcome.ALREADY_LATEST,
                        message = describeToolResult(toolLabel, outcome),
                        outcome = outcome
                    )
                }
            }

            MacPackageState.INSTALLED_OUTSIDE_BREW -> {
                onLine("$toolLabel 감지됨 (Homebrew 외부 설치, 업데이트 건너뜀)")
                ToolInstallResult(
                    success = true,
                    changed = false,
                    message = "$toolLabel 설치 확인됨 (Homebrew 외부 설치, 업데이트 건너뜀)",
                    outcome = InstallationOutcome.ALREADY_LATEST
                )
            }
        }
    }

    private fun detectMacPackageState(
        brewExecutable: String,
        commandName: String,
        packageName: String,
        versionArg: String
    ): MacPackageState {
        val commandInstalled = commandRunner.runCommandWithFallback(
            commandName,
            platformResolver.findCommandPath(OsType.MAC, commandName),
            versionArg
        ).success

        val brewManaged = commandRunner.runCommandWithFallback(
            "brew",
            brewExecutable,
            "list",
            "--formula",
            packageName
        ).success

        return when {
            brewManaged -> MacPackageState.INSTALLED_BY_BREW
            commandInstalled -> MacPackageState.INSTALLED_OUTSIDE_BREW
            else -> MacPackageState.NOT_INSTALLED
        }
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

        val resolvedWinget = wingetExecutable ?: "winget"

        val streamlinkResult = processWindowsTool(
            wingetExecutable = resolvedWinget,
            commandName = "streamlink",
            packageId = "Streamlink.Streamlink",
            toolLabel = "Streamlink",
            versionArg = "--version",
            onLine = onLine,
            onProgressLine = onProgressLine
        )
        toFailureInstallationResultOrNull(streamlinkResult)?.let { return it }

        val ffmpegResult = processWindowsTool(
            wingetExecutable = resolvedWinget,
            commandName = "ffmpeg",
            packageId = "Gyan.FFmpeg",
            toolLabel = "FFmpeg",
            versionArg = "-version",
            onLine = onLine,
            onProgressLine = onProgressLine
        )
        toFailureInstallationResultOrNull(ffmpegResult)?.let { return it }

        val overallOutcome = resolveOverallOutcome(streamlinkResult, ffmpegResult)

        return InstallationResult(
            success = true,
            outcome = overallOutcome,
            completionMessage = listOf(
                streamlinkResult.message,
                ffmpegResult.message
            ).joinToString("\n"),
            didChangeStreamlink = streamlinkResult.changed
        )
    }

    private fun processWindowsTool(
        wingetExecutable: String,
        commandName: String,
        packageId: String,
        toolLabel: String,
        versionArg: String,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): ToolInstallResult {
        return when (
            detectWindowsPackageState(
                wingetExecutable = wingetExecutable,
                commandName = commandName,
                packageId = packageId,
                versionArg = versionArg
            )
        ) {
            WindowsPackageState.NOT_INSTALLED -> {
                val result = runPackageManagerCommand(
                    startLabel = "WinGet으로",
                    toolLabel = toolLabel,
                    alreadyInstalled = false,
                    command = listOf(
                        wingetExecutable,
                        "install",
                        "-e",
                        "--id", packageId,
                        "--source", "winget",
                        "--accept-source-agreements",
                        "--accept-package-agreements",
                        "--disable-interactivity"
                    ),
                    onLine = onLine,
                    onProgressLine = onProgressLine
                )

                val outcome = classifyWingetResult(alreadyInstalled = false, result = result)
                if (outcome == null) {
                    ToolInstallResult(
                        success = false,
                        changed = false,
                        message = "$toolLabel 설치 실패"
                    )
                } else {
                    ToolInstallResult(
                        success = true,
                        changed = outcome != InstallationOutcome.ALREADY_LATEST,
                        message = describeToolResult(toolLabel, outcome),
                        outcome = outcome
                    )
                }
            }

            WindowsPackageState.INSTALLED_BY_WINGET -> {
                val result = runPackageManagerCommand(
                    startLabel = "WinGet으로",
                    toolLabel = toolLabel,
                    alreadyInstalled = true,
                    command = listOf(
                        wingetExecutable,
                        "upgrade",
                        "-e",
                        "--id", packageId,
                        "--source", "winget",
                        "--accept-source-agreements",
                        "--accept-package-agreements",
                        "--disable-interactivity"
                    ),
                    onLine = onLine,
                    onProgressLine = onProgressLine
                )

                val outcome = classifyWingetResult(alreadyInstalled = true, result = result)
                if (outcome == null) {
                    ToolInstallResult(
                        success = false,
                        changed = false,
                        message = "$toolLabel 업데이트 실패"
                    )
                } else {
                    ToolInstallResult(
                        success = true,
                        changed = outcome != InstallationOutcome.ALREADY_LATEST,
                        message = describeToolResult(toolLabel, outcome),
                        outcome = outcome
                    )
                }
            }

            WindowsPackageState.INSTALLED_OUTSIDE_WINGET -> {
                onLine("$toolLabel 감지됨 (WinGet 외부 설치, 업데이트 건너뜀)")
                ToolInstallResult(
                    success = true,
                    changed = false,
                    message = "$toolLabel 설치 확인됨 (WinGet 외부 설치, 업데이트 건너뜀)",
                    outcome = InstallationOutcome.ALREADY_LATEST
                )
            }
        }
    }

    private fun detectWindowsPackageState(
        wingetExecutable: String,
        commandName: String,
        packageId: String,
        versionArg: String
    ): WindowsPackageState {
        val commandInstalled = commandRunner.runCommandWithFallback(
            commandName,
            platformResolver.findCommandPath(OsType.WINDOWS, commandName),
            versionArg
        ).success

        val wingetManaged = isWingetPackageInstalled(
            wingetExecutable = wingetExecutable,
            packageId = packageId
        )

        return when {
            wingetManaged -> WindowsPackageState.INSTALLED_BY_WINGET
            commandInstalled -> WindowsPackageState.INSTALLED_OUTSIDE_WINGET
            else -> WindowsPackageState.NOT_INSTALLED
        }
    }

    private fun isWingetPackageInstalled(
        wingetExecutable: String,
        packageId: String
    ): Boolean {
        val result = commandRunner.runCommand(
            wingetExecutable,
            "list",
            "-e",
            "--id", packageId,
            "--source", "winget",
            "--accept-source-agreements",
            "--disable-interactivity"
        )

        if (!result.success) {
            return false
        }

        val output = result.fullOutput
        val packageIdRegex = Regex("""(?im)^.*\b${Regex.escape(packageId)}\b.*$""")
        return packageIdRegex.containsMatchIn(output)
    }

    private fun classifyWingetResult(
        alreadyInstalled: Boolean,
        result: CommandResult
    ): InstallationOutcome? {
        val output = result.fullOutput.lowercase()

        val noUpgradeMessages = listOf(
            "no available upgrade found",
            "no newer package versions are available",
            "no applicable upgrade found",
            "사용 가능한 업그레이드를 찾을 수 없습니다",
            "구성된 원본에서 사용할 수 있는 최신 패키지 버전이 없습니다",
            "적용 가능한 업그레이드를 찾을 수 없습니다"
        )

        if (alreadyInstalled && noUpgradeMessages.any { it.lowercase() in output }) {
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

    private fun describeToolResult(
        toolLabel: String,
        outcome: InstallationOutcome
    ): String {
        return when (outcome) {
            InstallationOutcome.INSTALLED -> "$toolLabel 설치 완료"
            InstallationOutcome.UPDATED -> "$toolLabel 업데이트 완료"
            InstallationOutcome.ALREADY_LATEST -> "$toolLabel 확인 완료"
            InstallationOutcome.PREREQUISITE_MISSING -> "$toolLabel 설치 전제 조건 부족"
            InstallationOutcome.UNSUPPORTED_OS -> "$toolLabel 지원하지 않는 운영체제"
            InstallationOutcome.FAILED -> "$toolLabel 처리 실패"
        }
    }
}