package app.linkdock.desktop.download

object StreamlinkProgressParser {

    private val progressRegex = Regex(
        """Written\s+([0-9.]+\s*[KMGTP]?i?B)\s+to\s+.+?\s+\(([^@]+?)\s+@\s+([0-9.]+\s*[KMGTP]?i?B/s)\)""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun parse(line: String): DownloadProgressInfo? {
        val match = progressRegex.find(line) ?: return null

        val (written, elapsed, speed) = match.destructured

        return DownloadProgressInfo(
            written = written.trim(),
            elapsed = elapsed.trim(),
            speed = speed.trim()
        )
    }
}