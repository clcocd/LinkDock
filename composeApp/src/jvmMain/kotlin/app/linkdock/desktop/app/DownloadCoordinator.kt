package app.linkdock.desktop.app

import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.download.DownloadCommandFactory
import app.linkdock.desktop.download.DownloadProgressInfo
import app.linkdock.desktop.download.SpwnProbeParser
import app.linkdock.desktop.download.StreamlinkProgressParser
import app.linkdock.desktop.platform.PlatformResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DownloadCoordinator(
    private val scope: CoroutineScope,
    platformResolver: PlatformResolver,
    private val commandRunner: CommandRunner,
    private val getState: () -> AppUiState,
    private val updateState: (transform: (AppUiState) -> AppUiState) -> Unit,
    private val appendLog: (String) -> Unit,
    private val startNewLogSession: (String) -> Unit,
    private val setStatus: (String) -> Unit
) {
    private val downloadCommandFactory = DownloadCommandFactory(platformResolver)

    @Volatile
    private var currentDownloadProcess: Process? = null

    @Volatile
    private var downloadStopRequested: Boolean = false

    fun startDownload() {
        val state = getState()

        if (state.isPreparingDownload || state.isDownloading || state.isInstalling || state.isCheckingEnvironment || state.isRefreshingEnvironment) {
            appendLog("다른 작업이 진행 중입니다.")
            return
        }

        if (!isEnvironmentVerified(state)) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("설치 확인이 아직 끝나지 않았습니다.")
            appendLog("잠시 후 다시 시도하거나 설치 확인을 시도해 주세요.")
            updateState { current ->
                current.copy(statusMessage = "설치 확인 필요")
            }
            return
        }

        if (state.selectedService == null) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("서비스가 선택되지 않았습니다.")
            updateState { current ->
                current.copy(statusMessage = "서비스 선택 필요")
            }
            return
        }

        if (state.email.isBlank()) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("이메일이 비어 있습니다.")
            updateState { current ->
                current.copy(statusMessage = "이메일 필요")
            }
            return
        }

        if (state.password.isBlank()) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("비밀번호가 비어 있습니다.")
            updateState { current ->
                current.copy(statusMessage = "비밀번호 필요")
            }
            return
        }

        if (state.url.isBlank()) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("URL이 비어 있습니다.")
            updateState { current ->
                current.copy(statusMessage = "URL 필요")
            }
            return
        }

        if (!state.hasStreamlink) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("Streamlink가 현재 앱에서 인식되지 않습니다.")
            appendLog("먼저 설치하거나 앱을 다시 시작한 뒤 시도해 주세요.")
            updateState { current ->
                current.copy(statusMessage = "Streamlink 필요")
            }
            return
        }

        if (!state.hasFfmpeg) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("FFmpeg가 현재 앱에서 인식되지 않습니다.")
            appendLog("먼저 설치/업데이트를 실행하거나 앱을 다시 시작한 뒤 시도해 주세요.")
            updateState { current ->
                current.copy(statusMessage = "FFmpeg 필요")
            }
            return
        }

        if (shouldRunSpwnProbe(state)) {
            runSpwnProbe(state)
            return
        }

        val finalStreamSelection = state.selectedSpwnPartStreamKey
        startActualDownload(state, finalStreamSelection)
    }

    private fun shouldRunSpwnProbe(state: AppUiState): Boolean {
        return state.selectedService == ServiceType.SPWN &&
                state.selectedSpwnPartStreamKey == null
    }

    private fun runSpwnProbe(state: AppUiState) {
        scope.launch {
            downloadStopRequested = false
            currentDownloadProcess = null

            startNewLogSession("SPWN 다운로드 항목 확인")
            appendLog("다운로드 가능한 항목을 확인하는 중...")

            updateState { current ->
                current.copy(
                    isPreparingDownload = true,
                    statusMessage = "다운로드 가능한 항목을 확인하는 중...",
                    showSpwnPartSelector = false,
                    spwnPartOptions = emptyList(),
                    selectedSpwnPartStreamKey = null,
                    selectedSpwnPartLabel = null,
                    downloadProgress = null
                )
            }

            try {
                val buildResult = downloadCommandFactory.buildProbe(state)
                val command = buildResult.command

                if (command == null) {
                    appendLog(buildResult.errorMessage ?: "다운로드할 항목 확인을 시작하지 못했습니다.")
                    setStatus("다운로드 항목 확인 실패")
                    return@launch
                }

                val probeLines = mutableListOf<String>()

                val result = commandRunner.runStreamingDownloadCommand(
                    command = command,
                    onStdoutLine = { line ->
                        probeLines += line
                        if (shouldExposeProbeLine(line)) {
                            appendLog(line)
                        }
                    },
                    onStderrLine = { line ->
                        probeLines += line
                        if (shouldExposeProbeLine(line)) {
                            appendLog(line)
                        }
                    },
                    onProcessStarted = { process ->
                        currentDownloadProcess = process
                    }
                )

                if (downloadStopRequested) {
                    setStatus("다운로드 준비 중지됨")
                    appendLog("다운로드 준비 중지됨")
                    return@launch
                }

                if (!result.success && probeLines.none { it.contains("Available streams:") }) {
                    setStatus("다운로드 항목 확인 실패")
                    appendLog("다운로드 가능한 항목을 확인하지 못했습니다.")
                    return@launch
                }

                val probeResult = SpwnProbeParser.parse(probeLines)

                if (probeResult.isMultiPart && probeResult.options.isNotEmpty()) {
                    val firstOption = probeResult.options.first()

                    updateState { current ->
                        current.copy(
                            isPreparingDownload = false,
                            showSpwnPartSelector = true,
                            spwnPartOptions = probeResult.options,
                            selectedSpwnPartStreamKey = firstOption.bestStreamKey,
                            selectedSpwnPartLabel = firstOption.displayLabel,
                            statusMessage = "다운로드할 VOD를 선택해 주세요."
                        )
                    }

                    appendLog("여러 VOD가 감지되었습니다. 받을 항목을 선택해 주세요.")
                    appendLog("기본 선택: ${firstOption.displayLabel}")
                    return@launch
                }

                updateState { current ->
                    current.copy(
                        isPreparingDownload = false,
                        showSpwnPartSelector = false,
                        spwnPartOptions = emptyList(),
                        selectedSpwnPartStreamKey = null,
                        selectedSpwnPartLabel = null
                    )
                }

                startActualDownload(getState(), streamSelectionOverride = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                    ?: "알 수 없는 오류"

                appendLog("다운로드 항목 확인 중 오류 발생: $errorMessage")
                setStatus("다운로드 항목 확인 실패")
            } finally {
                currentDownloadProcess = null
                downloadStopRequested = false

                updateState { current ->
                    if (current.isPreparingDownload) {
                        current.copy(isPreparingDownload = false)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun shouldExposeProbeLine(line: String): Boolean {
        return line.contains("Multi-part event:") ||
                line.contains("Available streams:") ||
                line.contains("error", ignoreCase = true)
    }

    private fun startActualDownload(
        state: AppUiState,
        streamSelectionOverride: String?
    ) {
        val buildResult = downloadCommandFactory.build(state, streamSelectionOverride)
        val command = buildResult.command

        if (command == null) {
            startNewLogSession("다운로드 시작 실패")
            appendLog(buildResult.errorMessage ?: "다운로드 명령을 생성하지 못했습니다.")
            updateState { current ->
                current.copy(statusMessage = "명령 생성 실패")
            }
            return
        }

        scope.launch {
            downloadStopRequested = false
            currentDownloadProcess = null
            var skippedBecauseFileExists = false

            try {
                startNewLogSession("다운로드 시작")
                appendLog("서비스: ${state.selectedService?.displayName ?: "알 수 없음"}")
                appendLog("URL: ${state.url}")
                appendLog("저장 경로: ${buildResult.resolvedOutputDir}")

                state.selectedSpwnPartLabel?.let {
                    appendLog("선택 항목: $it")
                }

                appendLog("선택 스트림: ${streamSelectionOverride ?: state.quality}")

                updateState { current ->
                    current.copy(
                        isDownloading = true,
                        statusMessage = "다운로드 중...",
                        downloadProgress = null
                    )
                }

                val result = commandRunner.runStreamingDownloadCommand(
                    command = command,
                    onStdoutLine = { line ->
                        if (line.contains("already exists", ignoreCase = true)) {
                            skippedBecauseFileExists = true
                        }

                        val progress = StreamlinkProgressParser.parse(line)

                        if (progress != null) {
                            setDownloadProgress(progress)
                        } else {
                            appendLog(line)
                        }
                    },
                    onStderrLine = { line ->
                        if (line.contains("already exists", ignoreCase = true)) {
                            skippedBecauseFileExists = true
                        }
                        appendLog(line)
                    },
                    onProcessStarted = { process ->
                        currentDownloadProcess = process
                    }
                )

                val stoppedByUser = downloadStopRequested

                when {
                    stoppedByUser -> {
                        setStatus("다운로드 중지됨")
                        appendLog("다운로드 중지됨")
                    }

                    skippedBecauseFileExists -> {
                        setStatus("이미 같은 파일이 있어 건너뜀")
                        appendLog("이미 같은 파일이 있어 건너뜀")
                    }

                    result.success -> {
                        setStatus("다운로드 완료")
                        appendLog("다운로드 완료")
                    }

                    else -> {
                        setStatus("다운로드 실패")
                        appendLog("다운로드 실패")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                    ?: "알 수 없는 오류"

                if (downloadStopRequested) {
                    setStatus("다운로드 중지됨")
                    appendLog("다운로드 중지됨")
                } else {
                    setStatus("다운로드 실패")
                    appendLog("다운로드 중 오류 발생: $errorMessage")
                }
            } finally {
                downloadStopRequested = false
                currentDownloadProcess = null

                updateState { current ->
                    current.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
            }
        }
    }

    fun stopDownload() {
        val state = getState()

        if (!state.isPreparingDownload && !state.isDownloading) {
            appendLog("중지할 작업이 없습니다.")
            return
        }

        downloadStopRequested = true
        setStatus(
            if (state.isPreparingDownload) {
                "다운로드 준비 중지 요청 중..."
            } else {
                "다운로드 중지 요청 중..."
            }
        )
        appendLog(
            if (state.isPreparingDownload) {
                "다운로드 준비 중지 요청"
            } else {
                "다운로드 중지 요청"
            }
        )

        currentDownloadProcess?.destroy()
    }

    private fun isEnvironmentVerified(state: AppUiState): Boolean {
        return state.environmentSource == EnvironmentSource.VERIFIED
    }

    private fun setDownloadProgress(progress: DownloadProgressInfo?) {
        updateState { current ->
            current.copy(downloadProgress = progress)
        }
    }
}