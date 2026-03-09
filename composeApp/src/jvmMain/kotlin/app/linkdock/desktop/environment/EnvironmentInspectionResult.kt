package app.linkdock.desktop.environment

import app.linkdock.desktop.domain.OsType

data class EnvironmentInspectionResult(
    val osType: OsType,
    val hasStreamlink: Boolean,
    val hasBrew: Boolean = false,
    val hasWinget: Boolean = false,
    val hasChoco: Boolean = false,
    val logs: List<String>
)