package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.Project
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.ToolResult
import com.example.aragon.llm.LlmMessage
import com.example.aragon.llm.LlmRole
import java.io.File

class ContextManager {

    fun buildSystemPrompt(
        project: Project?,
        resolver: WorkspacePathResolver
    ): String {
        return buildString {
            appendLine("You are ARAGON, a personal, phone-first autonomous computer agent operating on an Android device with an integrated local Ubuntu Linux userspace.")
            appendLine()
            appendLine("### CORE ARCHITECTURAL PRINCIPLES:")
            appendLine("1. THE RUNTIME IS THE SOURCE OF TRUTH. You are an operator of a real computer. Model claims (e.g., 'I created the file') are NEVER facts until observed via tools.")
            appendLine("2. LOGICAL ENVIRONMENT: You operate exclusively within logical paths:")
            appendLine("   - /workspace (primary task workspace)")
            appendLine("   - /workspace/.aragon/ (agent memory, state, runtime)")
            appendLine("   - /artifacts (generated outputs)")
            appendLine("   Never use Android private paths like /data/data/...")
            appendLine("3. STRUCTURED TOOL CALLS ONLY: You MUST invoke structured tools. Model prose is NEVER executed directly.")
            appendLine("4. VERIFICATION MANDATE: A task is only complete when the user's objective is verified on disk.")
            appendLine("5. PERSISTENT FILESYSTEM MEMORY: Durable data belongs in files inside /workspace, not crammed endlessly into conversation context.")
            appendLine("6. PYTHON INTEGRATION: Python code is written to /workspace/.aragon/runtime/<callId>.py, verified via SHA-256 hash, and executed.")
            appendLine()

            if (project != null && project.instructions.isNotBlank()) {
                appendLine("### PROJECT INSTRUCTIONS (${project.name}):")
                appendLine(project.instructions)
                appendLine()
            }

            // Read externalized memory notes if present
            val notesFile = File(resolver.memoryDir, "notes.md")
            if (notesFile.exists() && notesFile.length() > 0) {
                appendLine("### DURABLE WORKING MEMORY (notes.md):")
                appendLine(notesFile.readText().take(2000))
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

        // 2. Initial User Request
        messages.add(
            LlmMessage(
                role = LlmRole.USER,
                content = "Task Objective: ${task.originalRequest}"
            )
        )

        // 3. Current State & Plan Context
        val stateSummary = buildString {
            appendLine("### CURRENT AGENT STATE:")
            appendLine("- Status: ${task.status}")
            appendLine("- Iteration: ${task.iteration}")
            appendLine("- Current Objective: ${task.currentObjective.ifBlank { task.title }}")

            if (planSteps.isNotEmpty()) {
                appendLine("\n### ACTIVE PLAN:")
                planSteps.forEach { step ->
                    val mark = when (step.status) {
                        com.example.aragon.domain.model.StepStatus.COMPLETED -> "[✓]"
                        com.example.aragon.domain.model.StepStatus.RUNNING -> "[▶]"
                        com.example.aragon.domain.model.StepStatus.FAILED -> "[✗]"
                        else -> "[ ]"
                    }
                    appendLine("$mark Step ${step.stepNumber}: ${step.title}")
                }
            }

            if (artifacts.isNotEmpty()) {
                appendLine("\n### DISCOVERED ARTIFACTS:")
                artifacts.forEach { art ->
                    appendLine("- ${art.filename} (${art.size} bytes, Valid: ${art.valid}) -> ${art.logicalPath}")
                }
            }

            if (!loopWarning.isNullOrBlank()) {
                appendLine("\n⚠️ RECOVERY DIRECTIVE: $loopWarning")
                appendLine("Gather new observations or adjust approach rather than repeating.")
            }
        }

        messages.add(
            LlmMessage(
                role = LlmRole.ASSISTANT,
                content = stateSummary
            )
        )

        // 4. Recent Tool Executions & Observations
        for ((toolName, result) in recentToolResults.takeLast(5)) {
            val toolContent = buildString {
                appendLine("Tool executed: $toolName")
                appendLine("Exit code: ${result.exitCode}")
                appendLine("Success: ${result.success}")
                if (result.stdout.isNotBlank()) {
                    appendLine("STDOUT:\n${result.stdout.take(1500)}")
                }
                if (result.stderr.isNotBlank()) {
                    appendLine("STDERR:\n${result.stderr.take(1000)}")
                }
            }
            messages.add(
                LlmMessage(
                    role = LlmRole.TOOL,
                    name = toolName,
                    toolCallId = result.callId,
                    content = toolContent
                )
            )
        }

        return messages
    }
}
