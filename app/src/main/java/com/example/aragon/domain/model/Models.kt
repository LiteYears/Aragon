package com.example.aragon.domain.model

import org.json.JSONObject

enum class TaskStatus {
    CREATED,
    INITIALIZING,
    PROVISIONING,
    PLANNING,
    AWAITING_PLAN_APPROVAL,
    READY,
    EXECUTING,
    OBSERVING,
    VERIFYING,
    REPLANNING,
    AWAITING_APPROVAL,
    WAITING_FOR_USER,
    PAUSED,
    COMPLETING,
    COMPLETED,
    FAILED,
    BLOCKED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == BLOCKED

    val isActive: Boolean
        get() = this == INITIALIZING || this == PROVISIONING || this == PLANNING ||
                this == AWAITING_PLAN_APPROVAL || this == READY || this == EXECUTING ||
                this == OBSERVING || this == VERIFYING || this == REPLANNING ||
                this == AWAITING_APPROVAL || this == COMPLETING
}

enum class AgentMode {
    AGENT, // Autonomous tool execution
    PLAN,  // Generates plan, requests user approval before execution
    CHAT   // Interactive dialogue
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    BLOCKED,
    SKIPPED;

    companion object {
        // Compatibility alias for existing references
        val RUNNING = IN_PROGRESS
    }
}

enum class ArtifactStage {
    PROCESS,
    PRODUCT
}

data class PlanStep(
    val id: String,
    val taskId: String,
    val stepNumber: Int,
    val title: String,
    val description: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val dependencies: List<String> = emptyList(),
    val attemptCount: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val verificationStatus: String? = null,
    val toolName: String? = null,
    val verified: Boolean = false,
    val resultSummary: String? = null
)

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
    val finalSummary: String? = null,
    val metrics: TaskMetrics = TaskMetrics()
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
    val logicalPath: String, // e.g. /workspace/artifacts/report.docx
    val filename: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val stage: ArtifactStage = ArtifactStage.PRODUCT,
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
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = taskId,
    val iterationId: Int = 1
) {
    val id: String get() = callId
}

data class ToolResult(
    val callId: String,
    val taskId: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val workingDirectory: String = "/workspace",
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
    val terminationReason: String? = null,
    val artifacts: List<String> = emptyList(),
    val errorType: String? = null,
    val errorMessage: String? = null
) {
    val toolCallId: String get() = callId
}

enum class ApprovalType {
    DANGEROUS_COMMAND,
    OUTSIDE_WORKSPACE_ACCESS,
    SECRET_INJECTION,
    EXTERNAL_WRITE,
    DEPLOYMENT,
    DESTRUCTIVE_FILE_OPERATION,
    PARALLEL_RESEARCH
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED
}

data class ApprovalRequest(
    val id: String,
    val taskId: String,
    val type: ApprovalType,
    val description: String,
    val risk: String,
    val proposedAction: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

enum class WorkerStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class TaskMetrics(
    val durationMs: Long = 0L,
    val model: String = "",
    val tokensUsed: Int = 0,
    val toolCallsCount: Int = 0,
    val successfulToolCalls: Int = 0,
    val failedToolCalls: Int = 0,
    val commandsExecuted: Int = 0,
    val browserActions: Int = 0,
    val filesCreated: Int = 0,
    val artifactsProduced: Int = 0,
    val verificationFailures: Int = 0,
    val replansCount: Int = 0,
    val workersCount: Int = 0,
    val networkRequests: Int = 0
)

data class WorkerTask(
    val workerId: String,
    val parentTaskId: String,
    val objective: String,
    val workspacePath: String,
    val status: WorkerStatus = WorkerStatus.IDLE,
    val result: String? = null,
    val errors: List<String> = emptyList(),
    val artifacts: List<String> = emptyList(),
    val metrics: TaskMetrics = TaskMetrics()
)

data class FailedApproach(
    val id: String,
    val strategy: String,
    val error: String,
    val context: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class TimelineEventType {
    PLANNING,
    REASONING,
    TOOL_EXECUTION,
    OBSERVATION,
    VERIFICATION,
    ARTIFACT_GENERATION,
    STATUS_CHANGE,
    APPROVAL_REQUESTED,
    APPROVAL_GRANTED,
    APPROVAL_DENIED,
    CHECKPOINT_SAVED,
    WORKER_STARTED,
    WORKER_COMPLETED,
    REPLAN,
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
    val structuredOutput: Boolean = true,
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

data class BrowserSessionState(
    val currentUrl: String = "about:blank",
    val title: String = "",
    val tabs: List<String> = listOf("tab_1"),
    val viewport: String = "1280x800",
    val cookiesCount: Int = 0,
    val capabilityState: String = "HEADLESS_TEXT_EXTRACTOR",
    val lastScreenshotPath: String? = null
)

enum class ExecutionBackend {
    LOCAL_COMPUTER, // Built-in POSIX / Android userspace execution
    OPEN_SANDBOX    // Isolated OpenSandbox microVM / Docker container
}

