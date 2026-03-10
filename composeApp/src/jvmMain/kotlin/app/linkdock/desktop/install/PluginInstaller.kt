package app.linkdock.desktop.install

import app.linkdock.desktop.app.AppUiState
import app.linkdock.desktop.domain.OsType
import app.linkdock.desktop.platform.PlatformResolver
import java.io.File
import java.net.URI

private data class PluginAsset(
    val fileName: String,
    val downloadUrl: String
)

class PluginInstaller(
    private val platformResolver: PlatformResolver
) {

    private val pluginAssets = listOf(
        PluginAsset(
            fileName = "zan.py",
            downloadUrl = "https://raw.githubusercontent.com/pmrowla/streamlink-plugins/master/zan.py"
        ),
        PluginAsset(
            fileName = "spwn.py",
            downloadUrl = "https://raw.githubusercontent.com/pmrowla/streamlink-plugins/master/spwn.py"
        )
    )

    fun installOrUpdate(
        state: AppUiState,
        onLine: (String) -> Unit
    ): PluginInstallResult {
        val osType = state.osType ?: platformResolver.detectOsType()

        if (osType == OsType.UNSUPPORTED) {
            return PluginInstallResult(
                success = false,
                completionMessage = "현재 운영체제에서는 플러그인 설치를 지원하지 않습니다."
            )
        }

        val pluginDir = platformResolver.resolveAppPluginDir(osType)
            ?: return PluginInstallResult(
                success = false,
                completionMessage = "플러그인 폴더 경로를 결정할 수 없습니다."
            )

        if (!platformResolver.ensureDirectoryExists(pluginDir)) {
            return PluginInstallResult(
                success = false,
                completionMessage = "플러그인 폴더를 생성할 수 없습니다: $pluginDir" +
                        "\n해당 경로를 수동으로 생성한 뒤 다시 설치/업데이트를 실행해 주세요."
            )
        }
        onLine("플러그인 폴더 확인: $pluginDir")

        for (asset in pluginAssets) {
            val targetFile = File(pluginDir, asset.fileName)
            val tempFile = File(pluginDir, "${asset.fileName}.tmp")
            val backupFile = File(pluginDir, "${asset.fileName}.bak")
            var backupCreated = false

            try {
                tempFile.delete()

                downloadToFile(asset.downloadUrl, tempFile)

                if (!tempFile.isFile || tempFile.length() == 0L) {
                    return PluginInstallResult(
                        success = false,
                        completionMessage = "${asset.fileName} 다운로드 결과가 비어 있습니다."
                    )
                }

                if (targetFile.isFile && filesHaveSameContent(targetFile, tempFile)) {
                    onLine("${asset.fileName} 변경 없음, 유지")
                    continue
                }

                if (targetFile.isFile) {
                    onLine("${asset.fileName} 변경 감지")
                    targetFile.copyTo(backupFile, overwrite = true)
                    backupCreated = true
                }

                tempFile.copyTo(targetFile, overwrite = true)

                if (backupFile.isFile) {
                    backupFile.delete()
                }

                onLine("${asset.fileName} 업데이트 적용 완료")
            } catch (e: Exception) {
                val restoreMessage = if (backupCreated && backupFile.isFile) {
                    try {
                        backupFile.copyTo(targetFile, overwrite = true)
                        onLine("${asset.fileName} 복구 완료")
                        "\n백업에서 기존 파일을 복구했습니다: ${targetFile.absolutePath}"
                    } catch (restoreError: Exception) {
                        "\n백업 복구에도 실패했습니다: ${restoreError.message ?: "원인 불명"}" +
                                "\n백업 파일 위치: ${backupFile.absolutePath}"
                    }
                } else {
                    ""
                }

                return PluginInstallResult(
                    success = false,
                    completionMessage = "${asset.fileName} 설치 실패: ${e.message ?: "원인 불명"}$restoreMessage"
                )
            } finally {
                tempFile.delete()
            }
        }

        return PluginInstallResult(
            success = true,
            completionMessage = "플러그인 설치/업데이트 완료"
        )
    }

    private fun downloadToFile(url: String, targetFile: File) {
        val connection = URI(url).toURL().openConnection().apply {
            connectTimeout = 10_000
            readTimeout = 30_000
        }

        connection.getInputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun filesHaveSameContent(first: File, second: File): Boolean {
        if (!first.isFile || !second.isFile) return false
        if (first.length() != second.length()) return false

        first.inputStream().buffered().use { firstInput ->
            second.inputStream().buffered().use { secondInput ->
                while (true) {
                    val firstByte = firstInput.read()
                    val secondByte = secondInput.read()

                    if (firstByte != secondByte) {
                        return false
                    }

                    if (firstByte == -1) {
                        return true
                    }
                }
            }
        }
    }
}