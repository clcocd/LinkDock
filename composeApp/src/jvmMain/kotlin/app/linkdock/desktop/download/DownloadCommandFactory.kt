package app.linkdock.desktop.download

import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.platform.PlatformResolver
import java.io.File

class DownloadCommandFactory(
    private val platformResolver: PlatformResolver
) {

    fun build(state: AppUiState): DownloadCommandBuildResult {
        val osType = state.osType ?: platformResolver.detectOsType()

        val selectedService = state.selectedService
            ?: return DownloadCommandBuildResult(
                command = null,
                resolvedOutputDir = null,
                errorMessage = "서비스가 선택되지 않았습니다."
            )

        val normalizedUrl = state.url.trim()

        val unsupportedUrlMessage = getUnsupportedServiceUrlMessage(selectedService, normalizedUrl)
        if (unsupportedUrlMessage != null) {
            return DownloadCommandBuildResult(
                command = null,
                resolvedOutputDir = null,
                errorMessage = unsupportedUrlMessage
            )
        }

        val streamlinkExecutable = platformResolver.resolveStreamlinkExecutable(osType)
            ?: return DownloadCommandBuildResult(
                command = null,
                errorMessage = "현재 운영체제에서는 다운로드를 지원하지 않습니다."
            )

        val pluginDir = platformResolver.resolveAppPluginDir(osType)
            ?: return DownloadCommandBuildResult(
                command = null,
                errorMessage = "플러그인 폴더 경로를 결정할 수 없습니다."
            )

        val selectedPluginFile = platformResolver.resolveManagedPluginFile(osType, selectedService)
            ?.let(::File)
            ?: return DownloadCommandBuildResult(
                command = null,
                errorMessage = "플러그인 파일 경로를 결정할 수 없습니다."
            )

        if (!selectedPluginFile.isFile) {
            return DownloadCommandBuildResult(
                command = null,
                errorMessage = when (selectedService) {
                    ServiceType.ZAN ->
                        "ZAN 플러그인 파일이 없습니다: ${selectedPluginFile.absolutePath}\n먼저 Streamlink 설치/업데이트를 실행하세요."

                    ServiceType.SPWN ->
                        "SPWN 플러그인 파일이 없습니다: ${selectedPluginFile.absolutePath}\n먼저 Streamlink 설치/업데이트를 실행하세요."
                }
            )
        }

        val outputDir = platformResolver.resolveOutputDir(state.outputDir)

        if (!platformResolver.ensureDirectoryExists(outputDir)) {
            return DownloadCommandBuildResult(
                command = null,
                resolvedOutputDir = outputDir,
                errorMessage = "저장 폴더를 생성할 수 없습니다: $outputDir"
            )
        }

        val outputTemplate = when (selectedService) {
            ServiceType.ZAN -> File(outputDir, "{title}.mp4").path
            ServiceType.SPWN -> File(outputDir, "{time:%Y-%m-%d} {title}.mp4").path
        }

        val command = mutableListOf<String>()
        command += streamlinkExecutable
        command += listOf("--plugin-dir", pluginDir)

        when (selectedService) {
            ServiceType.ZAN -> {
                command += listOf("--zan-email", state.email)
                command += listOf("--zan-password", state.password)
            }

            ServiceType.SPWN -> {
                command += listOf("--spwn-email", state.email)
                command += listOf("--spwn-password", state.password)
            }
        }

        command += "--progress=force"
        command += "--skip"
        command += normalizedUrl
        command += state.quality
        command += listOf("-o", outputTemplate)

        return DownloadCommandBuildResult(
            command = command,
            resolvedOutputDir = outputDir
        )
    }
}