package app.linkdock.desktop.install

data class InstallationResult(
    val success: Boolean,
    val completionMessage: String,
    val restartRecommendationMessage: String? = null
)