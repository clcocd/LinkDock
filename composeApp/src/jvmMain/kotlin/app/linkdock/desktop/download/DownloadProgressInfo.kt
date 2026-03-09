package app.linkdock.desktop.download

data class DownloadProgressInfo(
    val written: String,
    val elapsed: String,
    val speed: String
) {
    fun toDisplayText(): String {
        return "$written · 경과 $elapsed · 속도 $speed"
    }
}