package app.linkdock.desktop.command

import java.io.BufferedReader
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class CommandRunner {

    fun runCommand(vararg command: String): CommandResult {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()

            val firstLine = output
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            CommandResult(
                success = exitCode == 0,
                exitCode = exitCode,
                firstLine = firstLine,
                fullOutput = output
            )
        } catch (e: Exception) {
            CommandResult(
                success = false,
                exitCode = -1,
                firstLine = e.message,
                fullOutput = e.stackTraceToString()
            )
        }
    }

    fun runCommandWithFallback(
        commandName: String,
        fallbackPath: String?,
        vararg args: String
    ): CommandResult {
        val directResult = runCommand(commandName, *args)
        if (directResult.success) return directResult

        if (fallbackPath != null) {
            val fallbackResult = runCommand(fallbackPath, *args)
            if (fallbackResult.success) return fallbackResult
        }

        return directResult
    }

    fun runStreamingCommand(
        command: List<String>,
        onLine: (String) -> Unit,
        onProgressLine: ((String) -> Unit)? = null,
        onProcessStarted: ((Process) -> Unit)? = null
    ): CommandResult {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            onProcessStarted?.invoke(process)

            var firstLine: String? = null

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine
                        .replace("\r", "")
                        .trimEnd()

                    if (line.isBlank()) return@forEach

                    val progressText = extractProgressDisplayText(line)
                    if (progressText != null) {
                        onProgressLine?.invoke(progressText)
                        return@forEach
                    }

                    if (firstLine == null) {
                        firstLine = line
                    }

                    onLine(line)
                }
            }

            val exitCode = process.waitFor()

            CommandResult(
                success = exitCode == 0,
                exitCode = exitCode,
                firstLine = firstLine,
                fullOutput = ""
            )
        } catch (e: Exception) {
            onLine("명령 실행 실패: ${e.message ?: "원인 불명"}")

            CommandResult(
                success = false,
                exitCode = -1,
                firstLine = e.message,
                fullOutput = e.stackTraceToString()
            )
        }
    }

    fun runStreamingDownloadCommand(
        command: List<String>,
        onStdoutLine: (String) -> Unit,
        onStderrLine: (String) -> Unit,
        onProcessStarted: ((Process) -> Unit)? = null
    ): CommandResult {
        return try {
            val process = ProcessBuilder(command).start()

            onProcessStarted?.invoke(process)

            val firstLineRef = AtomicReference<String?>(null)

            fun consumeStream(
                stream: InputStream,
                onLine: (String) -> Unit
            ) = thread(start = true) {
                val reader = stream.bufferedReader()
                val buffer = StringBuilder()

                fun flushBuffer() {
                    if (buffer.isEmpty()) return

                    val line = buffer.toString().trim()
                    buffer.clear()

                    if (line.isBlank()) return

                    firstLineRef.compareAndSet(null, line)
                    onLine(line)
                }

                while (true) {
                    val ch = reader.read()
                    if (ch == -1) {
                        flushBuffer()
                        break
                    }

                    when (ch.toChar()) {
                        '\r', '\n' -> flushBuffer()
                        else -> buffer.append(ch.toChar())
                    }
                }
            }

            val stdoutThread = consumeStream(
                stream = process.inputStream,
                onLine = onStdoutLine
            )

            val stderrThread = consumeStream(
                stream = process.errorStream,
                onLine = onStderrLine
            )

            val exitCode = process.waitFor()

            stdoutThread.join()
            stderrThread.join()

            CommandResult(
                success = exitCode == 0,
                exitCode = exitCode,
                firstLine = firstLineRef.get(),
                fullOutput = ""
            )
        } catch (e: Exception) {
            onStderrLine("명령 실행 실패: ${e.message ?: "원인 불명"}")

            CommandResult(
                success = false,
                exitCode = -1,
                firstLine = e.message,
                fullOutput = e.stackTraceToString()
            )
        }
    }

    private fun isSpinnerLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "-" || trimmed == "\\" || trimmed == "|" || trimmed == "/"
    }

    private fun extractProgressDisplayText(line: String): String? {
        val trimmed = line.trim()

        if (trimmed.isBlank()) return null

        if (isSpinnerLine(trimmed)) {
            return "설치 진행 중..."
        }

        val percentMatch = Regex("""(\d+%)""").find(trimmed)
        if (percentMatch != null) {
            return "설치 진행 중... ${percentMatch.value}"
        }

        val sizeMatch = Regex(
            """(\d+(?:\.\d+)?\s*(?:B|KB|MB|GB)\s*/\s*\d+(?:\.\d+)?\s*(?:B|KB|MB|GB))""",
            RegexOption.IGNORE_CASE
        ).find(trimmed)
        if (sizeMatch != null) {
            return "설치 진행 중... ${sizeMatch.value}"
        }

        val barCharCount = trimmed.count {
            it == '█' || it == '▓' || it == '▒' || it == '░' ||
                    it == '■' || it == '□' || it == '─' || it == '━' || it == '-'
        }

        if (barCharCount >= 5) {
            return "설치 진행 중..."
        }

        return null
    }
}