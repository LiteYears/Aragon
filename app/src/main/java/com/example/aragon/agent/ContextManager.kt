package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.data.local.ToolExecutionEntity
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.FailedApproach
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.Project
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.ToolResult
import com.example.aragon.llm.LlmMessage
import com.example.aragon.llm.LlmRole
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ContextManager(
    private val maxInlineOutputLength: Int = 1200,
    private val maxRecentResultsInline: Int = 6
) {
    private val failedApproachesByTask = java.util.concurrent.ConcurrentHashMap<String, MutableList<FailedApproach>>()

    fun recordFailedApproach(taskId: String, strategy: String, error: String, context: String) {
        val list = failedApproachesByTask.computeIfAbsent(taskId) { java.util.Collections.synchronizedList(mutableListOf()) }
        list.add(
            FailedApproach(
                id = UUID.randomUUID().toString(),
                strategy = strategy,
                error = error.take(300),
                context = context.take(300),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordFailedApproach(strategy: String, error: String, context: String) {
        recordFailedApproach("default", strategy, error, context)
    }

    fun getFailedApproaches(taskId: String): List<FailedApproach> {
        val list = failedApproachesByTask[taskId] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun getFailedApproaches(): List<FailedApproach> = getFailedApproaches("default")

    fun storeObservation(
        resolver: WorkspacePathResolver,
        callId: String,
        stdout: String,
        stderr: String
    ): String {
        val obsDir = resolver.observationsDir
        obsDir.mkdirs()

        val obsFile = File(obsDir, "obs_${callId}.log")
        val stdoutHeader = if (stdout.contains("[STDOUT Capped") || stdout.contains("[STDOUT Truncated")) {
            "=== STDOUT (Retained buffer: ${stdout.length} chars; stream capped at memory retention limit) ==="
        } else {
            "=== STDOUT (Retained buffer: ${stdout.length} chars) ==="
        }
        val stderrHeader = if (stderr.contains("[STDERR Capped") || stderr.contains("[STDERR Truncated")) {
            "=== STDERR (Retained buffer: ${stderr.length} chars; stream capped at memory retention limit) ==="
        } else {
            "=== STDERR (Retained buffer: ${stderr.length} chars) ==="
        }
        obsFile.writeText("$stdoutHeader\n$stdout\n\n$stderrHeader\n$stderr\n")
        return resolver.toLogicalPath(obsFile)
    }

    fun buildSystemPrompt(
        project: Project?,
        resolver: WorkspacePathResolver
    ): String {
        return buildString {
            appendLine("You are ARAGON, a personal autonomous computer agent operating inside a local Linux userspace.")
            appendLine()
            appendLine("### CORE EXECUTION PRINCIPLES:")
            appendLine("1. THE RUNTIME IS AUTHORITATIVE. Your claims in dialogue are hypotheses until verified on disk via tools.")
            appendLine("2. LOGICAL FILESYSTEM:")
            appendLine("   - /workspace (primary task files)")
            appendLine("   - /process (scratch files, raw downloads, intermediate outputs)")
            appendLine("   - /artifacts (final deliverables: DOCX, XLSX, PDF, web applications)")
            appendLine("   - /tmp (temporary caches)")
            appendLine("3. STRUCTURED TOOL CALLS MANDATORY. Do NOT output plain text claiming you created a file without executing the tool!")
            appendLine("4. CREATING REPORTS & DOCUMENTS: When asked to generate a DOCX report, audit, or document with tables and metrics, use the `document_create` tool. Provide filename (e.g. /artifacts/report.docx), title, paragraphs, and structured table data.")
            appendLine("5. CREATING SPREADSHEETS: When asked to generate Excel or spreadsheet workbooks, use the `spreadsheet_create` tool. Provide filename (e.g. /artifacts/data.xlsx), sheetName, headers, and structured rows.")
            appendLine("6. AGENTIC DATA & RESEARCH TOOLS:")
            appendLine("   - `csv_analyze`: Inspect CSV columns, compute aggregations (mean, sum, min, max, count), and review rows.")
            appendLine("   - `json_query`: Extract fields and arrays using path selectors from JSON files or strings.")
            appendLine("   - `http_request`: Perform custom HTTP/REST requests (GET, POST, PUT, DELETE) with headers and JSON body.")
            appendLine("   - `archive_manage`: Package deliverables into ZIP files or extract ZIP archives.")
            appendLine("   - `sandbox_manage`: Manage OpenSandbox microVM/container instances (status, metrics, packages, spawn, terminate).")
            appendLine("   - `python_execute`: Write, hash-verify, and run Python code.")
            appendLine("   - `run_command`: Execute shell commands (e.g. ls, cat, head, tail, grep, find, wc, env).")
            appendLine("7. VERIFICATION MANDATE: Tasks only complete when objective artifacts exist, are non-empty, and structurally valid on disk. Text-only claims of completion will be rejected by the runtime.")
            appendLine("8. CONTEXT TRANSFER: Large command and observation logs are stored on disk in /workspace/.aragon/observations/.")
            appendLine()

            if (project != null && project.instructions.isNotBlank()) {
                appendLine("### PROJECT DIRECTIVE (${project.name}):")
                appendLine(project.instructions)
                appendLine()
            }

            // Retrieve durable notes if available
            val memory = retrieveRelevantMemory(resolver)
            if (memory.isNotBlank()) {
                appendLine("### DURABLE MEMORY:")
                appendLine(memory)
                appendLine()
            }
        }
    }

    fun buildConversationMessages(
        task: Task,
        project: Project?,
        resolver: WorkspacePathResolver,
        planSteps: List<PlanStep>,
        recentToolResults: List<Pair<String, ToolResult>>,
        artifacts: List<Artifact>,
        loopWarning: String? = null,
        verificationResult: VerificationResult? = null,
        replanningDirective: String? = null,
        unverifiedClaimFeedback: String? = null,
        allExecutionSummaries: List<ToolExecutionEntity>? = null
    ): List<LlmMessage> {
        val messages = mutableListOf<LlmMessage>()

        // 1. System Prompt
        messages.add(
            LlmMessage(
                role = LlmRole.SYSTEM,
                content = buildSystemPrompt(project, resolver)
            )
        )

        // 2. User Objective
        messages.add(
            LlmMessage(
                role = LlmRole.USER,
                content = "Task Objective: ${task.originalRequest}"
            )
        )

        // 3. Compact state overview presented as execution environment context
        val stateSummary = buildString {
            appendLine("### ENVIRONMENT & EXECUTION STATE:")
            appendLine("- Status: ${task.status}")
            appendLine("- Iteration: ${task.iteration}")
            appendLine("- Current Objective: ${task.currentObjective.ifBlank { task.title }}")

            // Authoritative Objective Verification Ground Truth (Scenario E & Area 3: Ground Truth vs Model Inference)
            appendLine("\n### OBJECTIVE VERIFICATION GROUND TRUTH (AUTHORITATIVE):")
            if (verificationResult != null) {
                val overallState = if (verificationResult.isVerified) "[SATISFIED ✓]" else "[INCOMPLETE / PENDING ✗]"
                appendLine("- Verification State: $overallState")
                appendLine("- Ground Truth Summary: ${verificationResult.summary}")
                if (verificationResult.criteria.isNotEmpty()) {
                    appendLine("- Goal Criteria Checklist:")
                    verificationResult.criteria.forEach { crit ->
                        val mark = if (crit.isSatisfied) "[SATISFIED ✓]" else "[PENDING ✗]"
                        appendLine("  * $mark [${crit.targetType}] ${crit.description} (Evidence: ${crit.evidence.ifBlank { "None yet" }})")
                    }
                }
                val failingChecks = verificationResult.checks.filter { !it.passed }
                if (failingChecks.isNotEmpty()) {
                    appendLine("- Unmet Checks On Disk:")
                    failingChecks.forEach { chk ->
                        appendLine("  * [UNMET] ${chk.name}: ${chk.details}")
                    }
                }
            } else {
                appendLine("- Verification State: [PENDING EVALUATION]")
            }
            appendLine("Note: The above verification state is computed independently by the runtime on disk. Textual claims of completion in dialogue are hypotheses and do NOT satisfy criteria.")

            // Authoritative Prior Actions Ledger (Bounded to prevent context window exhaustion and deduplicated from inline recent observations)
            val allExecs = allExecutionSummaries ?: emptyList()
            val inlineCount = recentToolResults.takeLast(maxRecentResultsInline).size
            val priorExecs = if (allExecs.size > inlineCount) {
                allExecs.dropLast(inlineCount)
            } else {
                emptyList()
            }

            if (priorExecs.isNotEmpty()) {
                appendLine("\n### PRIOR ACTIONS LEDGER (HISTORICAL):")
                val maxPriorLedgerEntries = 20
                if (priorExecs.size > maxPriorLedgerEntries) {
                    val omittedCount = priorExecs.size - maxPriorLedgerEntries
                    appendLine("... [$omittedCount earlier historical operations recorded in durable database; omitted from prompt to conserve context] ...")
                }
                val visiblePrior = priorExecs.takeLast(maxPriorLedgerEntries)
                val baseIndex = priorExecs.size - visiblePrior.size
                visiblePrior.forEachIndexed { idx, ex ->
                    val statusMark = when (ex.status) {
                        "SUCCEEDED" -> "[SUCCEEDED]"
                        "FAILED" -> "[FAILED]"
                        "CANCELLED" -> "[CANCELLED]"
                        "UNKNOWN_AFTER_PROCESS_DEATH" -> "[UNKNOWN - PROCESS DIED]"
                        else -> "[${ex.status}]"
                    }
                    val artStr = if (ex.artifacts.isNotEmpty()) " -> Artifacts: ${ex.artifacts.joinToString()}" else ""
                    val errStr = if (!ex.errorMessage.isNullOrBlank()) " (${ex.errorMessage})" else ""
                    appendLine("${baseIndex + idx + 1}. $statusMark ${ex.toolName} ${ex.argumentsJson.take(120)}$artStr$errStr (Exit: ${ex.exitCode})")
                }
            }

            if (planSteps.isNotEmpty()) {
                appendLine("\n### HIERARCHICAL PLAN:")
                planSteps.forEach { step ->
                    val mark = when (step.status) {
                        com.example.aragon.domain.model.StepStatus.COMPLETED -> "[✓]"
                        com.example.aragon.domain.model.StepStatus.IN_PROGRESS -> "[▶]"
                        com.example.aragon.domain.model.StepStatus.FAILED -> "[✗]"
                        com.example.aragon.domain.model.StepStatus.BLOCKED -> "[!]"
                        else -> "[ ]"
                    }
                    appendLine("$mark Step ${step.stepNumber}: ${step.title}")
                }
            }

            if (artifacts.isNotEmpty()) {
                appendLine("\n### DISCOVERED ARTIFACTS ON DISK:")
                artifacts.forEach { art ->
                    val verifiedTag = if (art.verified && art.existsOnDisk) "VERIFIED" else "UNVERIFIED"
                    appendLine("- ${art.filename} (${art.size}B, Stage: ${art.stage}, [$verifiedTag]) -> ${art.logicalPath}")
                }
            }

            val taskFailures = getFailedApproaches(task.id)
            if (taskFailures.isNotEmpty()) {
                appendLine("\n### FAILED APPROACH MEMORY (DO NOT REPEAT):")
                taskFailures.takeLast(3).forEach {
                    appendLine("- Strategy '${it.strategy}' failed: ${it.error}")
                }
            }

            if (!replanningDirective.isNullOrBlank()) {
                appendLine("\n🔄 REPLANNING DIRECTIVE: $replanningDirective")
            }

            if (!loopWarning.isNullOrBlank()) {
                appendLine("\n⚠️ RUNTIME DIRECTIVE: $loopWarning")
            }

            if (!unverifiedClaimFeedback.isNullOrBlank()) {
                appendLine("\n⚠️ RUNTIME CORRECTION: $unverifiedClaimFeedback")
            }
        }

        messages.add(
            LlmMessage(
                role = LlmRole.USER,
                content = stateSummary
            )
        )

        // 4. Recent tool executions formatted with ToolExecutionObs (Scenario C, D, F, H)
        val resultsToInclude = recentToolResults.takeLast(maxRecentResultsInline)
        for ((toolName, result) in resultsToInclude) {
            val callName = if (toolName.isNotBlank()) toolName else result.toolName
            val callArgs = if (result.argumentsJson.isNotBlank() && result.argumentsJson != "{}") result.argumentsJson else "{}"

            val isParseFailure = result.errorType == "PARSE_FAILURE" || callName == "[PARSE_FAILURE]" || callName == "parse_tool_call"
            if (isParseFailure) {
                // Structured internal parse-failure observation: NOT a fake executable tool
                val parseFailureObservation = buildString {
                    appendLine("=== PARSE_FAILURE OBSERVATION ===")
                    appendLine("Terminal State: PARSE_FAILURE")
                    appendLine("Reason: ${result.errorMessage ?: "Malformed tool call syntax"}")
                    if (result.stderr.isNotBlank()) {
                        appendLine("Details:\n${result.stderr}")
                    }
                    appendLine("Directive: The previous model response attempted a tool invocation but failed to parse. No action was executed and no side effects occurred. Do not attempt to invoke 'parse_tool_call'. Format your next tool call using valid structured JSON or tags.")
                }
                messages.add(
                    LlmMessage(
                        role = LlmRole.USER,
                        content = parseFailureObservation
                    )
                )
                continue
            }

            val toolExecutionObs = Observation.ToolExecutionObs.fromToolResult(
                toolName = callName,
                result = result,
                resolver = resolver,
                callArgsOverride = callArgs
            )
            val observationContent = toolExecutionObs.toFormattedPrompt(
                maxInlineLength = maxInlineOutputLength,
                resolver = resolver
            )

            // Must have assistant message with matching toolCalls immediately before tool message
            messages.add(
                LlmMessage(
                    role = LlmRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        com.example.aragon.llm.LlmToolCall(
                            id = result.callId,
                            name = callName,
                            argumentsJson = callArgs
                        )
                    )
                )
            )

            messages.add(
                LlmMessage(
                    role = LlmRole.TOOL,
                    name = callName,
                    toolCallId = result.callId,
                    content = observationContent
                )
            )
        }

        return messages
    }

    fun compressContext(
        resolver: WorkspacePathResolver,
        task: Task,
        messages: List<LlmMessage>
    ): String {
        val summaryFile = File(resolver.memoryDir, "context_summary.md")
        val summaryContent = buildString {
            appendLine("# Compressed Context Transfer Snapshot")
            appendLine("Task: ${task.title} (ID: ${task.id})")
            appendLine("Timestamp: ${System.currentTimeMillis()}")
            appendLine("Original Request: ${task.originalRequest}")
            appendLine("Iterations Completed: ${task.iteration}")
            appendLine("Messages Count: ${messages.size}")
            appendLine("\n## Key Decisions and Failures:")
            val failures = (getFailedApproaches(task.id) + getFailedApproaches("default")).distinctBy { it.id }
            failures.forEach {
                appendLine("- Failed: ${it.strategy} (${it.error})")
            }
        }
        summaryFile.writeText(summaryContent)
        return resolver.toLogicalPath(summaryFile)
    }

    fun retrieveRelevantMemory(resolver: WorkspacePathResolver): String {
        val notesFile = File(resolver.memoryDir, "notes.md")
        val summaryFile = File(resolver.memoryDir, "context_summary.md")

        val sb = StringBuilder()
        if (notesFile.exists() && notesFile.length() > 0L) {
            sb.appendLine(notesFile.readText().take(1500))
        }
        if (summaryFile.exists() && summaryFile.length() > 0L) {
            sb.appendLine(summaryFile.readText().take(1000))
        }
        return sb.toString()
    }
}
