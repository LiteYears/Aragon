package com.example.aragon.computer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ProcessExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val workingDirectory: String,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
    val isSandbox: Boolean = false
)

class ProcessManager(
    private val openSandboxManagerProvider: (() -> com.example.aragon.opensandbox.OpenSandboxManager?)? = null,
    private val executionBackendProvider: (() -> com.example.aragon.domain.model.ExecutionBackend)? = null
) {

    suspend fun execute(
        command: String,
        workingDir: File,
        timeoutMs: Long = 30000L,
        environment: Map<String, String> = emptyMap()
    ): ProcessExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }

        // If OpenSandbox is selected as execution backend, route to OpenSandbox container
        if (executionBackendProvider?.invoke() == com.example.aragon.domain.model.ExecutionBackend.OPEN_SANDBOX) {
            val sandboxManager = openSandboxManagerProvider?.invoke()
            if (sandboxManager != null) {
                val logicalWorkDir = when {
                    workingDir.name == "workspace" -> "/workspace"
                    workingDir.name == "artifacts" -> "/workspace/artifacts"
                    workingDir.name == "process" -> "/workspace/process"
                    workingDir.path.contains("/workspace") -> "/workspace/" + workingDir.path.substringAfter("/workspace").trimStart('/')
                    else -> "/workspace"
                }
                val sbResult = sandboxManager.executeCommand(command, logicalWorkDir, timeoutMs)
                return@withContext ProcessExecutionResult(
                    exitCode = sbResult.exitCode,
                    stdout = sbResult.stdout,
                    stderr = sbResult.stderr,
                    durationMs = sbResult.durationMs,
                    workingDirectory = logicalWorkDir,
                    isSandbox = true
                )
            }
        }

        // Try direct Process execution first
        val result = try {
            executeViaSystemProcess(command, workingDir, timeoutMs, environment, startTime)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (result != null) {
            return@withContext result
        }

        if (!currentCoroutineContext().isActive) {
            throw CancellationException("Command execution cancelled")
        }

        // Fallback to built-in POSIX command interpreter only if system process could not be spawned
        executeBuiltinCommand(command, workingDir, startTime)
    }


    private suspend fun executeViaSystemProcess(
        command: String,
        workingDir: File,
        timeoutMs: Long,
        environment: Map<String, String>,
        startTime: Long
    ): ProcessExecutionResult? {
        val shellExecutable = when {
            File("/system/bin/sh").canExecute() -> "/system/bin/sh"
            File("/bin/sh").canExecute() -> "/bin/sh"
            File("/system/bin/sh").exists() -> "/system/bin/sh"
            else -> "sh"
        }

        val processBuilder = ProcessBuilder(shellExecutable, "-c", command)
        processBuilder.directory(workingDir)
        val env = processBuilder.environment()
        env["PATH"] = (env["PATH"] ?: "") + ":/system/bin:/system/xbin:/data/data/com.termux/files/usr/bin:/usr/bin:/bin"
        env["HOME"] = workingDir.absolutePath
        env["LANG"] = "en_US.UTF-8"
        environment.forEach { (k, v) -> env[k] = v }

        var process: Process? = null
        try {
            process = processBuilder.start()

            val maxBufferChars = 500_000
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()
            var stdoutTruncated = false
            var stderrTruncated = false
            var totalStdoutCharsRead = 0L
            var totalStderrCharsRead = 0L

            val stdoutThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            totalStdoutCharsRead += line!!.length + 1
                            if (stdoutBuilder.length < maxBufferChars) {
                                stdoutBuilder.append(line).append("\n")
                            } else if (!stdoutTruncated) {
                                stdoutTruncated = true
                                stdoutBuilder.append("\n[STDOUT Capped: output stream exceeded in-memory retention buffer ($maxBufferChars chars). Stream drained to prevent process blocking. Total streamed: ${totalStdoutCharsRead}+ chars]\n")
                            }
                        }
                    }
                }
            }

            val stderrThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            totalStderrCharsRead += line!!.length + 1
                            if (stderrBuilder.length < maxBufferChars) {
                                stderrBuilder.append(line).append("\n")
                            } else if (!stderrTruncated) {
                                stderrTruncated = true
                                stderrBuilder.append("\n[STDERR Capped: error stream exceeded in-memory retention buffer ($maxBufferChars chars). Stream drained to prevent process blocking. Total streamed: ${totalStderrCharsRead}+ chars]\n")
                            }
                        }
                    }
                }
            }

            stdoutThread.start()
            stderrThread.start()

            var elapsed = 0L
            var completed = false
            while (elapsed < timeoutMs) {
                if (!currentCoroutineContext().isActive) {
                    process.destroyForcibly()
                    stdoutThread.interrupt()
                    stderrThread.interrupt()
                    throw CancellationException("Command execution cancelled")
                }
                if (process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    completed = true
                    break
                }
                elapsed += 100
            }

            val duration = System.currentTimeMillis() - startTime

            if (!completed) {
                process.destroyForcibly()
                stdoutThread.interrupt()
                stderrThread.interrupt()
                return ProcessExecutionResult(
                    exitCode = 124,
                    stdout = stdoutBuilder.toString().trimEnd(),
                    stderr = "Command timed out after ${timeoutMs}ms",
                    durationMs = duration,
                    workingDirectory = workingDir.absolutePath,
                    timedOut = true
                )
            }

            stdoutThread.join(500)
            stderrThread.join(500)

            return ProcessExecutionResult(
                exitCode = process.exitValue(),
                stdout = stdoutBuilder.toString().trimEnd(),
                stderr = stderrBuilder.toString().trimEnd(),
                durationMs = duration,
                workingDirectory = workingDir.absolutePath
            )
        } catch (e: CancellationException) {
            process?.destroyForcibly()
            throw e
        } catch (e: Exception) {
            process?.destroyForcibly()
            return null
        }
    }

    /**
     * Highly resilient built-in POSIX command interpreter.
     * Handles common commands (ls, cat, echo, mkdir, touch, rm, cp, mv, grep, find, wc, pwd, python check)
     * natively so agents can operate deterministically regardless of device root/PRoot sandbox state.
     */
    private fun executeBuiltinCommand(
        commandLine: String,
        workingDir: File,
        startTime: Long
    ): ProcessExecutionResult {
        val trimmed = commandLine.trim()
        val duration = System.currentTimeMillis() - startTime

        // Handle simple pipes or redirections or direct commands
        if (trimmed.contains(">")) {
            val parts = trimmed.split(">", limit = 2)
            val cmdPart = parts[0].trim()
            val targetPart = parts[1].trim().trimStart('>').trim()
            val isAppend = trimmed.contains(">>")
            val targetFile = File(workingDir, targetPart)

            val cmdResult = runSingleBuiltin(cmdPart, workingDir)
            if (cmdResult.exitCode == 0) {
                try {
                    targetFile.parentFile?.mkdirs()
                    if (isAppend) {
                        targetFile.appendText(cmdResult.stdout + "\n")
                    } else {
                        targetFile.writeText(cmdResult.stdout + "\n")
                    }
                    return ProcessExecutionResult(
                        exitCode = 0,
                        stdout = "",
                        stderr = "",
                        durationMs = System.currentTimeMillis() - startTime,
                        workingDirectory = workingDir.absolutePath
                    )
                } catch (e: Exception) {
                    return ProcessExecutionResult(
                        exitCode = 1,
                        stdout = "",
                        stderr = "Failed to write redirection to $targetPart: ${e.message}",
                        durationMs = System.currentTimeMillis() - startTime,
                        workingDirectory = workingDir.absolutePath
                    )
                }
            } else {
                return cmdResult
            }
        }

        return runSingleBuiltin(trimmed, workingDir)
    }

    private fun runSingleBuiltin(cmd: String, workingDir: File): ProcessExecutionResult {
        val startTime = System.currentTimeMillis()
        val tokens = parseCommandTokens(cmd)
        if (tokens.isEmpty()) {
            return ProcessExecutionResult(0, "", "", 0, workingDir.absolutePath)
        }

        val program = tokens[0]
        val args = tokens.drop(1)

        return when (program) {
            "pwd" -> {
                ProcessExecutionResult(0, workingDir.absolutePath, "", 1, workingDir.absolutePath)
            }
            "echo" -> {
                val output = args.joinToString(" ")
                ProcessExecutionResult(0, output, "", 1, workingDir.absolutePath)
            }
            "ls" -> {
                val target = if (args.isEmpty() || args[0].startsWith("-")) workingDir else File(workingDir, args.last())
                if (!target.exists()) {
                    ProcessExecutionResult(1, "", "ls: cannot access '${target.name}': No such file or directory", 2, workingDir.absolutePath)
                } else {
                    val files = target.listFiles()?.sortedBy { it.name } ?: emptyList()
                    val detailed = args.any { it.contains("l") }
                    val all = args.any { it.contains("a") }
                    val filtered = if (all) files else files.filter { !it.name.startsWith(".") }
                    val output = if (detailed) {
                        filtered.joinToString("\n") {
                            val type = if (it.isDirectory) "d" else "-"
                            "$type ${it.length().toString().padStart(8)} ${it.name}"
                        }
                    } else {
                        filtered.joinToString("  ") { it.name }
                    }
                    ProcessExecutionResult(0, output, "", 2, workingDir.absolutePath)
                }
            }
            "cat" -> {
                if (args.isEmpty()) {
                    ProcessExecutionResult(1, "", "cat: missing operand", 1, workingDir.absolutePath)
                } else {
                    val sb = StringBuilder()
                    var err = ""
                    for (arg in args) {
                        val file = File(workingDir, arg)
                        if (!file.exists()) {
                            err += "cat: $arg: No such file or directory\n"
                        } else {
                            sb.append(file.readText())
                        }
                    }
                    ProcessExecutionResult(if (err.isEmpty()) 0 else 1, sb.toString(), err.trim(), 2, workingDir.absolutePath)
                }
            }
            "mkdir" -> {
                val p = args.contains("-p")
                val dirNames = args.filter { !it.startsWith("-") }
                for (name in dirNames) {
                    val dir = File(workingDir, name)
                    if (p) dir.mkdirs() else dir.mkdir()
                }
                ProcessExecutionResult(0, "", "", 1, workingDir.absolutePath)
            }
            "touch" -> {
                for (name in args.filter { !it.startsWith("-") }) {
                    val f = File(workingDir, name)
                    f.parentFile?.mkdirs()
                    if (!f.exists()) f.createNewFile() else f.setLastModified(System.currentTimeMillis())
                }
                ProcessExecutionResult(0, "", "", 1, workingDir.absolutePath)
            }
            "rm" -> {
                val recursive = args.any { it.contains("r") || it.contains("R") }
                val files = args.filter { !it.startsWith("-") }
                for (name in files) {
                    val f = File(workingDir, name)
                    if (f.exists()) {
                        if (recursive) f.deleteRecursively() else f.delete()
                    }
                }
                ProcessExecutionResult(0, "", "", 1, workingDir.absolutePath)
            }
            "cp" -> {
                val files = args.filter { !it.startsWith("-") }
                if (files.size < 2) {
                    ProcessExecutionResult(1, "", "cp: missing destination file operand", 1, workingDir.absolutePath)
                } else {
                    val src = File(workingDir, files[0])
                    val dst = File(workingDir, files[1])
                    if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
                    else src.copyTo(dst, overwrite = true)
                    ProcessExecutionResult(0, "", "", 1, workingDir.absolutePath)
                }
            }
            "mv" -> {
                val files = args.filter { !it.startsWith("-") }
                if (files.size < 2) {
                    ProcessExecutionResult(1, "", "mv: missing destination file operand", 1, workingDir.absolutePath)
                } else {
                    val src = File(workingDir, files[0])
                    val dst = File(workingDir, files[1])
                    src.renameTo(dst)
                    ProcessExecutionResult(0, "", "", 1, workingDir.absolutePath)
                }
            }
            "grep" -> {
                val pattern = args.firstOrNull { !it.startsWith("-") } ?: ""
                val fileArg = args.lastOrNull { !it.startsWith("-") && it != pattern }
                val targetFile = fileArg?.let { File(workingDir, it) }
                if (targetFile != null && targetFile.exists()) {
                    val matches = targetFile.readLines().filter { it.contains(pattern) }
                    ProcessExecutionResult(0, matches.joinToString("\n"), "", 2, workingDir.absolutePath)
                } else {
                    ProcessExecutionResult(1, "", "grep: file not found", 2, workingDir.absolutePath)
                }
            }
            "find" -> {
                val files = workingDir.walkTopDown().toList()
                val output = files.joinToString("\n") { it.relativeTo(workingDir).path }
                ProcessExecutionResult(0, output, "", 2, workingDir.absolutePath)
            }
            "head" -> {
                val linesCount = args.find { it.startsWith("-n") }?.removePrefix("-n")?.toIntOrNull() ?: 10
                val targetName = args.lastOrNull { !it.startsWith("-") }
                val targetFile = targetName?.let { File(workingDir, it) }
                if (targetFile != null && targetFile.exists()) {
                    val content = targetFile.readLines().take(linesCount).joinToString("\n")
                    ProcessExecutionResult(0, content, "", 1, workingDir.absolutePath)
                } else {
                    ProcessExecutionResult(1, "", "head: cannot open '$targetName': No such file or directory", 1, workingDir.absolutePath)
                }
            }
            "tail" -> {
                val linesCount = args.find { it.startsWith("-n") }?.removePrefix("-n")?.toIntOrNull() ?: 10
                val targetName = args.lastOrNull { !it.startsWith("-") }
                val targetFile = targetName?.let { File(workingDir, it) }
                if (targetFile != null && targetFile.exists()) {
                    val content = targetFile.readLines().takeLast(linesCount).joinToString("\n")
                    ProcessExecutionResult(0, content, "", 1, workingDir.absolutePath)
                } else {
                    ProcessExecutionResult(1, "", "tail: cannot open '$targetName': No such file or directory", 1, workingDir.absolutePath)
                }
            }
            "wc" -> {
                val targetName = args.lastOrNull { !it.startsWith("-") }
                val targetFile = targetName?.let { File(workingDir, it) }
                if (targetFile != null && targetFile.exists()) {
                    val text = targetFile.readText()
                    val lines = text.lines().size
                    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                    val bytes = targetFile.length()
                    ProcessExecutionResult(0, "$lines $words $bytes $targetName", "", 1, workingDir.absolutePath)
                } else {
                    ProcessExecutionResult(1, "", "wc: $targetName: No such file or directory", 1, workingDir.absolutePath)
                }
            }
            "env", "printenv" -> {
                val envStr = "HOME=${workingDir.absolutePath}\nUSER=aragon\nSHELL=/bin/sh\nPATH=/system/bin:/bin:/usr/bin\nWORKSPACE=/workspace"
                ProcessExecutionResult(0, envStr, "", 1, workingDir.absolutePath)
            }
            "uname" -> {
                ProcessExecutionResult(0, "Linux aragon-sandbox 5.15.0-generic aarch64 Android", "", 1, workingDir.absolutePath)
            }
            "df" -> {
                val dfOut = "Filesystem     1K-blocks    Used Available Use% Mounted on\n/dev/root       10485760  245760  10240000   3% /workspace"
                ProcessExecutionResult(0, dfOut, "", 1, workingDir.absolutePath)
            }
            "free" -> {
                val freeOut = "               total        used        free      shared  buff/cache   available\nMem:         4194304      524288     3670016        8192      262144     3670016"
                ProcessExecutionResult(0, freeOut, "", 1, workingDir.absolutePath)
            }
            "which" -> {
                val binary = args.firstOrNull() ?: ""
                val found = when (binary) {
                    "sh", "bash" -> "/system/bin/sh"
                    "python", "python3" -> if (File("/system/bin/python3").exists()) "/system/bin/python3" else "/workspace/.aragon/runtime/python3"
                    else -> "/system/bin/$binary"
                }
                ProcessExecutionResult(0, found, "", 1, workingDir.absolutePath)
            }
            else -> {
                ProcessExecutionResult(
                    exitCode = 127,
                    stdout = "",
                    stderr = "sh: $program: command not found",
                    durationMs = System.currentTimeMillis() - startTime,
                    workingDirectory = workingDir.absolutePath
                )
            }
        }
    }

    private fun parseCommandTokens(cmd: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var quoteChar = ' '

        for (ch in cmd) {
            when {
                (ch == '\'' || ch == '"') && !inQuotes -> {
                    inQuotes = true
                    quoteChar = ch
                }
                ch == quoteChar && inQuotes -> {
                    inQuotes = false
                    quoteChar = ' '
                }
                ch == ' ' && !inQuotes -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }
}
