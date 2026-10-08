package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.FailedApproach
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.ToolResult
import org.json.JSONObject

enum class ReplanDecisionType {
    CONTINUE_CURRENT_STEP,
    REPAIR_CURRENT_STEP,
    CHANGE_STRATEGY,
    EXPONENTIAL_BACKOFF,
    FALLBACK_BUILTIN,
    SKIP_STEP,
    REQUEST_USER_INPUT,
    ABORT,
    COMPLETE
}

data class ReplanDecision(
    val type: ReplanDecisionType,
    val explanation: String,
    val suggestedTool: String? = null,
    val suggestedParameters: String? = null,
    val updatedSteps: List<PlanStep>? = null,
    val suggestedIntent: String? = null,
    val backoffDelayMs: Long = 0L
)

/**
 * Intelligent execution plan resilience engine.
 * Diagnoses root causes of command and tool failures, detects timeout and rate limit patterns,
 * applies exponential backoff, switches strategies away from broken dependencies to native generators,
 * and recovers file editing mistakes without infinite loops.
 */
class Replanner {

    fun analyzeAndReplan(
        task: Task,
        currentPlan: List<PlanStep>,
        failedApproaches: List<FailedApproach>,
        recentResults: List<Pair<String, ToolResult>>,
        artifacts: List<Artifact>,
        verificationResult: VerificationResult?,
        resolver: WorkspacePathResolver
    ): ReplanDecision {
        // 1. If verification already passed, complete immediately
        if (verificationResult?.isVerified == true) {
            return ReplanDecision(
                type = ReplanDecisionType.COMPLETE,
                explanation = "Verification succeeded: All required criteria and deliverables are satisfied.",
                suggestedIntent = "Deliver product artifacts and finalize session."
            )
        }

        val lastFailure = recentResults.lastOrNull { !it.second.success }
        val activeStep = currentPlan.find { it.status == StepStatus.IN_PROGRESS || it.status == StepStatus.PENDING }

        // 2. Comprehensive Error Diagnosis & Resilient Strategy Switching
        if (lastFailure != null) {
            val (toolName, result) = lastFailure
            val err = (result.errorMessage.orEmpty() + " " + result.stderr).lowercase()

            // 0. UNKNOWN_AFTER_PROCESS_DEATH: Must NEVER blindly retry
            if (result.status == com.example.aragon.domain.model.ToolExecutionStatus.UNKNOWN_AFTER_PROCESS_DEATH ||
                result.terminationReason == "PROCESS_DIED_BEFORE_RESULT") {
                return ReplanDecision(
                    type = ReplanDecisionType.CHANGE_STRATEGY,
                    explanation = "Host process died before result of $toolName was observed. External side effects may have occurred. Do NOT blindly repeat tool execution. Reconcile workspace state first.",
                    suggestedTool = "file_list",
                    suggestedParameters = """{"path": "/workspace"}""",
                    suggestedIntent = "RECONCILE_OR_INSPECT_FIRST: Inspect workspace/server state to verify if previous mutation occurred before process died."
                )
            }

            // 0.1 ARTIFACT_INVALID / VALIDATION_FAILED / STALE_ARTIFACT
            if (result.errorType == "ARTIFACT_INVALID" || result.errorType == "STALE_ARTIFACT" || result.errorType == "VALIDATION_FAILED") {
                val isDoc = toolName.contains("doc") || toolName.contains("word") || (result.errorMessage?.contains("DOCX") == true)
                val isXls = toolName.contains("sheet") || toolName.contains("excel") || (result.errorMessage?.contains("XLSX") == true)
                return when {
                    isDoc -> ReplanDecision(
                        type = ReplanDecisionType.FALLBACK_BUILTIN,
                        explanation = "Artifact failed structural validation (${result.errorMessage ?: "invalid format"}). Switching to native validated OpenXML document_create tool with structured paragraphs and tables.",
                        suggestedTool = "document_create",
                        suggestedParameters = JSONObject().apply {
                            put("filename", "/artifacts/report.docx")
                            put("title", task.title)
                        }.toString(),
                        suggestedIntent = "Generate document using native validated OpenXML generator document_create."
                    )
                    isXls -> ReplanDecision(
                        type = ReplanDecisionType.FALLBACK_BUILTIN,
                        explanation = "Spreadsheet artifact failed structural validation (${result.errorMessage ?: "invalid format"}). Switching to native validated spreadsheet_create tool with structured sheets and rows.",
                        suggestedTool = "spreadsheet_create",
                        suggestedParameters = JSONObject().apply {
                            put("filename", "/artifacts/data.xlsx")
                            put("sheetName", "Data")
                        }.toString(),
                        suggestedIntent = "Generate spreadsheet workbook using native validated spreadsheet_create."
                    )
                    else -> ReplanDecision(
                        type = ReplanDecisionType.REPAIR_CURRENT_STEP,
                        explanation = "Delivered artifact failed structural validation: ${result.errorMessage ?: "corrupt or incomplete output"}. Inspect or recreate file using native tools.",
                        suggestedTool = "inspect_file",
                        suggestedIntent = "Inspect and recreate invalid artifact."
                    )
                }
            }

            // A. TIMEOUTS: Apply exponential backoff and break operation into sub-steps
            if (result.timedOut || err.contains("timed out") || err.contains("timeout")) {
                val attemptCount = failedApproaches.count { it.error.lowercase().contains("timeout") } + 1
                val backoffMs = (1000L * (1 shl attemptCount.coerceAtMost(4))) + (100..400).random()
                return ReplanDecision(
                    type = ReplanDecisionType.EXPONENTIAL_BACKOFF,
                    explanation = "Operation timed out during $toolName. Applying exponential backoff (${backoffMs}ms) and splitting task into smaller units.",
                    suggestedTool = if (toolName == "playwright_browser") "playwright_browser" else "run_command",
                    suggestedIntent = "Retry with smaller chunk size and increased execution headroom.",
                    backoffDelayMs = backoffMs
                )
            }

            // B. RATE LIMITS / HTTP 429
            if (err.contains("429") || err.contains("rate limit") || err.contains("quota exceeded") || err.contains("too many requests")) {
                val backoffMs = 2500L * (failedApproaches.size + 1)
                return ReplanDecision(
                    type = ReplanDecisionType.EXPONENTIAL_BACKOFF,
                    explanation = "API or upstream rate limit reached (HTTP 429). Pausing with exponential backoff before continuing.",
                    suggestedIntent = "Wait for rate limit recovery window.",
                    backoffDelayMs = backoffMs
                )
            }

            // C. PYTHON MISSING MODULES / EXTERNAL LIBS -> Fall back to built-in native generators
            if (err.contains("no module named") || err.contains("modulenotfounderror") || err.contains("nameerror")) {
                val isDocRequest = task.title.lowercase().contains("doc") || task.title.lowercase().contains("report") || task.title.lowercase().contains("word")
                val isSpreadsheetRequest = task.title.lowercase().contains("sheet") || task.title.lowercase().contains("excel") || task.title.lowercase().contains("csv")

                return when {
                    isDocRequest -> ReplanDecision(
                        type = ReplanDecisionType.FALLBACK_BUILTIN,
                        explanation = "Python python-docx / reportlab library not found. Switching to built-in validated OpenXML document_create generator.",
                        suggestedTool = "document_create",
                        suggestedParameters = JSONObject().apply {
                            put("filename", "/artifacts/report.docx")
                            put("title", task.title)
                        }.toString(),
                        suggestedIntent = "Use built-in OpenXML generator to produce deliverable without python dependencies."
                    )
                    isSpreadsheetRequest -> ReplanDecision(
                        type = ReplanDecisionType.FALLBACK_BUILTIN,
                        explanation = "Python openpyxl / pandas library not found. Switching to built-in validated OpenXML spreadsheet_create generator.",
                        suggestedTool = "spreadsheet_create",
                        suggestedParameters = JSONObject().apply {
                            put("filename", "/artifacts/data.xlsx")
                            put("sheetName", "Summary")
                        }.toString(),
                        suggestedIntent = "Use built-in XLSX workbook generator to produce deliverable."
                    )
                    else -> ReplanDecision(
                        type = ReplanDecisionType.CHANGE_STRATEGY,
                        explanation = "Switching strategy: Python missing external dependency ($err). Executing via standard library or pure bash POSIX builtins.",
                        suggestedTool = "run_command",
                        suggestedIntent = "Execute using standard library or POSIX command line."
                    )
                }
            }

            // D. FILE EDITING / PATCH ERRORS (targetContent not found)
            if (err.contains("targetcontent not found") || err.contains("patch target block") || err.contains("target string not found")) {
                return ReplanDecision(
                    type = ReplanDecisionType.REPAIR_CURRENT_STEP,
                    explanation = "File patch failed due to whitespace/formatting mismatch. Viewing file lines first to inspect exact contents before re-applying patch.",
                    suggestedTool = "text_editor",
                    suggestedParameters = JSONObject().apply {
                        put("operation", "view")
                        put("path", "/workspace")
                    }.toString(),
                    suggestedIntent = "View existing file structure to ensure precise replacement target."
                )
            }

            // E. COMMAND NOT FOUND
            if (err.contains("command not found") || err.contains("not found: ") || result.exitCode == 127) {
                return ReplanDecision(
                    type = ReplanDecisionType.CHANGE_STRATEGY,
                    explanation = "Command '$toolName' or binary not found in container PATH. Switching to native tool implementation.",
                    suggestedTool = "run_command",
                    suggestedIntent = "Switch to native container tools."
                )
            }

            // F. PERMISSION DENIED
            if (err.contains("permission denied") || result.exitCode == 126) {
                return ReplanDecision(
                    type = ReplanDecisionType.REPAIR_CURRENT_STEP,
                    explanation = "Permission denied. Redirecting file target to /workspace and setting write permissions.",
                    suggestedTool = "run_command",
                    suggestedParameters = """{"command": "chmod -R 777 ${resolver.workspaceDir.absolutePath}"}""",
                    suggestedIntent = "Grant workspace permissions and retry."
                )
            }
        }

        // 3. Prevent Infinite Failure Cycles (halt after 5 failed approaches)
        if (failedApproaches.size >= 5) {
            return ReplanDecision(
                type = ReplanDecisionType.ABORT,
                explanation = "Exhausted 5 alternative strategies without successful convergence. Halting session safely to prevent infinite loops.",
                suggestedIntent = "Session terminated: strategy recovery limit reached."
            )
        }

        // 4. Default: repair active step and continue
        return ReplanDecision(
            type = ReplanDecisionType.REPAIR_CURRENT_STEP,
            explanation = "Self-correcting parameters for active step '${activeStep?.title ?: "execution"}'.",
            suggestedIntent = "Retry step with calibrated arguments."
        )
    }
}
