package com.example.aragon.tools

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.ToolResult
import java.io.File

class TextEditorTool {

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
                "view" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val lines = file.readLines()
                    val from = ((startLine ?: 1) - 1).coerceAtLeast(0)
                    val count = (lineCount ?: lines.size).coerceAtLeast(0)
                    val selected = lines.drop(from).take(count)

                    val output = selected.mapIndexed { idx, line ->
                        "${from + idx + 1}: $line"
                    }.joinToString("\n")

                    return successResult(callId, taskId, output, startTime, resolver.toLogicalPath(file))
                }

                "create" -> {
                    file.parentFile?.mkdirs()
                    file.writeText(content ?: "")
                    return successResult(
                        callId,
                        taskId,
                        "Created file at ${resolver.toLogicalPath(file)} (${file.length()} bytes)",
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

                    if (!oldText.contains(target)) {
                        return errorResult(callId, taskId, "targetContent not found in $path", startTime)
                    }

                    val newText = oldText.replace(target, replacement)
                    file.writeText(newText)
                    return successResult(
                        callId,
                        taskId,
                        "Replaced target text in ${resolver.toLogicalPath(file)}",
                        startTime,
                        resolver.toLogicalPath(file)
                    )
                }

                "patch" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val currentText = file.readText()
                    val target = targetContent ?: return errorResult(callId, taskId, "targetContent required for patch", startTime)
                    val replacement = replacementContent ?: ""

                    if (!currentText.contains(target)) {
                        return errorResult(callId, taskId, "Patch target block not found in file", startTime)
                    }

                    val updated = currentText.replaceFirst(target, replacement)
                    file.writeText(updated)
                    return successResult(callId, taskId, "Patch applied successfully", startTime, resolver.toLogicalPath(file))
                }

                "append" -> {
                    file.parentFile?.mkdirs()
                    val textToAppend = content ?: ""
                    file.appendText(if (file.exists() && file.length() > 0 && !file.readText().endsWith("\n")) "\n$textToAppend" else textToAppend)
                    return successResult(callId, taskId, "Appended content to ${resolver.toLogicalPath(file)}", startTime, resolver.toLogicalPath(file))
                }

                "delete" -> {
                    if (!file.exists()) {
                        return errorResult(callId, taskId, "File not found: $path", startTime)
                    }
                    val deleted = file.delete()
                    return if (deleted) {
                        successResult(callId, taskId, "Deleted file ${resolver.toLogicalPath(file)}", startTime, resolver.toLogicalPath(file))
                    } else {
                        errorResult(callId, taskId, "Failed to delete file", startTime)
                    }
                }

                else -> {
                    return errorResult(callId, taskId, "Unknown editor operation: $operation", startTime)
                }
            }
        } catch (e: Exception) {
            return errorResult(callId, taskId, "Editor exception: ${e.message}", startTime)
        }
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
        stderr: String,
        startTime: Long
    ): ToolResult = ToolResult(
        callId = callId,
        taskId = taskId,
        success = false,
        exitCode = 1,
        stdout = "",
        stderr = stderr,
        durationMs = System.currentTimeMillis() - startTime,
        workingDirectory = "/workspace",
        errorType = "EDITOR_ERROR",
        errorMessage = stderr
    )
}
