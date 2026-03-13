package app.linkdock.desktop.download

import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.domain.ServiceType
import app.linkdock.desktop.platform.PlatformResolver
import java.io.File

class DownloadCommandFactory(
    private val platformResolver: PlatformResolver
) {

    fun build(
        state: AppUiState,
        streamSelectionOverride: String? = null
    ): DownloadCommandBuildResult {
        val prepared = prepareCommon(state) ?: return DownloadCommandBuildResult(
            command = null,
            errorMessage = "다운로드 명령을 준비하지 못했습니다."
        )

        val outputDir = platformResolver.resolveOutputDir(state.outputDir)

        if (!platformResolver.ensureDirectoryExists(outputDir)) {
            return DownloadCommandBuildResult(
                command = null,
                resolvedOutputDir = outputDir,
                errorMessage = "저장 폴더를 생성할 수 없습니다: $outputDir"
            )
        }

        val outputTemplate = when (prepared.selectedService) {
            ServiceType.ZAN -> File(outputDir, "{title}.mp4").path
            ServiceType.SPWN -> File(outputDir, buildSpwnOutputTemplate(state)).path
        }

        val streamSelection = streamSelectionOverride ?: state.quality
        if (streamSelection.isBlank()) {
            return DownloadCommandBuildResult(
                command = null,
                resolvedOutputDir = outputDir,
                errorMessage = "스트림 선택 값을 결정하지 못했습니다."
            )
        }

        val command = prepared.baseCommand.toMutableList()
        command += "--progress=force"
        command += "--skip"
        command += prepared.normalizedUrl
        command += streamSelection
        command += listOf("-o", outputTemplate)

        return DownloadCommandBuildResult(
            command = command,
            resolvedOutputDir = outputDir
        )
    }

    fun buildProbe(state: AppUiState): DownloadCommandBuildResult {
        val prepared = prepareCommon(state) ?: return DownloadCommandBuildResult(
            command = null,
            errorMessage = "SPWN 확인용 명령을 준비하지 못했습니다."
        )

        val command = prepared.baseCommand.toMutableList()
        command += prepared.normalizedUrl

        return DownloadCommandBuildResult(
            command = command,
            resolvedOutputDir = null
        )
    }

    private fun prepareCommon(state: AppUiState): PreparedDownloadCommand? {
        val osType = state.osType ?: platformResolver.detectOsType()

        val selectedService = state.selectedService ?: return null

        val normalizedUrl = state.url.trim()
        val unsupportedUrlMessage = getUnsupportedServiceUrlMessage(selectedService, normalizedUrl)
        if (unsupportedUrlMessage != null) {
            return null
        }

        val streamlinkExecutable = platformResolver.resolveStreamlinkExecutable(osType) ?: return null
        val ffmpegExecutable = platformResolver.resolveFfmpegExecutable(osType) ?: return null
        val pluginDir = platformResolver.resolveAppPluginDir(osType) ?: return null

        val selectedPluginFile = platformResolver.resolveManagedPluginFile(osType, selectedService)
            ?.let(::File)
            ?: return null

        if (!selectedPluginFile.isFile) {
            return null
        }

        val baseCommand = mutableListOf<String>()
        baseCommand += streamlinkExecutable
        baseCommand += listOf("--plugin-dir", pluginDir)
        baseCommand += listOf("--ffmpeg-ffmpeg", ffmpegExecutable)

        when (selectedService) {
            ServiceType.ZAN -> {
                baseCommand += listOf("--zan-email", state.email)
                baseCommand += listOf("--zan-password", state.password)
            }

            ServiceType.SPWN -> {
                baseCommand += listOf("--spwn-email", state.email)
                baseCommand += listOf("--spwn-password", state.password)
            }
        }

        return PreparedDownloadCommand(
            selectedService = selectedService,
            normalizedUrl = normalizedUrl,
            baseCommand = baseCommand
        )
    }

    private fun buildSpwnOutputTemplate(state: AppUiState): String {
        val selectedPartSuffix = state.selectedSpwnPartLabel
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeFileNameSegment)
            ?.takeIf { it.isNotBlank() }
            ?.let { " - $it" }
            .orEmpty()

        return "{time:%Y-%m-%d} {title}$selectedPartSuffix.mp4"
    }

    private fun sanitizeFileNameSegment(value: String): String {
        return value
            .replace(Regex("""[\\/:*?"<>|]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.')
    }

    private data class PreparedDownloadCommand(
        val selectedService: ServiceType,
        val normalizedUrl: String,
        val baseCommand: List<String>
    )
}