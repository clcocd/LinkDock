package app.linkdock.desktop.install

enum class PluginInstallOutcome {
    NO_CHANGES,
    UPDATED
}

data class PluginInstallResult(
    val success: Boolean,
    val completionMessage: String,
    val outcome: PluginInstallOutcome? = null
)