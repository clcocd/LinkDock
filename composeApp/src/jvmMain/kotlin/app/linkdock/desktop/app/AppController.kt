package app.linkdock.desktop.app

import app.linkdock.desktop.command.CommandRunner
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.domain.ThemeMode
import app.linkdock.desktop.install.PluginInstaller
import app.linkdock.desktop.install.StreamlinkInstaller
import app.linkdock.desktop.platform.DirectoryPicker
import app.linkdock.desktop.platform.PlatformResolver
import app.linkdock.desktop.download.SpwnPartOption
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class AppController {

    private val platformResolver = PlatformResolver()

    private val commandRunner = CommandRunner()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appPreferencesService = AppPreferencesService(platformResolver)

    private val streamlinkInstaller = StreamlinkInstaller(platformResolver, commandRunner)

    private val pluginInstaller = PluginInstaller(platformResolver)

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

    private val downloadCoordinator = DownloadCoordinator(
        scope = scope,
        platformResolver = platformResolver,
        commandRunner = commandRunner,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        appendLog = ::appendLog,
        startNewLogSession = ::startNewLogSession,
        setStatus = ::setStatus
    )

    private val installationCoordinator = InstallationCoordinator(
        scope = scope,
        streamlinkInstaller = streamlinkInstaller,
        pluginInstaller = pluginInstaller,
        getState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        appendLog = ::appendLog,
        appendLogSection = ::appendLogSection,
        startNewLogSession = ::startNewLogSession,
        setStatus = ::setStatus,
        setInstallProgressText = ::setInstallProgressText,
        runSilentEnvironmentRefresh = environmentCheckCoordinator::runSilentEnvironmentRefresh,
        runPostInstallVerification = environmentCheckCoordinator::runPostInstallVerification
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
        return state.isPreparingDownload || state.isDownloading || state.isInstalling || state.isCheckingEnvironment
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
            logs = listOf("${serviceType.displayName} 로그 세션 시작")
        ).clearSpwnSelection()
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
        ).clearSpwnSelection()
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
        installationCoordinator.installOrUpdateStreamlink()
    }

    fun startDownload() {
        downloadCoordinator.startDownload()
    }

    fun selectSpwnPart(option: SpwnPartOption) {
        val current = _uiState.value

        if (isInputLocked(current) || current.isRefreshingEnvironment) {
            return
        }

        if (option !in current.spwnPartOptions) {
            return
        }

        _uiState.update { state ->
            state.copy(
                selectedSpwnPartStreamKey = option.bestStreamKey,
                selectedSpwnPartLabel = option.displayLabel
            )
        }
    }

    fun stopDownload() {
        downloadCoordinator.stopDownload()
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
}