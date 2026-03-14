package app.linkdock.desktop.app

import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.environment.EnvironmentInspectionResult
import app.linkdock.desktop.environment.EnvironmentInspector
import app.linkdock.desktop.platform.PlatformResolver
import app.linkdock.desktop.storage.EnvCheckCache
import app.linkdock.desktop.storage.EnvCheckStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EnvironmentCheckCoordinator(
    private val scope: CoroutineScope,
    platformResolver: PlatformResolver,
    commandRunner: CommandRunner,
    private val getState: () -> AppUiState,
    private val updateState: (transform: (AppUiState) -> AppUiState) -> Unit,
    private val appendLog: (String) -> Unit,
    private val startNewLogSession: (String) -> Unit,
    private val setStatus: (String) -> Unit
) {
    private val environmentInspector = EnvironmentInspector(platformResolver, commandRunner)
    private val envCheckStore = EnvCheckStore(platformResolver)

    private var backgroundEnvironmentRefreshJob: Job? = null
    private var environmentCheckJob: Job? = null

    @Volatile
    private var environmentCheckJobUserInitiated: Boolean = false

    fun restoreCachedEnvironmentState() {
        val cache = envCheckStore.load() ?: return

        updateState { current ->
            current.copy(
                osType = cache.osType,
                hasStreamlink = cache.hasStreamlink,
                hasFfmpeg = cache.hasFfmpeg,
                hasBrew = cache.hasBrew,
                hasWinget = cache.hasWinget,
                environmentSource = EnvironmentSource.CACHED,
                lastEnvironmentCheckedAtEpochMillis = cache.checkedAtEpochMillis
            )
        }
    }

    fun startBackgroundEnvironmentRefresh() {
        backgroundEnvironmentRefreshJob?.cancel()

        backgroundEnvironmentRefreshJob = scope.launch {
            while (isActive) {
                val state = getState()
                val environmentCheckRunning = environmentCheckJob?.isActive == true

                val busy =
                    state.isPreparingDownload ||
                            state.isDownloading ||
                            state.isInstalling ||
                            state.isCheckingEnvironment ||
                            state.isRefreshingEnvironment ||
                            environmentCheckRunning

                if (!busy) {
                    runEnvironmentCheck(
                        startNewSession = false,
                        userInitiated = false,
                        silentIfBusy = true
                    )
                    return@launch
                }

                delay(1000)
            }
        }
    }

    fun runUserEnvironmentCheck() {
        runEnvironmentCheck(
            startNewSession = true,
            userInitiated = true
        )
    }

    fun runSilentEnvironmentRefresh() {
        runEnvironmentCheck(
            startNewSession = false,
            userInitiated = false,
            showPostInstallHint = false,
            silentIfBusy = true
        )
    }

    fun runPostInstallVerification() {
        runEnvironmentCheck(
            startNewSession = false,
            userInitiated = true,
            showPostInstallHint = true
        )
    }

    private fun runEnvironmentCheck(
        startNewSession: Boolean,
        userInitiated: Boolean,
        showPostInstallHint: Boolean = false,
        silentIfBusy: Boolean = false
    ) {
        val previousEnvironmentCheckJob = environmentCheckJob
        val previousEnvironmentCheckJobUserInitiated = environmentCheckJobUserInitiated

        val job = scope.launch {
            if (userInitiated) {
                backgroundEnvironmentRefreshJob?.cancelAndJoin()
                backgroundEnvironmentRefreshJob = null

                if (
                    previousEnvironmentCheckJob != null &&
                    previousEnvironmentCheckJob.isActive &&
                    !previousEnvironmentCheckJobUserInitiated
                ) {
                    previousEnvironmentCheckJob.cancelAndJoin()
                }
            }

            val state = getState()
            val anotherEnvironmentCheckRunning =
                previousEnvironmentCheckJob != null &&
                        previousEnvironmentCheckJob.isActive &&
                        previousEnvironmentCheckJob !== coroutineContext[Job]

            val busy =
                state.isPreparingDownload ||
                        state.isDownloading ||
                        state.isInstalling ||
                        state.isCheckingEnvironment ||
                        state.isRefreshingEnvironment ||
                        anotherEnvironmentCheckRunning

            if (busy) {
                if (!silentIfBusy) {
                    appendLog("작업 중에는 설치 확인을 실행하지 않습니다.")
                }
                return@launch
            }

            updateState { current ->
                if (userInitiated) {
                    current.copy(isCheckingEnvironment = true)
                } else {
                    current.copy(isRefreshingEnvironment = true)
                }
            }

            try {
                if (userInitiated) {
                    if (startNewSession) {
                        startNewLogSession("설치 확인 시작")
                    } else {
                        appendLog("설치 다시 확인 시작")
                    }
                    setStatus("설치 확인 중...")
                }

                val result = environmentInspector.inspect()

                if (userInitiated) {
                    result.logs.forEach { log ->
                        appendLog(log)
                    }
                }

                applyEnvironmentResult(result)

                if (showPostInstallHint) {
                    val nextPostInstallState = when {
                        result.osType == OsType.WINDOWS &&
                                (!result.hasStreamlink || !result.hasFfmpeg) ->
                            PostInstallState.MAY_NEED_RESTART

                        !result.hasStreamlink || !result.hasFfmpeg ->
                            PostInstallState.NEEDS_RECHECK

                        else ->
                            PostInstallState.NONE
                    }

                    updateState { current ->
                        current.copy(postInstallState = nextPostInstallState)
                    }

                    when (nextPostInstallState) {
                        PostInstallState.MAY_NEED_RESTART -> {
                            appendLog("설치는 완료되었지만 현재 앱에서 아직 Streamlink 또는 FFmpeg가 인식되지 않았습니다.")
                            appendLog("이 경우 앱을 종료한 뒤 다시 실행하면 정상 반영될 수 있습니다.")
                            setStatus("설치 완료, 앱 재실행 필요할 수 있음")
                        }

                        PostInstallState.NEEDS_RECHECK -> {
                            appendLog("설치 후에도 아직 Streamlink 또는 FFmpeg가 감지되지 않았습니다.")
                            appendLog("잠시 후 설치 확인을 다시 실행해 주세요.")
                            setStatus("설치 후 다시 확인 필요")
                        }

                        PostInstallState.NONE -> {
                            setStatus("설치 후 상태 확인 완료")
                            appendLog("설치 후 상태 확인 완료")
                        }

                        PostInstallState.VERIFYING -> {
                            // 여기까지 오면 안 되므로 아무 처리 안 함
                        }
                    }
                } else if (userInitiated) {
                    updateState { current ->
                        current.copy(postInstallState = PostInstallState.NONE)
                    }
                    setStatus("설치 확인 완료")
                    appendLog("설치 확인 완료")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                    ?: "알 수 없는 오류"

                if (userInitiated || showPostInstallHint) {
                    appendLog("설치 확인 중 오류 발생: $errorMessage")
                }

                if (showPostInstallHint) {
                    updateState { current ->
                        current.copy(postInstallState = PostInstallState.NEEDS_RECHECK)
                    }
                    setStatus("설치 후 다시 확인 필요")
                } else if (userInitiated) {
                    setStatus("설치 확인 실패")
                }
            } finally {
                updateState { current ->
                    if (userInitiated) {
                        current.copy(isCheckingEnvironment = false)
                    } else {
                        current.copy(isRefreshingEnvironment = false)
                    }
                }

                if (environmentCheckJob === coroutineContext[Job]) {
                    environmentCheckJob = null
                }
            }
        }

        environmentCheckJobUserInitiated = userInitiated
        environmentCheckJob = job
    }

    private fun applyEnvironmentResult(result: EnvironmentInspectionResult) {
        val checkedAt = System.currentTimeMillis()

        updateState { current ->
            current.copy(
                osType = result.osType,
                hasStreamlink = result.hasStreamlink,
                hasFfmpeg = result.hasFfmpeg,
                hasBrew = result.hasBrew,
                hasWinget = result.hasWinget,
                environmentSource = EnvironmentSource.VERIFIED,
                lastEnvironmentCheckedAtEpochMillis = checkedAt
            )
        }

        envCheckStore.save(
            EnvCheckCache(
                checkedAtEpochMillis = checkedAt,
                osType = result.osType,
                hasStreamlink = result.hasStreamlink,
                hasFfmpeg = result.hasFfmpeg,
                hasBrew = result.hasBrew,
                hasWinget = result.hasWinget
            )
        )
    }
}