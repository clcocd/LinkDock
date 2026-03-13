package app.linkdock.desktop.download

import java.util.Locale

data class SpwnProbeResult(
    val isMultiPart: Boolean,
    val options: List<SpwnPartOption>
)

data class SpwnPartOption(
    val displayLabel: String,
    val bestStreamKey: String,
    val partKey: String,
    val rawLabel: String,
    val originalOrder: Int
)

object SpwnProbeParser {

    private val availableStreamsRegex = Regex("""Available streams:\s*(.+)$""")
    private val multiPartEventRegex = Regex("""Multi-part event:\s*(part\d+)\s*\((.+)\)\s*$""")
    private val partStreamRegex = Regex("""^(part\d+)_(.+)$""")
    private val resolutionRegex = Regex("""_(\d+)p$""", RegexOption.IGNORE_CASE)
    private val vodSuffixRegex = Regex("""\s*\[VOD]""", RegexOption.IGNORE_CASE)
    private val versionSuffixRegex = Regex("""_v\d+$""", RegexOption.IGNORE_CASE)
    private val trailingNumberRegex = Regex("""^(.+?)\s+(\d+)$""")

    fun parse(lines: List<String>): SpwnProbeResult {
        val streamKeys = parseAvailableStreamKeys(lines)
        val grouped = groupByPart(streamKeys)

        if (grouped.isEmpty()) {
            return SpwnProbeResult(
                isMultiPart = false,
                options = emptyList()
            )
        }

        val rawLabelMap = parseRawLabelMap(lines)

        val options = grouped.entries.mapIndexed { index, entry ->
            val partKey = entry.key
            val keys = entry.value
            val rawLabel = rawLabelMap[partKey].orEmpty()

            SpwnPartOption(
                displayLabel = buildDisplayLabel(rawLabel, partKey),
                bestStreamKey = selectBestStreamKey(keys),
                partKey = partKey,
                rawLabel = rawLabel,
                originalOrder = index
            )
        }

        return SpwnProbeResult(
            isMultiPart = grouped.keys.size >= 2,
            options = sortOptions(options)
        )
    }

    private fun parseAvailableStreamKeys(lines: List<String>): List<String> {
        val availableLine = lines.lastOrNull { it.contains("Available streams:") }
            ?: return emptyList()

        val match = availableStreamsRegex.find(availableLine) ?: return emptyList()

        return match.groupValues[1]
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { partStreamRegex.matches(it) }
    }

    private fun groupByPart(streamKeys: List<String>): LinkedHashMap<String, List<String>> {
        val grouped = LinkedHashMap<String, MutableList<String>>()

        streamKeys.forEach { streamKey ->
            val match = partStreamRegex.find(streamKey) ?: return@forEach
            val partKey = match.groupValues[1]
            grouped.getOrPut(partKey) { mutableListOf() }.add(streamKey)
        }

        return LinkedHashMap(grouped.mapValues { it.value.toList() })
    }

    private fun parseRawLabelMap(lines: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()

        lines.forEach { line ->
            val match = multiPartEventRegex.find(line) ?: return@forEach

            val partKey = match.groupValues[1]
            val inner = match.groupValues[2].trim()

            val rawLabel = inner.substringAfter(": ", inner).trim()
            result[partKey] = rawLabel
        }

        return result
    }

    private fun buildDisplayLabel(rawLabel: String, partKey: String): String {
        if (rawLabel.isBlank()) {
            return fallbackDisplayLabel(partKey)
        }

        val baseToken = rawLabel
            .substringAfterLast('/')
            .replace(vodSuffixRegex, "")
            .replace(versionSuffixRegex, "")
            .trim()

        if (baseToken.isBlank()) {
            return fallbackDisplayLabel(partKey)
        }

        val spaced = baseToken
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("""([A-Za-z])(\d)"""), "$1 $2")
            .replace(Regex("""(\d)([A-Za-z])"""), "$1 $2")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (spaced.isBlank()) {
            return fallbackDisplayLabel(partKey)
        }

        return spaced
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { normalizeWord(it) }
    }

    private fun normalizeWord(word: String): String {
        if (word.isBlank()) return word
        if (word.all { it.isDigit() }) return word

        val lower = word.lowercase(Locale.ROOT)
        return lower.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
        }
    }

    private fun fallbackDisplayLabel(partKey: String): String {
        val number = partKey.removePrefix("part").toIntOrNull()
        return if (number != null) "항목 $number" else "항목"
    }

    private fun selectBestStreamKey(streamKeys: List<String>): String {
        return streamKeys.maxByOrNull { extractResolution(it) ?: -1 } ?: streamKeys.first()
    }

    private fun extractResolution(streamKey: String): Int? {
        return resolutionRegex.find(streamKey)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun sortOptions(options: List<SpwnPartOption>): List<SpwnPartOption> {
        if (options.size <= 1) return options

        val parsed = options.map { option ->
            option to parseTrailingNumber(option.displayLabel)
        }

        val allNumbered = parsed.all { it.second != null }
        if (!allNumbered) {
            return options.sortedBy { it.originalOrder }
        }

        val prefixes = parsed
            .mapNotNull { it.second?.first?.lowercase(Locale.ROOT) }
            .distinct()

        return if (prefixes.size == 1) {
            parsed
                .sortedBy { it.second?.second }
                .map { it.first }
        } else {
            options.sortedBy { it.originalOrder }
        }
    }

    private fun parseTrailingNumber(label: String): Pair<String, Int>? {
        val match = trailingNumberRegex.find(label.trim()) ?: return null
        val prefix = match.groupValues[1].trim()
        val number = match.groupValues[2].toIntOrNull() ?: return null
        return prefix to number
    }
}