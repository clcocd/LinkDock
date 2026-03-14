package app.linkdock.desktop.app

import app.linkdock.desktop.install.PluginInstaller
import app.linkdock.desktop.install.StreamlinkInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class InstallationCoordinator(
    private val scope: CoroutineScope,
    private val streamlinkInstaller: StreamlinkInstaller,
    private val pluginInstaller: PluginInstaller,
    private val getState: () -> AppUiState,
    private val updateState: (transform: (AppUiState) -> AppUiState) -> Unit,
    private val appendLog: (String) -> Unit,
    private val appendLogSection: (String) -> Unit,
    private val startNewLogSession: (String) -> Unit,
    private val setStatus: (String) -> Unit,
    private val setInstallProgressText: (String?) -> Unit,
    private val runSilentEnvironmentRefresh: () -> Unit,
    private val runPostInstallVerification: () -> Unit
) {
    fun installOrUpdateStreamlink() {
        val state = getState()

        if (
            state.isPreparingDownload ||
            state.isDownloading ||
            state.isInstalling ||
            state.isCheckingEnvironment ||
            state.isRefreshingEnvironment
        ) {
            appendLog("다른 작업이 진행 중입니다.")
            return
        }

        scope.launch {
            startNewLogSession("Streamlink / FFmpeg 설치/업데이트 시작")
            setStatus("Streamlink / FFmpeg 설치/업데이트 준비 중...")

            updateState { current ->
                current.copy(
                    isInstalling = true,
                    installProgressText = null,
                    postInstallState = PostInstallState.NONE
                )
            }

            var shouldRunPostInstallCheck: Boolean
            var shouldRunSilentEnvironmentRefresh = false

            try {
                val streamlinkResult = streamlinkInstaller.installOrUpdate(
                    state = state,
                    onLine = { line ->
                        appendLog(line)
                    },
                    onProgressLine = { progressLine ->
                        setInstallProgressText(progressLine)
                    }
                )

                appendLog(streamlinkResult.completionMessage)

                if (!streamlinkResult.success) {
                    setStatus(streamlinkResult.completionMessage)
                    return@launch
                }

                shouldRunPostInstallCheck = true

                setInstallProgressText(null)
                setStatus("플러그인 확인 중...")
                appendLog("플러그인 확인 시작")

                val pluginResult = pluginInstaller.installOrUpdate(
                    state = state,
                    onLine = { line ->
                        appendLog(line)
                    }
                )

                appendLog(pluginResult.completionMessage)

                if (!pluginResult.success) {
                    setStatus(pluginResult.completionMessage)
                    shouldRunSilentEnvironmentRefresh = true
                } else {
                    val finalStatus = buildString {
                        append(streamlinkResult.completionMessage)
                        append("\n")
                        append(pluginResult.completionMessage)
                    }

                    setStatus(finalStatus)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                    ?: "알 수 없는 오류"

                appendLog("설치/업데이트 중 오류 발생: $errorMessage")
                setStatus("설치/업데이트 실패")
                shouldRunPostInstallCheck = false
                shouldRunSilentEnvironmentRefresh = false
            } finally {
                updateState { current ->
                    current.copy(
                        isInstalling = false,
                        installProgressText = null
                    )
                }
            }

            if (shouldRunPostInstallCheck) {
                if (shouldRunSilentEnvironmentRefresh) {
                    appendLog("플러그인 확인은 실패했습니다.\nStreamlink / FFmpeg 상태는 다시 확인합니다.")
                    runSilentEnvironmentRefresh()
                } else {
                    updateState { current ->
                        current.copy(postInstallState = PostInstallState.VERIFYING)
                    }

                    appendLogSection("설치 다시 확인")
                    setStatus("설치 반영 확인 중...")
                    runPostInstallVerification()
                }
            }
        }
    }
}