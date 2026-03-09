package app.linkdock.desktop.install

enum class InstallationOutcome {
    INSTALLED,
    UPDATED,
    ALREADY_LATEST,
    PREREQUISITE_MISSING,
    UNSUPPORTED_OS,
    FAILED
}

data class InstallationResult(
    val success: Boolean,
    val outcome: InstallationOutcome,
    val completionMessage: String,
    val didChangeStreamlink: Boolean = false
)