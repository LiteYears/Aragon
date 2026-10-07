package com.example.aragon.domain.model

enum class TaskStatus {
    CREATED,
    PLANNING,
    READY,
    EXECUTING,
    OBSERVING,
    VERIFYING,
    REPLANNING,
    WAITING_FOR_USER,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED

    val isActive: Boolean
        get() = this == PLANNING || this == READY || this == EXECUTING ||
                this == OBSERVING || this == VERIFYING || this == REPLANNING
}

enum class AgentMode {
    AGENT, // Autonomous tool execution
    PLAN,  // Generates plan, requests user approval before execution
    CHAT   // Interactive dialogue
}

enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class Task(
    val id: String,
    val projectId: String? = null,
    val title: String,
    val originalRequest: String,
    val status: TaskStatus = TaskStatus.CREATED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val workspacePath: String = "/workspace",
    val selectedModel: String = "meta/llama-3.3-70b-instruct",
    val mode: AgentMode = AgentMode.AGENT,
    val currentObjective: String = "",
    val completedObjectives: List<String> = emptyList(),
    val pendingObjectives: List<String> = emptyList(),
    val iteration: Int = 0,
    val lastProgressAt: Long = System.currentTimeMillis(),
    val failureCount: Int = 0,
    val lastError: String? = null,
    val finalSummary: String? = null
)

data class PlanStep(
    val id: String,
    val taskId: String,
    val stepNumber: Int,
    val title: String,
    val description: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val toolName: String? = null,
    val verified: Boolean = false,
    val resultSummary: String? = null
)

data class Project(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String = "",
    val workspacePath: String = "/workspace",
    val preferredModel: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Artifact(
    val id: String,
    val taskId: String,
    val logicalPath: String, // e.g. /workspace/report.docx
    val filename: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val valid: Boolean = false,
    val verified: Boolean = false,
    val previewable: Boolean = true,
    val shareable: Boolean = true,
    val downloadable: Boolean = true,
    val validationDetails: String = ""
)

data class ToolCall(
    val callId: String,
    val taskId: String,
    val toolName: String,
    val argumentsJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ToolResult(
    val callId: String,
    val taskId: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val workingDirectory: String,
    val errorType: String? = null,
    val errorMessage: String? = null
)

enum class TimelineEventType {
    PLANNING,
    REASONING,
    TOOL_EXECUTION,
    OBSERVATION,
    VERIFICATION,
    ARTIFACT_GENERATION,
    STATUS_CHANGE,
    ERROR
}

data class TimelineEvent(
    val id: String,
    val taskId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: TimelineEventType,
    val title: String,
    val details: String = "",
    val toolCallId: String? = null,
    val durationMs: Long? = null,
    val isSuccess: Boolean? = null
)

data class ModelCapabilities(
    val toolCalling: Boolean = true,
    val vision: Boolean = false,
    val reasoning: Boolean = false,
    val streaming: Boolean = true,
    val maxContextTokens: Long? = 131072L
)

data class ModelInfo(
    val id: String,
    val name: String,
    val publisher: String,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val description: String = ""
)

enum class AutonomyLevel {
    SAFE,       // Read only and safe ops
    NORMAL,     // Standard file ops, python, safe commands
    FULL        // Autonomous execution with replanning
}
