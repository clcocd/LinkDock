package app.linkdock.desktop.install

import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.platform.PlatformResolver
import app.linkdock.desktop.command.CommandResult

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
            OsType.MAC -> installOrUpdateOnMac(state, onLine, onProgressLine)
            OsType.WINDOWS -> installOrUpdateOnWindows(state, onLine, onProgressLine)
            OsType.UNSUPPORTED -> InstallationResult(
                success = false,
                completionMessage = "현재 운영체제에서는 설치/업데이트를 지원하지 않습니다."
            )
        }
    }

    private fun actionWord(state: AppUiState): String {
        return if (state.hasStreamlink) "업데이트" else "설치"
    }

    private fun runPackageManagerCommand(
        startLabel: String,
        state: AppUiState,
        command: List<String>,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit,
        classifier: (AppUiState, CommandResult) -> InstallationResult
    ): InstallationResult {
        onLine("$startLabel Streamlink ${actionWord(state)} 중...")

        val result = commandRunner.runStreamingCommand(
            command = command,
            onLine = onLine,
            onProgressLine = onProgressLine
        )

        onProgressLine(null)
        return classifier(state, result)
    }

    private fun installOrUpdateOnMac(
        state: AppUiState,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationResult {
        val preferredBrewPath = platformResolver.findCommandPath(OsType.MAC, "brew")
        val brewProbe = commandRunner.runCommandWithFallback(
            commandName = "brew",
            preferredBrewPath,
            "--version"
        )

        if (!brewProbe.success) {
            return InstallationResult(
                success = false,
                completionMessage = "Homebrew 없음"
            )
        }

        val brewExecutable = preferredBrewPath ?: "brew"

        val command = if (state.hasStreamlink) {
            listOf(brewExecutable, "upgrade", "streamlink")
        } else {
            listOf(brewExecutable, "install", "streamlink")
        }

        return runPackageManagerCommand(
            startLabel = "Homebrew로",
            state = state,
            command = command,
            onLine = onLine,
            onProgressLine = onProgressLine,
            classifier = ::classifyBrewResult
        )
    }

    private fun classifyBrewResult(
        state: AppUiState,
        result: CommandResult
    ): InstallationResult {
        val output = result.fullOutput.lowercase()

        val alreadyLatest = state.hasStreamlink && (
                "already installed and up-to-date" in output ||
                        "no packages to upgrade" in output ||
                        ("warning:" in output && "already installed" in output && "streamlink" in output) ||
                        ("already installed" in output && "streamlink" in output)
                )

        if (alreadyLatest) {
            return InstallationResult(
                success = true,
                completionMessage = "Homebrew 기준 Streamlink는 이미 최신 버전입니다."
            )
        }

        if (result.exitCode == 0) {
            return InstallationResult(
                success = true,
                completionMessage = if (state.hasStreamlink) {
                    "Streamlink 업데이트 완료"
                } else {
                    "Streamlink 설치 완료"
                }
            )
        }

        return InstallationResult(
            success = false,
            completionMessage = if (state.hasStreamlink) {
                "Streamlink 업데이트 실패"
            } else {
                "Streamlink 설치 실패"
            }
        )
    }

    private fun installOrUpdateOnWindows(
        state: AppUiState,
        onLine: (String) -> Unit,
        onProgressLine: (String?) -> Unit
    ): InstallationResult {
        if (!state.hasWinget) {
            return InstallationResult(
                success = false,
                completionMessage = "WinGet 없음"
            )
        }

        val wingetExecutable =
            platformResolver.findCommandPath(OsType.WINDOWS, "winget") ?: "winget"

        val commonArgs = listOf(
            "-e",
            "--id", "Streamlink.Streamlink",
            "--source", "winget",
            "--accept-source-agreements",
            "--accept-package-agreements",
            "--disable-interactivity"
        )

        val command = if (state.hasStreamlink) {
            listOf(wingetExecutable, "upgrade") + commonArgs
        } else {
            listOf(wingetExecutable, "install") + commonArgs
        }

        return runPackageManagerCommand(
            startLabel = "WinGet으로",
            state = state,
            command = command,
            onLine = onLine,
            onProgressLine = onProgressLine,
            classifier = ::classifyWingetResult
        )
    }

    private fun classifyWingetResult(
        state: AppUiState,
        result: CommandResult
    ): InstallationResult {
        val output = result.fullOutput.lowercase()

        if (result.exitCode == 0) {
            return InstallationResult(
                success = true,
                completionMessage = if (state.hasStreamlink) {
                    "WinGet으로 Streamlink 업데이트 완료"
                } else {
                    "WinGet으로 Streamlink 설치 완료"
                }
            )
        }

        if (
            state.hasStreamlink &&
            (
                    "no available upgrade found" in output ||
                            "no newer package versions are available" in output ||
                            "no applicable upgrade found" in output
                    )
        ) {
            return InstallationResult(
                success = true,
                completionMessage = "WinGet 기준 Streamlink는 이미 최신 버전입니다."
            )
        }

        return InstallationResult(
            success = false,
            completionMessage = if (state.hasStreamlink) {
                "WinGet 업데이트 실패"
            } else {
                "WinGet 설치 실패"
            }
        )
    }
}