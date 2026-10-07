package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
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
    private val failedApproaches = mutableListOf<FailedApproach>()

    fun recordFailedApproach(strategy: String, error: String, context: String) {
        failedApproaches.add(
            FailedApproach(
                id = UUID.randomUUID().toString(),
                strategy = strategy,
                error = error.take(300),
                context = context.take(300),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun getFailedApproaches(): List<FailedApproach> = failedApproaches.toList()

    fun storeObservation(
        resolver: WorkspacePathResolver,
        callId: String,
        stdout: String,
        stderr: String
    ): String {
        val obsDir = resolver.observationsDir
        obsDir.mkdirs()

        val obsFile = File(obsDir, "obs_${callId}.log")
        obsFile.writeText("=== STDOUT ===\n$stdout\n\n=== STDERR ===\n$stderr\n")
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
            appendLine("1. THE RUNTIME IS AUTHORITATIVE. Your claims are hypotheses until verified on disk via tools.")
            appendLine("2. LOGICAL FILESYSTEM:")
            appendLine("   - /workspace (primary task files)")
            appendLine("   - /process (scratch files, raw downloads, intermediate outputs)")
            appendLine("   - /artifacts (final deliverables: DOCX, XLSX, PDF, web applications)")
            appendLine("   - /tmp (temporary caches)")
            appendLine("3. STRUCTURED TOOL CALLS ONLY. Never output pseudo-syntax like <tool>run</tool>.")
            appendLine("4. VERIFICATION MANDATE: Tasks only complete when objective artifacts exist, are non-empty, and structurally valid.")
            appendLine("5. PYTHON SCRIPTS: Always written to /workspace/.aragon/runtime/scripts/, verified via SHA-256, and executed.")
            appendLine("6. CONTEXT TRANSFER: Large command and observation logs are stored on disk in /workspace/.aragon/observations/.")
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
        loopWarning: String? = null
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

        // 3. Compact state overview
        val stateSummary = buildString {
            appendLine("### AGENT STATE:")
            appendLine("- Status: ${task.status}")
            appendLine("- Iteration: ${task.iteration}")
            appendLine("- Current Objective: ${task.currentObjective.ifBlank { task.title }}")

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
                appendLine("\n### DISCOVERED ARTIFACTS:")
                artifacts.forEach { art ->
                    appendLine("- ${art.filename} (${art.size}B, Stage: ${art.stage}, Valid: ${art.valid}) -> ${art.logicalPath}")
                }
            }

            if (failedApproaches.isNotEmpty()) {
                appendLine("\n### FAILED APPROACH MEMORY (DO NOT REPEAT):")
                failedApproaches.takeLast(3).forEach {
                    appendLine("- Strategy '${it.strategy}' failed: ${it.error}")
                }
            }

            if (!loopWarning.isNullOrBlank()) {
                appendLine("\n⚠️ RUNTIME DIRECTIVE: $loopWarning")
            }
        }

        messages.add(
            LlmMessage(
                role = LlmRole.ASSISTANT,
                content = stateSummary
            )
        )

        // 4. Recent tool executions with compressed outputs
        val resultsToInclude = recentToolResults.takeLast(maxRecentResultsInline)
        for ((toolName, result) in resultsToInclude) {
            val content = buildString {
                appendLine("Tool: $toolName")
                appendLine("ExitCode: ${result.exitCode}, Success: ${result.success}")
                if (result.stdout.isNotBlank()) {
                    appendLine("STDOUT (truncated):\n${result.stdout.take(maxInlineOutputLength)}")
                }
                if (result.stderr.isNotBlank()) {
                    appendLine("STDERR:\n${result.stderr.take(maxInlineOutputLength)}")
                }
                if (result.artifacts.isNotEmpty()) {
                    appendLine("Artifacts produced: ${result.artifacts.joinToString()}")
                }
            }

            messages.add(
                LlmMessage(
                    role = LlmRole.TOOL,
                    name = toolName,
                    toolCallId = result.callId,
                    content = content
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
            failedApproaches.forEach {
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
