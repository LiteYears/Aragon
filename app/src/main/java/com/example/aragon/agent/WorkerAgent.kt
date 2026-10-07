package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.TaskMetrics
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.domain.model.WorkerStatus
import com.example.aragon.domain.model.WorkerTask
import com.example.aragon.tools.ToolDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class WorkerAgent(
    val workerId: String,
    val parentTaskId: String,
    val objective: String,
    val isolatedWorkspacePath: String,
    private val toolDispatcher: ToolDispatcher
) {
    private var status: WorkerStatus = WorkerStatus.IDLE
    private var result: String? = null
    private val errors = mutableListOf<String>()
    private val artifacts = mutableListOf<String>()
    private var metrics = TaskMetrics()

    fun getWorkerTask(): WorkerTask = WorkerTask(
        workerId = workerId,
        parentTaskId = parentTaskId,
        objective = objective,
        workspacePath = isolatedWorkspacePath,
        status = status,
        result = result,
        errors = errors.toList(),
        artifacts = artifacts.toList(),
        metrics = metrics
    )

    suspend fun execute(resolver: WorkspacePathResolver): WorkerTask = withContext(Dispatchers.IO) {
        status = WorkerStatus.RUNNING
        val workerDir = File(resolver.resolve(isolatedWorkspacePath).absolutePath).apply { mkdirs() }

        try {
            // Worker executes targeted research / sub-operation
            val call = ToolCall(
                callId = "w_${workerId}_1",
                taskId = parentTaskId,
                toolName = "web_search",
                argumentsJson = JSONObject().put("query", objective).put("maxResults", 3).toString()
            )

            val toolResult = toolDispatcher.dispatch(call, resolver)
            metrics = metrics.copy(
                toolCallsCount = metrics.toolCallsCount + 1,
                successfulToolCalls = if (toolResult.success) metrics.successfulToolCalls + 1 else metrics.successfulToolCalls
            )

            // Save sub-findings to worker's isolated directory
            val findingFile = File(workerDir, "findings.md")
            val findingsText = if (toolResult.stdout.isNotBlank()) toolResult.stdout else "Searched for: $objective"
            findingFile.writeText(findingsText)
            artifacts.add(resolver.toLogicalPath(findingFile))

            result = "Completed sub-objective: $objective\nSummary: ${findingsText.take(400)}"
            status = WorkerStatus.COMPLETED
        } catch (e: Exception) {
            status = WorkerStatus.FAILED
            errors.add(e.message ?: "Unknown worker failure")
        }

        getWorkerTask()
    }
}
