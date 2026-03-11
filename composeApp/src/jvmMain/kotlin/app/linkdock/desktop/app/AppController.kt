package app.linkdock.desktop.app

import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.domain.ThemeMode
import app.linkdock.desktop.download.DownloadCommandFactory
import app.linkdock.desktop.download.DownloadProgressInfo
import app.linkdock.desktop.download.StreamlinkProgressParser
import app.linkdock.desktop.download.getUnsupportedServiceUrlMessage
import app.linkdock.desktop.install.PluginInstallOutcome
import app.linkdock.desktop.install.PluginInstaller
import app.linkdock.desktop.install.StreamlinkInstaller
import app.linkdock.desktop.platform.DirectoryPicker
import app.linkdock.desktop.platform.PlatformResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class AppController {

    private val platformResolver = PlatformResolver()

    private val commandRunner = CommandRunner()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val downloadCommandFactory = DownloadCommandFactory(platformResolver)

    private val streamlinkInstaller = StreamlinkInstaller(platformResolver, commandRunner)

    private val pluginInstaller = PluginInstaller(platformResolver)

    private val appPreferencesService = AppPreferencesService(platformResolver)

    @Volatile
    private var currentDownloadProcess: Process? = null

    @Volatile
    private var downloadStopRequested: Boolean = false

    private val _uiState = MutableStateFlow(AppUiState())

    private val environmentCheckCoordinator = EnvironmentCheckCoordinator(
        scope = scope,
        platformResolver = platformResolver,
        commandRunner = commandRunner,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        appendLog = ::appendLog,
        startNewLogSession = ::startNewLogSession,
        setStatus = ::setStatus
    )

    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        environmentCheckCoordinator.restoreCachedEnvironmentState()
        environmentCheckCoordinator.startBackgroundEnvironmentRefresh()
        restoreSavedOutputDirectoryOrDefault()
        prepareReleaseNotesDialog()
    }

    private fun prepareReleaseNotesDialog() {
        val currentReleaseNote = appPreferencesService.getStartupReleaseNote() ?: return

        _uiState.update { current ->
            current.copy(
                releaseNoteToShow = currentReleaseNote,
                releaseNotesDialogMode = ReleaseNotesDialogMode.RECENT
            )
        }
    }

    fun dismissReleaseNotesDialog() {
        val saveSucceeded = appPreferencesService.markCurrentReleaseNotesAsSeen()

        if (!saveSucceeded) {
            appendLog("설정 저장 실패: 릴리스 노트 확인 상태를 기록하지 못했습니다.")
        }

        _uiState.update { current ->
            current.copy(
                releaseNoteToShow = null,
                releaseNotesDialogMode = ReleaseNotesDialogMode.RECENT
            )
        }
    }

    fun showReleaseNotesDialog() {
        val currentReleaseNote = appPreferencesService.getCurrentReleaseNote() ?: return

        _uiState.update { current ->
            current.copy(
                releaseNoteToShow = currentReleaseNote,
                releaseNotesDialogMode = ReleaseNotesDialogMode.ALL
            )
        }
    }

    private fun isInputLocked(state: AppUiState = _uiState.value): Boolean {
        return state.isDownloading || state.isInstalling || state.isCheckingEnvironment
    }

    private fun isEnvironmentVerified(state: AppUiState = _uiState.value): Boolean {
        return state.environmentSource == EnvironmentSource.VERIFIED
    }

    private fun removeHangul(value: String): String {
        return value.replace(Regex("[ㄱ-ㅎㅏ-ㅣ가-힣]"), "")
    }

    private fun resolveHangulRejectedField(
        current: AppUiState,
        field: HangulRejectedField,
        original: String,
        sanitized: String
    ): HangulRejectedField? {
        return when {
            sanitized != original -> field
            current.hangulRejectedField == field -> null
            else -> current.hangulRejectedField
        }
    }

    private fun restoreSavedOutputDirectoryOrDefault() {
        val restoreResult = appPreferencesService.restoreOutputDirectory()

        restoreResult.warningLog?.let { warning ->
            appendLog(warning)
        }

        _uiState.value = _uiState.value.copy(
            outputDir = restoreResult.visiblePath
        )
    }

    fun setThemeMode(themeMode: ThemeMode) {
        val current = _uiState.value

        _uiState.value = current.copy(themeMode = themeMode)
        appendLog("테마 모드 변경: $themeMode")
    }

    fun browseOutputDirectory() {
        val current = _uiState.value

        if (isInputLocked(current)) {
            appendLog("작업 중에는 저장 경로를 변경할 수 없습니다.")
            return
        }

        val selectedPath = DirectoryPicker.pickDirectory(current.outputDir)

        if (!selectedPath.isNullOrBlank()) {
            updateOutputDir(selectedPath)
            appendLog("저장 경로 선택: $selectedPath")
            setStatus("저장 경로 선택 완료")
        }
    }

    fun updateService(serviceType: ServiceType) {
        val current = _uiState.value

        if (isInputLocked(current)) {
            appendLog("작업 중에는 서비스를 변경할 수 없습니다.")
            return
        }

        if (current.selectedService == serviceType) return

        _uiState.value = current.copy(
            selectedService = serviceType,
            logs = listOf("${serviceType.displayName} 로그 세션 시작"),
            statusMessage = "${serviceType.displayName} 선택됨"
        )
    }

    fun updateEmail(value: String) {
        if (isInputLocked()) return

        val current = _uiState.value
        val sanitized = removeHangul(value)

        _uiState.value = current.copy(
            email = sanitized,
            hangulRejectedField = resolveHangulRejectedField(
                current = current,
                field = HangulRejectedField.EMAIL,
                original = value,
                sanitized = sanitized
            )
        )
    }

    fun updatePassword(value: String) {
        if (isInputLocked()) return

        val current = _uiState.value
        val sanitized = removeHangul(value)

        _uiState.value = current.copy(
            password = sanitized,
            hangulRejectedField = resolveHangulRejectedField(
                current = current,
                field = HangulRejectedField.PASSWORD,
                original = value,
                sanitized = sanitized
            )
        )
    }

    fun updateUrl(value: String) {
        if (isInputLocked()) return

        val current = _uiState.value
        val sanitized = removeHangul(value)

        _uiState.value = current.copy(
            url = sanitized,
            hangulRejectedField = resolveHangulRejectedField(
                current = current,
                field = HangulRejectedField.URL,
                original = value,
                sanitized = sanitized
            )
        )
    }

    fun updateOutputDir(value: String) {
        if (isInputLocked()) return

        _uiState.value = _uiState.value.copy(outputDir = value)

        val saveSucceeded = appPreferencesService.saveOutputDirectory(value)

        if (!saveSucceeded) {
            appendLog("설정 저장 실패: 저장 경로를 기록하지 못했습니다.")
        }
    }

    fun runEnvironmentCheck() {
        environmentCheckCoordinator.runUserEnvironmentCheck()
    }


    fun installOrUpdateStreamlink() {
        val state = _uiState.value

        if (state.isDownloading || state.isInstalling || state.isCheckingEnvironment || state.isRefreshingEnvironment) {
            appendLog("다른 작업이 진행 중입니다.")
            return
        }

        scope.launch {
            startNewLogSession("Streamlink / FFmpeg 설치/업데이트 시작")
            setStatus("Streamlink / FFmpeg 설치/업데이트 준비 중...")

            _uiState.update { current ->
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
                    val pluginStatus = when (pluginResult.outcome) {
                        PluginInstallOutcome.UPDATED -> "플러그인 업데이트가 완료되었습니다."
                        PluginInstallOutcome.NO_CHANGES -> "플러그인 확인이 완료되었습니다."
                        null -> null
                    }

                    val finalStatus = buildString {
                        append(streamlinkResult.completionMessage)
                        if (!pluginStatus.isNullOrBlank()) {
                            append("\n")
                            append(pluginStatus)
                        }
                    }

                    appendLog(finalStatus)
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
                _uiState.update { current ->
                    current.copy(
                        isInstalling = false,
                        installProgressText = null
                    )
                }
            }

            if (shouldRunPostInstallCheck) {
                if (shouldRunSilentEnvironmentRefresh) {
                    appendLog("플러그인 확인은 실패했습니다.\nStreamlink / FFmpeg 상태는 다시 확인합니다.")
                    environmentCheckCoordinator.runSilentEnvironmentRefresh()
                } else {
                    _uiState.update { current ->
                        current.copy(postInstallState = PostInstallState.VERIFYING)
                    }

                    appendLogSection("설치 다시 확인")
                    setStatus("설치 반영 확인 중...")
                    environmentCheckCoordinator.runPostInstallVerification()
                }
            }
        }
    }

    fun startDownload() {
        val state = _uiState.value

        if (state.isDownloading || state.isInstalling || state.isCheckingEnvironment || state.isRefreshingEnvironment) {
            appendLog("다른 작업이 진행 중입니다.")
            return
        }

        if (!isEnvironmentVerified(state)) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("설치 확인이 아직 끝나지 않았습니다.")
            appendLog("잠시 후 다시 시도하거나 설치 확인을 눌러주세요.")
            _uiState.update { current ->
                current.copy(statusMessage = "설치 확인 필요")
            }
            return
        }

        if (state.selectedService == null) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("서비스가 선택되지 않았습니다.")
            _uiState.update { current ->
                current.copy(statusMessage = "서비스 선택 필요")
            }
            return
        }

        if (state.email.isBlank()) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("이메일이 비어 있습니다.")
            _uiState.update { current -> current.copy(statusMessage = "이메일 필요") }
            return
        }

        if (state.password.isBlank()) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("비밀번호가 비어 있습니다.")
            _uiState.update { current -> current.copy(statusMessage = "비밀번호 필요") }
            return
        }

        if (state.url.isBlank()) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("URL이 비어 있습니다.")
            _uiState.update { current -> current.copy(statusMessage = "URL 필요") }

            val unsupportedUrlMessage =
                getUnsupportedServiceUrlMessage(state.selectedService, state.url)

            if (unsupportedUrlMessage != null) {
                startNewLogSession("다운로드 시작 실패")
                appendLog(unsupportedUrlMessage)
                _uiState.update { current -> current.copy(statusMessage = "지원하지 않는 URL") }
                return
            }

            return
        }

        if (!state.hasStreamlink) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("Streamlink가 현재 앱에서 인식되지 않습니다.")
            appendLog("먼저 설치하거나 앱을 다시 시작한 뒤 다시 시도해주세요.")
            _uiState.update { current -> current.copy(statusMessage = "Streamlink 필요") }
            return
        }

        if (!state.hasFfmpeg) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("FFmpeg가 현재 앱에서 인식되지 않습니다.")
            appendLog("먼저 설치/업데이트를 실행하거나 앱을 다시 시작한 뒤 다시 시도해주세요.")
            _uiState.update { current -> current.copy(statusMessage = "FFmpeg 필요") }
            return
        }

        val buildResult = downloadCommandFactory.build(state)
        val command = buildResult.command

        if (command == null) {
            startNewLogSession("다운로드 시작 실패")
            appendLog(buildResult.errorMessage ?: "다운로드 명령을 생성하지 못했습니다.")
            _uiState.update { current -> current.copy(statusMessage = "명령 생성 실패") }
            return
        }

        scope.launch {
            downloadStopRequested = false
            currentDownloadProcess = null
            var skippedBecauseFileExists = false

            try {
                startNewLogSession("다운로드 시작")
                appendLog("서비스: ${state.selectedService.displayName}")
                appendLog("URL: ${state.url}")
                appendLog("저장 경로: ${buildResult.resolvedOutputDir}")
                appendLog("화질: ${state.quality}")

                _uiState.update { current ->
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

                _uiState.update { current ->
                    current.copy(
                        isDownloading = false,
                        downloadProgress = null
                    )
                }
            }
        }
    }

    fun stopDownload() {
        val state = _uiState.value

        if (!state.isDownloading) {
            appendLog("중지할 다운로드가 없습니다.")
            return
        }

        downloadStopRequested = true
        setStatus("다운로드 중지 요청 중...")
        appendLog("다운로드 중지 요청")

        currentDownloadProcess?.destroy()
    }

    private fun setStatus(message: String) {
        _uiState.update { current ->
            current.copy(statusMessage = message)
        }
    }

    private fun appendLog(message: String) {
        _uiState.update { current ->
            current.copy(logs = current.logs + message)
        }
    }

    private fun appendLogSection(title: String) {
        val hasLogs = _uiState.value.logs.isNotEmpty()
        if (hasLogs) {
            appendLog("")
        }
        appendLog("────────── $title ──────────")
    }

    private fun startNewLogSession(title: String) {
        _uiState.update { current ->
            current.copy(logs = listOf(title))
        }
    }

    private fun setInstallProgressText(text: String?) {
        _uiState.update { current ->
            current.copy(installProgressText = text)
        }
    }

    private fun setDownloadProgress(progress: DownloadProgressInfo?) {
        _uiState.update { current ->
            current.copy(downloadProgress = progress)
        }
    }
}