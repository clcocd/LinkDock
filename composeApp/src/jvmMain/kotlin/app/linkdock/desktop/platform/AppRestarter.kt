package app.linkdock.desktop.platform

import java.io.File

object AppRestarter {

    fun restartCurrentApp(): Boolean {
        val relaunchCommand = resolveRelaunchCommand() ?: return false

        return runCatching {
            val processBuilder = ProcessBuilder(relaunchCommand)

            resolveWorkingDirectory(relaunchCommand.first())?.let(processBuilder::directory)

            processBuilder.start()
            true
        }.getOrDefault(false)
    }

    private fun resolveRelaunchCommand(): List<String>? {
        val processInfo = ProcessHandle.current().info()
        val currentCommand = processInfo.command().orElse(null)
        val currentArgs = processInfo.arguments().orElse(null)?.toList().orEmpty()

        if (!currentCommand.isNullOrBlank()) {
            val commandFile = File(currentCommand)
            val commandName = commandFile.name.lowercase()
            val isJavaLauncher = commandName == "java" ||
                    commandName == "java.exe" ||
                    commandName == "javaw.exe"

            if (commandFile.exists() && commandFile.canExecute() && !isJavaLauncher) {
                return listOf(currentCommand) + currentArgs
            }

            if (isJavaLauncher) {
                buildJavaFallbackCommand(currentCommand)?.let { return it }
            }
        }

        return buildJavaFallbackCommand()
    }

    private fun buildJavaFallbackCommand(currentJavaCommand: String? = null): List<String>? {
        val classPath = System.getProperty("java.class.path").orEmpty()
        val sunJavaCommand = System.getProperty("sun.java.command").orEmpty()

        if (classPath.isBlank() || sunJavaCommand.isBlank()) {
            return null
        }

        val javaCommand = currentJavaCommand ?: resolveJavaExecutable() ?: return null
        val parsedCommand = tokenizeCommandLine(sunJavaCommand)

        if (parsedCommand.isEmpty()) {
            return null
        }

        return listOf(javaCommand, "-cp", classPath) + parsedCommand
    }

    private fun resolveJavaExecutable(): String? {
        val javaHome = System.getProperty("java.home").orEmpty()
        if (javaHome.isBlank()) {
            return null
        }

        val candidates = listOf(
            File(javaHome, "bin/javaw.exe"),
            File(javaHome, "bin/java.exe"),
            File(javaHome, "bin/java")
        )

        return candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

    private fun resolveWorkingDirectory(executablePath: String): File? {
        val executable = File(executablePath).absoluteFile
        return executable.parentFile?.takeIf { it.exists() }
    }

    private fun tokenizeCommandLine(commandLine: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        commandLine.forEach { char ->
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                }

                char.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.setLength(0)
                    }
                }

                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens += current.toString()
        }

        return tokens
    }
}
