package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.ToolExecutionStatus
import com.example.aragon.domain.model.ToolResult
import com.example.aragon.tools.ToolDispatcher
import com.example.aragon.tools.ToolRegistry
import java.io.File

sealed interface Observation {
    val timestamp: Long
    val summary: String
    val fullOutputRef: String?

    data class Terminal(
        override val timestamp: Long = System.currentTimeMillis(),
        val command: String,
        val exitCode: Int,
        val stdoutSnippet: String,
        val stderrSnippet: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class FileOps(
        override val timestamp: Long = System.currentTimeMillis(),
        val operation: String,
        val logicalPath: String,
        val success: Boolean,
        val details: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class Browser(
        override val timestamp: Long = System.currentTimeMillis(),
        val action: String,
        val url: String,
        val title: String,
        val extractedTextSnippet: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class Process(
        override val timestamp: Long = System.currentTimeMillis(),
        val processId: Long,
        val status: String,
        val cpuUsagePercent: Float,
        val memoryUsageMb: Long,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class ArtifactObs(
        override val timestamp: Long = System.currentTimeMillis(),
        val filename: String,
        val logicalPath: String,
        val mimeType: String,
        val sizeBytes: Long,
        val isValid: Boolean,
        val stage: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class Environment(
        override val timestamp: Long = System.currentTimeMillis(),
        val isUbuntuReady: Boolean,
        val isPythonAvailable: Boolean,
        val isNetworkAvailable: Boolean,
        val freeStorageMb: Long,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    /**
     * Authoritative structured observation capturing full ground-truth feedback
     * from any tool invocation in the runtime environment.
     */
    data class ToolExecutionObs(
        val callId: String,
        val toolName: String,
        val canonicalToolName: String,
        val argumentsJson: String,
        val terminalState: String, // "SUCCEEDED", "FAILED", "CANCELLED", "UNKNOWN_AFTER_PROCESS_DEATH", "SANDBOX_FAILURE", "SANDBOX_SYNC_FAILURE", "MCP_FAILURE", "ARTIFACT_INVALID", "AWAITING_APPROVAL"
        val status: ToolExecutionStatus,
        val exitCode: Int,
        val exitCodeDisplay: String,
        val stdout: String,
        val stderr: String,
        val isStdoutBlank: Boolean,
        val isStderrBlank: Boolean,
        val artifacts: List<String>,
        val verifiedArtifacts: List<String>,
        val validationDetails: String?,
        val durationMs: Long,
        val workingDirectory: String,
        val environment: String,
        val errorType: String?,
        val errorMessage: String?,
        val terminationReason: String?,
        val timedOut: Boolean,
        val cancelled: Boolean,
        val isReadOnly: Boolean,
        val semantics: ToolDispatcher.Companion.OperationSemantics = ToolDispatcher.Companion.classifyOperation(canonicalToolName, argumentsJson),
        val retrySafety: String = if (isReadOnly) "SAFE (Read-only inspection)" else "CAUTION (Inspect state before retrying)",
        override val timestamp: Long,
        override val fullOutputRef: String?,
        override val summary: String
    ) : Observation {

        fun toFormattedPrompt(maxInlineLength: Int = 1200, resolver: WorkspacePathResolver? = null): String {
            return buildString {
                appendLine("=== TOOL OBSERVATION ===")
                appendLine("Tool Call ID: $callId")
                appendLine("Tool: $toolName (Canonical: $canonicalToolName)")
                if (argumentsJson.isNotBlank() && argumentsJson != "{}") {
                    appendLine("Normalized Arguments: $argumentsJson")
                }
                appendLine("Operation Category: [${semantics.name}]")
                appendLine("Retry Safety: $retrySafety")
                appendLine("Environment: $environment ($workingDirectory)")
                appendLine("Terminal State: $terminalState")
                val successLabel = if (terminalState == "UNKNOWN_AFTER_PROCESS_DEATH") "UNKNOWN (Result not observed)" else "${status == ToolExecutionStatus.SUCCEEDED}"
                appendLine("Execution Status: ${status.name} (Success: $successLabel, ExitCode: $exitCodeDisplay)")

                if (!fullOutputRef.isNullOrBlank()) {
                    appendLine("Authoritative Observation Log: $fullOutputRef")
                } else if (resolver != null) {
                    appendLine("Authoritative Observation Log: /workspace/.aragon/observations/obs_${callId}.log")
                }

                if (!terminationReason.isNullOrBlank()) {
                    appendLine("Termination Reason: $terminationReason")
                }
                if (timedOut) {
                    appendLine("Timed Out: true")
                }
                if (cancelled) {
                    appendLine("Cancelled: true")
                }
                if (durationMs > 0) {
                    appendLine("Duration: ${durationMs}ms")
                }
                if (!errorType.isNullOrBlank()) {
                    appendLine("Error Type: $errorType")
                }
                if (!errorMessage.isNullOrBlank()) {
                    appendLine("Error Message: $errorMessage")
                }

                if (terminalState == "UNKNOWN_AFTER_PROCESS_DEATH") {
                    appendLine("==================================================")
                    appendLine("STATUS = UNKNOWN_AFTER_PROCESS_DEATH")
                    appendLine("RESULT = NOT_OBSERVED")
                    appendLine("RETRY = DO_NOT_BLINDLY_RETRY")
                    appendLine("ACTION = RECONCILE_OR_INSPECT_FIRST")
                    appendLine("Directive: Host process died before tool execution result was observed. External side effects (e.g. filesystem mutation, remote MCP call, sandbox command, or HTTP request) may have already taken effect. Do NOT blindly repeat non-idempotent mutations. Inspect current workspace/server state first (e.g. via file_list, inspect_file) to reconcile.")
                    appendLine("==================================================")
                }

                if (terminalState == "ARTIFACT_INVALID") {
                    appendLine("==================================================")
                    appendLine("STATUS = ARTIFACT_INVALID")
                    appendLine("Directive: Delivered artifact failed structural format or integrity validation on disk. ${validationDetails ?: errorMessage ?: ""}")
                    appendLine("==================================================")
                }

                if (artifacts.isNotEmpty()) {
                    appendLine("Artifacts Produced: ${artifacts.joinToString()}")
                    if (resolver != null) {
                        val diskVerification = artifacts.map { p ->
                            val f = resolver.resolve(p)
                            if (f.exists() && f.isFile) "$p (verified on disk: ${f.length()}B)" else "$p (MISSING ON DISK)"
                        }.joinToString("; ")
                        appendLine("Artifact Verification: $diskVerification")
                    }
                }

                appendLine("Timestamp: $timestamp")

                // Explicit STDOUT handling (Scenario C: Empty stdout rendered distinctly from missing/omitted stdout)
                if (isStdoutBlank) {
                    appendLine("STDOUT:\n(empty - 0 bytes produced)")
                } else {
                    val wasCapped = stdout.contains("[STDOUT Capped") || stdout.contains("[STDOUT Truncated")
                    val outTrunc = if (stdout.length > maxInlineLength) {
                        val logRef = fullOutputRef ?: "/workspace/.aragon/observations/obs_${callId}.log"
                        val note = if (wasCapped) {
                            "Retained diagnostic buffer (${stdout.length} chars) stored in $logRef (original output exceeded retention limit)"
                        } else {
                            "Complete retained output (${stdout.length} chars) stored in $logRef"
                        }
                        "${stdout.take(maxInlineLength)}\n...[Inline output truncated at $maxInlineLength chars. $note]"
                    } else {
                        stdout
                    }
                    appendLine("STDOUT:\n$outTrunc")
                }

                // Explicit STDERR handling
                if (isStderrBlank) {
                    appendLine("STDERR:\n(empty - 0 bytes error)")
                } else {
                    val wasCapped = stderr.contains("[STDERR Capped") || stderr.contains("[STDERR Truncated")
                    val errTrunc = if (stderr.length > maxInlineLength) {
                        val logRef = fullOutputRef ?: "/workspace/.aragon/observations/obs_${callId}.log"
                        val note = if (wasCapped) {
                            "Retained error buffer (${stderr.length} chars) stored in $logRef (stream exceeded retention limit)"
                        } else {
                            "Complete retained error (${stderr.length} chars) stored in $logRef"
                        }
                        "${stderr.take(maxInlineLength)}\n...[Inline error truncated at $maxInlineLength chars. $note]"
                    } else {
                        stderr
                    }
                    appendLine("STDERR:\n$errTrunc")
                }
            }
        }

        companion object {
            fun fromToolResult(
                toolName: String,
                result: ToolResult,
                resolver: WorkspacePathResolver? = null,
                callArgsOverride: String? = null
            ): ToolExecutionObs {
                val canonical = ToolRegistry.resolveCanonicalToolName(if (toolName.isNotBlank()) toolName else result.toolName)
                val callArgs = callArgsOverride ?: (if (result.argumentsJson.isNotBlank() && result.argumentsJson != "{}") result.argumentsJson else "{}")
                val semantics = ToolDispatcher.Companion.classifyOperation(canonical, callArgs)
                val isReadOnly = semantics == ToolDispatcher.Companion.OperationSemantics.READ_ONLY

                val terminalState = when {
                    result.status == ToolExecutionStatus.UNKNOWN_AFTER_PROCESS_DEATH -> "UNKNOWN_AFTER_PROCESS_DEATH"
                    result.terminationReason == "PROCESS_DIED_BEFORE_RESULT" || (result.errorType == "PROCESS_TERMINATED" && result.exitCode == -1) -> "UNKNOWN_AFTER_PROCESS_DEATH"
                    result.errorType == "PROCESS_TERMINATED" -> "PROCESS_TERMINATED"
                    result.status == ToolExecutionStatus.AWAITING_APPROVAL || result.terminationReason == "AWAITING_APPROVAL" -> "AWAITING_APPROVAL"
                    result.errorType == "OPENSANDBOX_SYNC_FAILED" -> "SANDBOX_SYNC_FAILURE"
                    result.errorType?.startsWith("OPENSANDBOX") == true -> "SANDBOX_FAILURE"
                    result.errorType == "MCP_ERROR" -> "MCP_FAILURE"
                    result.errorType == "ARTIFACT_INVALID" || result.errorType == "STALE_ARTIFACT" ||
                        result.errorType == "VALIDATION_FAILED" || result.errorType == "WRITE_VERIFICATION_FAILED" -> "ARTIFACT_INVALID"
                    result.cancelled || result.status == ToolExecutionStatus.CANCELLED -> "CANCELLED"
                    result.success || result.status == ToolExecutionStatus.SUCCEEDED -> "SUCCEEDED"
                    else -> "FAILED"
                }

                val retrySafety = when {
                    terminalState == "UNKNOWN_AFTER_PROCESS_DEATH" -> "UNSAFE_WITHOUT_RECONCILIATION (Inspect workspace state before repeating)"
                    terminalState == "CANCELLED" -> "SAFE_TO_RESUME (Operation was cancelled before completion)"
                    terminalState == "ARTIFACT_INVALID" -> "REPAIR_REQUIRED (Format/schema validation failed; use native generator or fix structure)"
                    semantics == ToolDispatcher.Companion.OperationSemantics.READ_ONLY -> "SAFE (Read-only inspection has no side effects)"
                    semantics == ToolDispatcher.Companion.OperationSemantics.IDEMPOTENT_MUTATION -> "SAFE (Idempotent mutation produces identical state on repeat)"
                    semantics == ToolDispatcher.Companion.OperationSemantics.NON_IDEMPOTENT_MUTATION -> "CAUTION (Non-idempotent mutation may duplicate effects; verify state first)"
                    else -> "CONSERVATIVE (Side-effects unverified; inspect before retrying)"
                }

                val exitCodeDisplay = if (terminalState == "UNKNOWN_AFTER_PROCESS_DEATH") {
                    "None (Unobserved - Process Died Before Result)"
                } else {
                    "${result.exitCode}"
                }

                val fullLogPath = if (resolver != null) {
                    resolver.toLogicalPath(File(resolver.observationsDir, "obs_${result.callId}.log"))
                } else {
                    "/workspace/.aragon/observations/obs_${result.callId}.log"
                }

                val effectiveStatus = when {
                    terminalState == "UNKNOWN_AFTER_PROCESS_DEATH" -> ToolExecutionStatus.UNKNOWN_AFTER_PROCESS_DEATH
                    terminalState == "CANCELLED" -> ToolExecutionStatus.CANCELLED
                    result.success -> ToolExecutionStatus.SUCCEEDED
                    else -> ToolExecutionStatus.FAILED
                }

                val summaryText = when {
                    terminalState == "UNKNOWN_AFTER_PROCESS_DEATH" -> "$canonical outcome unobserved (host process died before result)"
                    result.success -> "$canonical succeeded (${result.durationMs}ms, ${result.artifacts.size} artifacts)"
                    else -> "$canonical failed (${result.errorType ?: "exit ${result.exitCode}"}: ${result.errorMessage ?: result.stderr.take(80)})"
                }

                return ToolExecutionObs(
                    callId = result.callId,
                    toolName = if (toolName.isNotBlank()) toolName else result.toolName,
                    canonicalToolName = canonical,
                    argumentsJson = callArgs,
                    terminalState = terminalState,
                    status = effectiveStatus,
                    exitCode = result.exitCode,
                    exitCodeDisplay = exitCodeDisplay,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    isStdoutBlank = result.stdout.isBlank(),
                    isStderrBlank = result.stderr.isBlank(),
                    artifacts = result.artifacts,
                    verifiedArtifacts = result.artifacts,
                    validationDetails = if (terminalState == "ARTIFACT_INVALID") (result.errorMessage ?: result.stderr) else null,
                    durationMs = result.durationMs,
                    workingDirectory = result.workingDirectory,
                    environment = result.environment,
                    errorType = result.errorType,
                    errorMessage = result.errorMessage,
                    terminationReason = result.terminationReason,
                    timedOut = result.timedOut,
                    cancelled = result.cancelled,
                    isReadOnly = isReadOnly,
                    semantics = semantics,
                    retrySafety = retrySafety,
                    timestamp = if (result.completedAt > 0L) result.completedAt else System.currentTimeMillis(),
                    fullOutputRef = fullLogPath,
                    summary = summaryText
                )
            }
        }
    }
}

