package com.example.aragon.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.Project
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.domain.model.TimelineEvent
import com.example.aragon.domain.model.TimelineEventType
import com.example.aragon.domain.model.ToolResult

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val title: String,
    val originalRequest: String,
    val status: TaskStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val workspacePath: String,
    val selectedModel: String,
    val mode: AgentMode,
    val currentObjective: String,
    val completedObjectives: List<String>,
    val pendingObjectives: List<String>,
    val iteration: Int,
    val lastProgressAt: Long,
    val failureCount: Int,
    val lastError: String?,
    val finalSummary: String?
) {
    fun toDomain(): Task = Task(
        id = id,
        projectId = projectId,
        title = title,
        originalRequest = originalRequest,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        workspacePath = workspacePath,
        selectedModel = selectedModel,
        mode = mode,
        currentObjective = currentObjective,
        completedObjectives = completedObjectives,
        pendingObjectives = pendingObjectives,
        iteration = iteration,
        lastProgressAt = lastProgressAt,
        failureCount = failureCount,
        lastError = lastError,
        finalSummary = finalSummary
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id,
            projectId = task.projectId,
            title = task.title,
            originalRequest = task.originalRequest,
            status = task.status,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            workspacePath = task.workspacePath,
            selectedModel = task.selectedModel,
            mode = task.mode,
            currentObjective = task.currentObjective,
            completedObjectives = task.completedObjectives,
            pendingObjectives = task.pendingObjectives,
            iteration = task.iteration,
            lastProgressAt = task.lastProgressAt,
            failureCount = task.failureCount,
            lastError = task.lastError,
            finalSummary = task.finalSummary
        )
    }
}

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val workspacePath: String,
    val preferredModel: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): Project = Project(
        id = id,
        name = name,
        description = description,
        instructions = instructions,
        workspacePath = workspacePath,
        preferredModel = preferredModel,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id,
            name = project.name,
            description = project.description,
            instructions = project.instructions,
            workspacePath = project.workspacePath,
            preferredModel = project.preferredModel,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt
        )
    }
}

@Entity(tableName = "plan_steps")
data class PlanStepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val stepNumber: Int,
    val title: String,
    val description: String,
    val status: StepStatus,
    val toolName: String?,
    val verified: Boolean,
    val resultSummary: String?,
    val dependencies: List<String> = emptyList(),
    val attemptCount: Int = 0,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val verificationStatus: String? = null,
    val phase: String = "",
    val subtasks: List<String> = emptyList(),
    val activeSubtaskIndex: Int = 0,
    val nextIntent: String = ""
) {
    fun toDomain(): PlanStep = PlanStep(
        id = id,
        taskId = taskId,
        stepNumber = stepNumber,
        title = title,
        description = description,
        status = status,
        dependencies = dependencies,
        attemptCount = attemptCount,
        startedAt = startedAt,
        completedAt = completedAt,
        verificationStatus = verificationStatus,
        toolName = toolName,
        verified = verified,
        resultSummary = resultSummary,
        phase = phase,
        subtasks = subtasks,
        activeSubtaskIndex = activeSubtaskIndex,
        nextIntent = nextIntent
    )

    companion object {
        fun fromDomain(step: PlanStep): PlanStepEntity = PlanStepEntity(
            id = step.id,
            taskId = step.taskId,
            stepNumber = step.stepNumber,
            title = step.title,
            description = step.description,
            status = step.status,
            dependencies = step.dependencies,
            attemptCount = step.attemptCount,
            startedAt = step.startedAt,
            completedAt = step.completedAt,
            verificationStatus = step.verificationStatus,
            toolName = step.toolName,
            verified = step.verified,
            resultSummary = step.resultSummary,
            phase = step.phase,
            subtasks = step.subtasks,
            activeSubtaskIndex = step.activeSubtaskIndex,
            nextIntent = step.nextIntent
        )
    }
}

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val logicalPath: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val valid: Boolean,
    val verified: Boolean,
    val previewable: Boolean,
    val shareable: Boolean,
    val downloadable: Boolean,
    val validationDetails: String,
    val stage: com.example.aragon.domain.model.ArtifactStage = com.example.aragon.domain.model.ArtifactStage.PRODUCT
) {
    fun toDomain(): Artifact = Artifact(
        id = id,
        taskId = taskId,
        logicalPath = logicalPath,
        filename = filename,
        mimeType = mimeType,
        size = size,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        stage = stage,
        valid = valid,
        verified = verified,
        previewable = previewable,
        shareable = shareable,
        downloadable = downloadable,
        validationDetails = validationDetails
    )

    companion object {
        fun fromDomain(artifact: Artifact): ArtifactEntity = ArtifactEntity(
            id = artifact.id,
            taskId = artifact.taskId,
            logicalPath = artifact.logicalPath,
            filename = artifact.filename,
            mimeType = artifact.mimeType,
            size = artifact.size,
            createdAt = artifact.createdAt,
            modifiedAt = artifact.modifiedAt,
            stage = artifact.stage,
            valid = artifact.valid,
            verified = artifact.verified,
            previewable = artifact.previewable,
            shareable = artifact.shareable,
            downloadable = artifact.downloadable,
            validationDetails = artifact.validationDetails
        )
    }
}

@Entity(tableName = "tool_executions")
data class ToolExecutionEntity(
    @PrimaryKey val callId: String,
    val taskId: String,
    val toolName: String,
    val argumentsJson: String,
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val workingDirectory: String,
    val errorType: String?,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDomainResult(): ToolResult = ToolResult(
        callId = callId,
        taskId = taskId,
        success = success,
        exitCode = exitCode,
        stdout = stdout,
        stderr = stderr,
        durationMs = durationMs,
        workingDirectory = workingDirectory,
        errorType = errorType,
        errorMessage = errorMessage
    )
}

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val timestamp: Long,
    val type: TimelineEventType,
    val title: String,
    val details: String,
    val toolCallId: String?,
    val durationMs: Long?,
    val isSuccess: Boolean?
) {
    fun toDomain(): TimelineEvent = TimelineEvent(
        id = id,
        taskId = taskId,
        timestamp = timestamp,
        type = type,
        title = title,
        details = details,
        toolCallId = toolCallId,
        durationMs = durationMs,
        isSuccess = isSuccess
    )

    companion object {
        fun fromDomain(event: TimelineEvent): TimelineEventEntity = TimelineEventEntity(
            id = event.id,
            taskId = event.taskId,
            timestamp = event.timestamp,
            type = event.type,
            title = event.title,
            details = event.details,
            toolCallId = event.toolCallId,
            durationMs = event.durationMs,
            isSuccess = event.isSuccess
        )
    }
}
