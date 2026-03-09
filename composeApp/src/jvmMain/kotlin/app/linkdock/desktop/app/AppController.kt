package app.linkdock.desktop.app

import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.domain.ThemeMode
import app.linkdock.desktop.download.DownloadCommandFactory
import app.linkdock.desktop.environment.EnvironmentInspector
import app.linkdock.desktop.install.StreamlinkInstaller
import app.linkdock.desktop.platform.PlatformResolver
import app.linkdock.desktop.platform.DirectoryPicker
import app.linkdock.desktop.install.PluginInstaller
import app.linkdock.desktop.download.DownloadProgressInfo
import app.linkdock.desktop.download.StreamlinkProgressParser
import app.linkdock.desktop.storage.EnvCheckCache
import app.linkdock.desktop.storage.EnvCheckStore
import app.linkdock.desktop.storage.AppSettings
import app.linkdock.desktop.storage.AppSettingsStore
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppController {

    private val platformResolver = PlatformResolver()

    private val commandRunner = CommandRunner()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val environmentInspector = EnvironmentInspector(platformResolver, commandRunner)

    private val downloadCommandFactory = DownloadCommandFactory(platformResolver)

    private val streamlinkInstaller = StreamlinkInstaller(platformResolver, commandRunner)

    private val pluginInstaller = PluginInstaller(platformResolver)

    private val envCheckStore = EnvCheckStore()

    private val appSettingsStore = AppSettingsStore(platformResolver)

    @Volatile
    private var currentDownloadProcess: Process? = null

    @Volatile
    private var downloadStopRequested: Boolean = false

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        restoreCachedEnvironmentState()
        startBackgroundEnvironmentRefresh()
        restoreSavedOutputDirectoryOrDefault()
    }

    private fun isInputLocked(state: AppUiState = _uiState.value): Boolean {
        return state.isDownloading || state.isInstalling || state.isCheckingEnvironment
    }

    private fun isEnvironmentVerified(state: AppUiState = _uiState.value): Boolean {
        return state.environmentSource == EnvironmentSource.VERIFIED
    }

    private fun hasVerifiedStreamlink(state: AppUiState = _uiState.value): Boolean {
        return isEnvironmentVerified(state) && state.hasStreamlink
    }

    private fun removeHangul(value: String): String {
        return value.replace(Regex("[ㄱ-ㅎㅏ-ㅣ가-힣]"), "")
    }

    private fun restoreCachedEnvironmentState() {
        val cache = envCheckStore.load() ?: return

        _uiState.update { current ->
            current.copy(
                osType = cache.osType,
                hasStreamlink = cache.hasStreamlink,
                hasBrew = cache.hasBrew,
                hasWinget = cache.hasWinget,
                environmentSource = EnvironmentSource.CACHED,
                lastEnvironmentCheckedAtEpochMillis = cache.checkedAtEpochMillis,
                statusMessage = if (cache.hasStreamlink) {
                    "이전 환경 검사 결과 불러옴"
                } else {
                    "이전 환경 검사 결과: Streamlink 없음"
                }
            )
        }

        appendLog("이전 환경 검사 결과를 불러왔습니다.")
    }

    private fun startBackgroundEnvironmentRefresh() {
        scope.launch {
            delay(400)
            runEnvironmentCheck(
                startNewSession = false,
                userInitiated = false,
                silentIfBusy = true
            )
        }
    }

    private fun restoreSavedOutputDirectoryOrDefault() {
        val savedPath = appSettingsStore.load()?.lastSavePath
        val defaultPath = platformResolver.resolveOutputDir("")

        val savedPathInvalid =
            !savedPath.isNullOrBlank() && !isUsableDirectory(savedPath)

        val visiblePath = when {
            !savedPath.isNullOrBlank() && !savedPathInvalid -> savedPath
            else -> defaultPath
        }

        if (savedPathInvalid) {
            appendLog("저장된 경로를 사용할 수 없어 기본 다운로드 폴더를 사용합니다.")
        }

        _uiState.value = _uiState.value.copy(outputDir = visiblePath)
    }

    private fun isUsableDirectory(path: String): Boolean {
        return runCatching {
            val dir = File(path)
            dir.exists() && dir.isDirectory && dir.canWrite()
        }.getOrDefault(false)
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

        val sanitized = removeHangul(value)

        _uiState.value = _uiState.value.copy(email = sanitized)
    }

    fun updatePassword(value: String) {
        if (isInputLocked()) return

        val sanitized = removeHangul(value)

        _uiState.value = _uiState.value.copy(password = sanitized)
    }

    fun updateUrl(value: String) {
        if (isInputLocked()) return

        val sanitized = removeHangul(value)

        _uiState.value = _uiState.value.copy(url = sanitized)
    }

    fun updateOutputDir(value: String) {
        if (isInputLocked()) return

        _uiState.value = _uiState.value.copy(outputDir = value)

        appSettingsStore.save(
            AppSettings(lastSavePath = value)
        )
    }

    fun runEnvironmentCheck() {
        runEnvironmentCheck(
            startNewSession = true,
            userInitiated = true

        )
    }

    private fun runEnvironmentCheck(
        startNewSession: Boolean,
        userInitiated: Boolean,
        showPostInstallHint: Boolean = false,
        silentIfBusy: Boolean = false
    ) {
        val state = _uiState.value

        val busy =
            state.isDownloading ||
                    state.isInstalling ||
                    state.isCheckingEnvironment ||
                    state.isRefreshingEnvironment

        if (busy) {
            if (!silentIfBusy) {
                appendLog("작업 중에는 환경 검사를 실행하지 않습니다.")
            }
            return
        }

        scope.launch {
            _uiState.update { current ->
                if (userInitiated) {
                    current.copy(isCheckingEnvironment = true)
                } else {
                    current.copy(isRefreshingEnvironment = true)
                }
            }

            try {
                if (userInitiated) {
                    if (startNewSession) {
                        startNewLogSession("환경 검사 시작")
                    } else {
                        appendLog("환경 다시 검사 시작")
                    }
                    setStatus("환경 확인 중...")
                }

                val result = environmentInspector.inspect()

                if (userInitiated) {
                    result.logs.forEach { log ->
                        appendLog(log)
                    }
                }

                updateEnvironmentState(
                    osType = result.osType,
                    hasStreamlink = result.hasStreamlink,
                    hasBrew = result.hasBrew,
                    hasWinget = result.hasWinget
                )

                envCheckStore.save(
                    EnvCheckCache(
                        checkedAtEpochMillis = System.currentTimeMillis(),
                        osType = result.osType,
                        hasStreamlink = result.hasStreamlink,
                        hasBrew = result.hasBrew,
                        hasWinget = result.hasWinget
                    )
                )

                if (
                    showPostInstallHint &&
                    result.osType == OsType.WINDOWS &&
                    !result.hasStreamlink
                ) {
                    appendLog("Streamlink 설치는 완료되었지만 현재 앱에서 아직 인식되지 않았습니다.")
                    appendLog("이 경우 앱을 종료한 뒤 다시 실행하면 정상 반영될 수 있습니다.")
                    setStatus("설치 완료, 앱 재실행 필요할 수 있음")
                } else if (userInitiated) {
                    setStatus("환경 검사 완료")
                    appendLog("환경 검사 완료")
                }
            } catch (t: Throwable) {
                val errorMessage = t.message?.takeIf { it.isNotBlank() }
                    ?: t::class.simpleName
                    ?: "알 수 없는 오류"

                if (userInitiated) {
                    appendLog("환경 검사 중 오류 발생: $errorMessage")
                }

                setStatus("환경 검사 실패")
            } finally {
                _uiState.update { current ->
                    if (userInitiated) {
                        current.copy(isCheckingEnvironment = false)
                    } else {
                        current.copy(isRefreshingEnvironment = false)
                    }
                }
            }
        }
    }

    fun installOrUpdateStreamlink() {
        val state = _uiState.value

        if (state.isDownloading || state.isInstalling || state.isCheckingEnvironment || state.isRefreshingEnvironment) {
            appendLog("다른 작업이 진행 중입니다.")
            return
        }

        val verifiedHasStreamlink = hasVerifiedStreamlink(state)

        val installActionLabel = when {
            verifiedHasStreamlink -> "업데이트"
            isEnvironmentVerified(state) -> "설치"
            else -> "설치/업데이트"
        }

        val installState = state.copy(
            hasStreamlink = verifiedHasStreamlink
        )

        scope.launch {
            startNewLogSession("Streamlink $installActionLabel 시작")

            setStatus("Streamlink $installActionLabel 준비 중...")

            _uiState.update { current ->
                current.copy(
                    isInstalling = true,
                    installProgressText = null
                )
            }

            try {
                val streamlinkResult = streamlinkInstaller.installOrUpdate(
                    state = installState,
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

                setInstallProgressText(null)
                setStatus("플러그인 설치/업데이트 중...")
                appendLog("플러그인 설치/업데이트 시작")

                val pluginResult = pluginInstaller.installOrUpdate(
                    state = state,
                    onLine = { line ->
                        appendLog(line)
                    }
                )

                appendLog(pluginResult.completionMessage)

                if (!pluginResult.success) {
                    setStatus(pluginResult.completionMessage)
                    return@launch
                }

                appendLog("설치 / 업데이트 완료")
            } finally {
                _uiState.update { current ->
                    current.copy(
                        isInstalling = false,
                        installProgressText = null
                    )
                }
            }

            appendLogSection("설치 후 환경 다시 검사")
            setStatus("환경 다시 검사 중...")
            runEnvironmentCheck(
                startNewSession = false,
                userInitiated = true,
                showPostInstallHint = true
            )
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
            appendLog("환경 자동 확인이 아직 끝나지 않았습니다.")
            appendLog("잠시 후 다시 시도하거나 환경 다시 확인을 눌러주세요.")
            _uiState.update { current ->
                current.copy(statusMessage = "환경 확인 필요")
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
            return
        }

        if (!state.hasStreamlink) {
            startNewLogSession("다운로드 시작 실패")
            appendLog("Streamlink가 현재 앱에서 인식되지 않습니다.")
            appendLog("먼저 설치하거나 앱을 다시 시작한 뒤 다시 시도해주세요.")
            _uiState.update { current -> current.copy(statusMessage = "Streamlink 필요") }
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
            downloadStopRequested = false
            currentDownloadProcess = null

            _uiState.update { current ->
                current.copy(
                    isDownloading = false,
                    downloadProgress = null
                )
            }

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

    private fun updateEnvironmentState(
        osType: OsType,
        hasStreamlink: Boolean,
        hasBrew: Boolean = false,
        hasWinget: Boolean = false
    ) {
        _uiState.update { current ->
            current.copy(
                osType = osType,
                hasStreamlink = hasStreamlink,
                hasBrew = hasBrew,
                hasWinget = hasWinget,
                environmentSource = EnvironmentSource.VERIFIED,
                lastEnvironmentCheckedAtEpochMillis = System.currentTimeMillis()
            )
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