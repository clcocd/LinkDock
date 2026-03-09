package app.linkdock.desktop.command

data class CommandResult(
    val success: Boolean,
    val exitCode: Int,
    val firstLine: String?,
    val fullOutput: String
)