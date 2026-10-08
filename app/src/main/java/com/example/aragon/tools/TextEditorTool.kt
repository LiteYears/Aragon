package com.example.aragon.tools

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.ToolResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced text editor and iterative file builder tool.
 * Specifically engineered to enable agents to safely build upon, edit, append to,
 * and incrementally patch files across multiple turns without clobbering or corrupting contents.
 */
class TextEditorTool {

    // In-memory backup snapshots for undo/rollback protection (thread-safe)
    private val fileBackups = ConcurrentHashMap<String, List<String>>()

    fun execute(
        callId: String,
        taskId: String,
        operation: String,
        path: String,
        content: String?,
        targetContent: String?,
        replacementContent: String?,
        startLine: Int?,
        lineCount: Int?,
        resolver: WorkspacePathResolver
    ): ToolResult {
        val startTime = System.currentTimeMillis()
        val file = resolver.resolve(path)

        try {
            when (operation.lowercase()) {
                "view", "read" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val lines = file.readLines()
                    val from = ((startLine ?: 1) - 1).coerceAtLeast(0)
                    val count = (lineCount ?: lines.size).coerceAtLeast(0)
                    val selected = lines.drop(from).take(count)

                    val output = buildString {
                        appendLine("File: ${resolver.toLogicalPath(file)} (${lines.size} total lines, ${file.length()} bytes)")
                        appendLine("Showing lines ${from + 1} to ${from + selected.size}:")
                        appendLine("--------------------------------------------------")
                        selected.forEachIndexed { idx, line ->
                            appendLine("${(from + idx + 1).toString().padStart(4, ' ')} | $line")
                        }
                    }

                    return successResult(callId, taskId, output, startTime, resolver.toLogicalPath(file))
                }

                "create", "write" -> {
                    saveBackup(file)
                    file.parentFile?.mkdirs()
                    file.writeText(content ?: "")
                    val lineCountResult = file.readLines().size
                    val output = "Successfully created file at ${resolver.toLogicalPath(file)} ($lineCountResult lines, ${file.length()} bytes)."
                    return successResult(
                        callId,
                        taskId,
                        output,
                        startTime,
                        resolver.toLogicalPath(file),
                        listOf(resolver.toLogicalPath(file))
                    )
                }

                "replace" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val oldText = file.readText()
                    val target = targetContent ?: return errorResult(callId, taskId, "targetContent required for replace", startTime)
                    val replacement = replacementContent ?: ""

                    // 1. Try exact replacement
                    if (oldText.contains(target)) {
                        saveBackup(file)
                        val newText = oldText.replace(target, replacement)
                        file.writeText(newText)
                        val diffSummary = "Replaced exact target substring in ${resolver.toLogicalPath(file)} (New size: ${file.length()} bytes, ${file.readLines().size} lines)."
                        return successResult(callId, taskId, diffSummary, startTime, resolver.toLogicalPath(file))
                    }

                    // 2. Fallback to whitespace/newline normalized matching
                    val normalizedResult = replaceNormalized(oldText, target, replacement)
                    if (normalizedResult != null) {
                        saveBackup(file)
                        file.writeText(normalizedResult)
                        val diffSummary = "Applied replacement with whitespace normalization in ${resolver.toLogicalPath(file)} (New size: ${file.length()} bytes)."
                        return successResult(callId, taskId, diffSummary, startTime, resolver.toLogicalPath(file))
                    }

                    return errorResult(
                        callId,
                        taskId,
                        "targetContent not found in $path. Check exact whitespace, or use 'view' operation to inspect lines.",
                        startTime
                    )
                }

                "patch", "fuzzy_replace" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val currentText = file.readText()
                    val target = targetContent ?: return errorResult(callId, taskId, "targetContent required for patch", startTime)
                    val replacement = replacementContent ?: ""

                    if (currentText.contains(target)) {
                        saveBackup(file)
                        val updated = currentText.replaceFirst(target, replacement)
                        file.writeText(updated)
                        return successResult(callId, taskId, "Patch applied successfully to ${resolver.toLogicalPath(file)}", startTime, resolver.toLogicalPath(file))
                    }

                    val fuzzyUpdated = replaceNormalized(currentText, target, replacement)
                    if (fuzzyUpdated != null) {
                        saveBackup(file)
                        file.writeText(fuzzyUpdated)
                        return successResult(callId, taskId, "Patch applied with flexible whitespace matching in ${resolver.toLogicalPath(file)}", startTime, resolver.toLogicalPath(file))
                    }

                    return errorResult(callId, taskId, "Patch target block could not be located in $path.", startTime)
                }

                "replace_lines" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val lines = file.readLines().toMutableList()
                    val fromLine = startLine ?: return errorResult(callId, taskId, "startLine required for replace_lines", startTime)
                    val count = lineCount ?: 1
                    val newLines = (replacementContent ?: content ?: "").lines()

                    val startIndex = (fromLine - 1).coerceIn(0, lines.size)
                    val endIndex = (startIndex + count).coerceIn(startIndex, lines.size)

                    saveBackup(file)
                    val resultLines = mutableListOf<String>()
                    resultLines.addAll(lines.subList(0, startIndex))
                    resultLines.addAll(newLines)
                    if (endIndex < lines.size) {
                        resultLines.addAll(lines.subList(endIndex, lines.size))
                    }

                    file.writeText(resultLines.joinToString("\n"))
                    val output = "Replaced lines $fromLine to ${fromLine + count - 1} in ${resolver.toLogicalPath(file)} with ${newLines.size} new lines (Total lines: ${resultLines.size})."
                    return successResult(callId, taskId, output, startTime, resolver.toLogicalPath(file))
                }

                "insert_after", "insert_before" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val lines = file.readLines().toMutableList()
                    val target = targetContent ?: return errorResult(callId, taskId, "targetContent (anchor) required", startTime)
                    val textToInsert = (replacementContent ?: content ?: "").lines()

                    val matchIndex = lines.indexOfFirst { it.contains(target) }
                    if (matchIndex == -1) {
                        return errorResult(callId, taskId, "Anchor target '$target' not found in file lines", startTime)
                    }

                    saveBackup(file)
                    val insertPos = if (operation.lowercase() == "insert_after") matchIndex + 1 else matchIndex
                    lines.addAll(insertPos, textToInsert)
                    file.writeText(lines.joinToString("\n"))

                    val output = "Inserted ${textToInsert.size} lines ${operation.lowercase().removePrefix("insert_")} line ${matchIndex + 1} ('${lines[matchIndex].take(40)}...')"
                    return successResult(callId, taskId, output, startTime, resolver.toLogicalPath(file))
                }

                "append", "append_section" -> {
                    file.parentFile?.mkdirs()
                    saveBackup(file)
                    val textToAppend = content ?: replacementContent ?: ""
                    val currentText = if (file.exists()) file.readText() else ""

                    val updated = buildString {
                        append(currentText)
                        if (currentText.isNotEmpty() && !currentText.endsWith("\n")) {
                            append("\n")
                        }
                        append(textToAppend)
                        if (!textToAppend.endsWith("\n")) {
                            append("\n")
                        }
                    }
                    file.writeText(updated)

                    val newLines = file.readLines().size
                    val output = "Successfully appended content to ${resolver.toLogicalPath(file)}. File now has $newLines lines (${file.length()} bytes)."
                    return successResult(
                        callId,
                        taskId,
                        output,
                        startTime,
                        resolver.toLogicalPath(file),
                        listOf(resolver.toLogicalPath(file))
                    )
                }

                "rollback", "undo" -> {
                    var previousVersion: String? = null
                    fileBackups.compute(file.absolutePath) { _, existing ->
                        if (existing.isNullOrEmpty()) {
                            null
                        } else {
                            val list = existing.toMutableList()
                            previousVersion = list.removeAt(list.size - 1)
                            list
                        }
                    }
                    if (previousVersion == null) {
                        return errorResult(callId, taskId, "No previous backup available for $path", startTime)
                    }
                    file.writeText(previousVersion!!)
                    return successResult(callId, taskId, "Successfully rolled back ${resolver.toLogicalPath(file)} to previous version.", startTime, resolver.toLogicalPath(file))
                }

                "delete" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    saveBackup(file)
                    val deleted = file.delete()
                    return if (deleted) {
                        successResult(callId, taskId, "Deleted file ${resolver.toLogicalPath(file)}", startTime, resolver.toLogicalPath(file))
                    } else {
                        errorResult(callId, taskId, "Failed to delete file", startTime)
                    }
                }

                else -> {
                    return errorResult(callId, taskId, "Unknown editor operation: '$operation'. Supported: view, create, replace, patch, replace_lines, insert_after, insert_before, append, rollback, delete", startTime)
                }
            }
        } catch (e: Exception) {
            return errorResult(callId, taskId, "Editor exception: ${e.message}", startTime)
        }
    }

    private fun saveBackup(file: File) {
        if (file.exists()) {
            val text = file.readText()
            fileBackups.compute(file.absolutePath) { _, existing ->
                val list = (existing ?: emptyList()).toMutableList()
                if (list.size >= 10) list.removeAt(0)
                list.add(text)
                list
            }
        }
    }

    private fun replaceNormalized(source: String, target: String, replacement: String): String? {
        val normSource = source.replace("\r\n", "\n")
        val normTarget = target.replace("\r\n", "\n")
        if (normSource.contains(normTarget)) {
            return normSource.replaceFirst(normTarget, replacement)
        }

        // Try trimming lines on target
        val trimmedTargetLines = normTarget.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (trimmedTargetLines.isEmpty()) return null

        val sourceLines = normSource.lines()
        for (i in 0..sourceLines.size - trimmedTargetLines.size) {
            var match = true
            for (j in trimmedTargetLines.indices) {
                if (sourceLines[i + j].trim() != trimmedTargetLines[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val before = sourceLines.subList(0, i).joinToString("\n")
                val after = sourceLines.subList(i + trimmedTargetLines.size, sourceLines.size).joinToString("\n")
                return buildString {
                    if (before.isNotEmpty()) {
                        append(before)
                        append("\n")
                    }
                    append(replacement)
                    if (after.isNotEmpty()) {
                        append("\n")
                        append(after)
                    }
                }
            }
        }
        return null
    }

    private fun successResult(
        callId: String,
        taskId: String,
        stdout: String,
        startTime: Long,
        workingDir: String,
        artifacts: List<String> = emptyList()
    ): ToolResult = ToolResult(
        callId = callId,
        taskId = taskId,
        success = true,
        exitCode = 0,
        stdout = stdout,
        stderr = "",
        durationMs = System.currentTimeMillis() - startTime,
        workingDirectory = workingDir,
        artifacts = artifacts
    )

    private fun errorResult(
        callId: String,
        taskId: String,
        error: String,
        startTime: Long
    ): ToolResult = ToolResult(
        callId = callId,
        taskId = taskId,
        success = false,
        exitCode = 1,
        stdout = "",
        stderr = error,
        durationMs = System.currentTimeMillis() - startTime,
        workingDirectory = "/workspace",
        errorType = "TEXT_EDITOR_ERROR",
        errorMessage = error
    )
}
