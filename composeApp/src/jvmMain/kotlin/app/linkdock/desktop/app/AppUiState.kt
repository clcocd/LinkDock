package app.linkdock.desktop.app

import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.domain.ThemeMode
import app.linkdock.desktop.download.DownloadProgressInfo

enum class EnvironmentSource {
    UNKNOWN,
    CACHED,
    VERIFIED
}

data class AppUiState(

    val selectedService: ServiceType? = null,
    val email: String = "",
    val password: String = "",
    val url: String = "",
    val outputDir: String = "",
    val quality: String = "best",
    val isDownloading: Boolean = false,
    val isInstalling: Boolean = false,

    // 수동 버튼으로 돌리는 검사
    val isCheckingEnvironment: Boolean = false,

    // 앱 시작 시 자동으로 도는 백그라운드 검사
    val isRefreshingEnvironment: Boolean = false,

    val installProgressText: String? = null,
    val downloadProgress: DownloadProgressInfo? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val osType: OsType? = null,
    val hasStreamlink: Boolean = false,
    val hasBrew: Boolean = false,
    val hasWinget: Boolean = false,
    val environmentSource: EnvironmentSource = EnvironmentSource.UNKNOWN,

    val lastEnvironmentCheckedAtEpochMillis: Long? = null,

    val statusMessage: String = "대기 중",
    val logs: List<String> = listOf("LinkDock 시작됨")
)