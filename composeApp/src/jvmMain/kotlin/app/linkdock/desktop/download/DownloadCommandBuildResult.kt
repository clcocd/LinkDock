package app.linkdock.desktop.download

data class DownloadCommandBuildResult(
    val command: List<String>?,
    val resolvedOutputDir: String? = null,
    val errorMessage: String? = null
)