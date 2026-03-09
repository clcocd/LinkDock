package app.linkdock.desktop.storage

import app.linkdock.desktop.domain.OsType

data class EnvCheckCache(
    val checkedAtEpochMillis: Long,
    val osType: OsType,
    val hasStreamlink: Boolean,
    val hasBrew: Boolean,
    val hasWinget: Boolean,
    val hasChoco: Boolean
)