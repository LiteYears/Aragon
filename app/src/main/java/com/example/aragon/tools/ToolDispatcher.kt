package com.example.aragon.tools

import com.example.aragon.agent.ApprovalManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.data.local.ToolExecutionDao
import com.example.aragon.domain.model.AutonomyLevel
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.domain.model.ToolExecutionStatus
import com.example.aragon.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ToolDispatcher(
    private val toolExecutor: ToolExecutor,
    private val approvalManager: ApprovalManager,
    private val toolRegistry: ToolRegistry,
    private val toolExecutionDao: ToolExecutionDao? = null
) {
    private val activeToolCallIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun dispatch(
        toolCall: ToolCall,
        resolver: WorkspacePathResolver,
        autonomyLevel: AutonomyLevel = AutonomyLevel.FULL,
        isPreApproved: Boolean = false,
        onStatusChange: ((ToolExecutionStatus) -> Unit)? = null
    ): ToolResult = withContext(Dispatchers.IO) {
        val dispatchedAt = System.currentTimeMillis()
        onStatusChange?.invoke(ToolExecutionStatus.DISPATCHED)

        // 0. Check if this tool call has already been executed to prevent duplicates
        if (toolExecutionDao != null) {
            val prior = toolExecutionDao.getExecutionByCallId(toolCall.id)
            if (prior != null && prior.taskId == toolCall.taskId && prior.toolName == toolCall.toolName &&
                (prior.status == ToolExecutionStatus.SUCCEEDED.name || prior.status == ToolExecutionStatus.FAILED.name || prior.status == ToolExecutionStatus.CANCELLED.name)) {
                val res = prior.toDomainResult()
                onStatusChange?.invoke(res.status)
                return@withContext res
            }
        }

        // Prevent in-flight concurrent execution of the same call ID for this task
        val inFlightKey = "${toolCall.taskId}_${toolCall.id}"
        if (!activeToolCallIds.add(inFlightKey)) {
            val res = ToolResult(
                callId = toolCall.id,
                taskId = toolCall.taskId,
                toolName = toolCall.toolName,
                argumentsJson = toolCall.argumentsJson,
                success = false,
                exitCode = 1,
                stdout = "",
                stderr = "Tool call '${toolCall.id}' is already in-flight. Duplicate execution rejected.",
                durationMs = 0L,
                workingDirectory = "/workspace",
                errorType = "CONCURRENT_DUPLICATE_CALL",
                errorMessage = "Tool call is already actively executing",
                startedAt = dispatchedAt,
                completedAt = System.currentTimeMillis(),
                status = ToolExecutionStatus.FAILED
            )
            onStatusChange?.invoke(ToolExecutionStatus.FAILED)
            return@withContext res
        }

        try {
            // 1. Validate argument JSON formatting strictly
            val args = try {
                JSONObject(toolCall.argumentsJson)
            } catch (e: Exception) {
                val res = ToolResult(
                    callId = toolCall.id,
                    taskId = toolCall.taskId,
                    toolName = toolCall.toolName,
                    argumentsJson = toolCall.argumentsJson,
                    success = false,
                    exitCode = 1,
                    stdout = "",
                    stderr = "Malformed JSON arguments: ${e.message}",
                    durationMs = 0L,
                    workingDirectory = "/workspace",
                    errorType = "INVALID_JSON_ARGUMENTS",
                    errorMessage = "Malformed JSON arguments: ${e.message}",
                    startedAt = dispatchedAt,
                    completedAt = System.currentTimeMillis(),
                    status = ToolExecutionStatus.FAILED
                )
                onStatusChange?.invoke(ToolExecutionStatus.FAILED)
                return@withContext res
            }

            // 2. Validate tool registration
            val toolDef = toolRegistry.getTool(toolCall.toolName)
            if (toolDef == null) {
                val res = ToolResult(
                    callId = toolCall.id,
                    taskId = toolCall.taskId,
                    toolName = toolCall.toolName,
                    argumentsJson = toolCall.argumentsJson,
                    success = false,
                    exitCode = 1,
                    stdout = "",
                    stderr = "Unknown tool: '${toolCall.toolName}' is not registered in Aragon ToolRegistry",
                    durationMs = 0L,
                    workingDirectory = "/workspace",
                    errorType = "UNKNOWN_TOOL",
                    errorMessage = "Tool '${toolCall.toolName}' is not registered",
                    startedAt = dispatchedAt,
                    completedAt = System.currentTimeMillis(),
                    status = ToolExecutionStatus.FAILED
                )
                onStatusChange?.invoke(ToolExecutionStatus.FAILED)
                return@withContext res
            }

            // 3. Validate required arguments
            for (param in toolDef.parameters) {
                if (param.required && (!args.has(param.name) || args.isNull(param.name))) {
                    val res = ToolResult(
                        callId = toolCall.id,
                        taskId = toolCall.taskId,
                        toolName = toolCall.toolName,
                        argumentsJson = toolCall.argumentsJson,
                        success = false,
                        exitCode = 1,
                        stdout = "",
                        stderr = "Missing required argument '${param.name}' for tool '${toolCall.toolName}'",
                        durationMs = 0L,
                        workingDirectory = "/workspace",
                        errorType = "MISSING_REQUIRED_ARGUMENT",
                        errorMessage = "Missing required argument '${param.name}'",
                        startedAt = dispatchedAt,
                        completedAt = System.currentTimeMillis(),
                        status = ToolExecutionStatus.FAILED
                    )
                    onStatusChange?.invoke(ToolExecutionStatus.FAILED)
                    return@withContext res
                }
            }

            // 4. Security & Human Approval Check
            if (approvalManager.isDeniedForCall(toolCall.id)) {
                val res = ToolResult(
                    callId = toolCall.id,
                    taskId = toolCall.taskId,
                    toolName = toolCall.toolName,
                    argumentsJson = toolCall.argumentsJson,
                    success = false,
                    exitCode = 126,
                    stdout = "",
                    stderr = "Operation was denied by user.",
                    durationMs = 0L,
                    workingDirectory = args.optString("workingDirectory", "/workspace"),
                    cancelled = true,
                    terminationReason = "APPROVAL_DENIED",
                    errorMessage = "Execution denied by user",
                    startedAt = dispatchedAt,
                    completedAt = System.currentTimeMillis(),
                    status = ToolExecutionStatus.CANCELLED
                )
                onStatusChange?.invoke(ToolExecutionStatus.CANCELLED)
                return@withContext res
            }

            val isCallApproved = isPreApproved || approvalManager.isApprovedForCall(toolCall.id)
            if (isCallApproved) {
                approvalManager.consumeApprovalForCall(toolCall.id)
                onStatusChange?.invoke(ToolExecutionStatus.APPROVED)
            } else if (toolCall.toolName == "run_command") {
                val command = args.optString("command", "")
                val workDir = args.optString("workingDirectory", "/workspace")
                val requiredApproval = approvalManager.requiresApproval(command, workDir, autonomyLevel)

                if (requiredApproval != null) {
                    val req = approvalManager.createRequest(
                        taskId = toolCall.taskId,
                        type = requiredApproval,
                        description = "Execution of sensitive command requested: $command",
                        risk = "Requires manual confirmation under policy level $autonomyLevel",
                        proposedAction = command,
                        toolCallId = toolCall.id,
                        toolName = toolCall.toolName,
                        argumentsJson = toolCall.argumentsJson
                    )
                    val res = ToolResult(
                        callId = toolCall.id,
                        taskId = toolCall.taskId,
                        toolName = toolCall.toolName,
                        argumentsJson = toolCall.argumentsJson,
                        success = false,
                        exitCode = 126,
                        stdout = "",
                        stderr = "Operation requires human approval (Request ID: ${req.id}). Status: Awaiting permission.",
                        durationMs = 0L,
                        workingDirectory = workDir,
                        cancelled = true,
                        terminationReason = "AWAITING_APPROVAL",
                        startedAt = dispatchedAt,
                        completedAt = System.currentTimeMillis(),
                        status = ToolExecutionStatus.AWAITING_APPROVAL
                    )
                    onStatusChange?.invoke(ToolExecutionStatus.AWAITING_APPROVAL)
                    return@withContext res
                }
            }

            // 5. Dispatch to ToolExecutor with running notification
            onStatusChange?.invoke(ToolExecutionStatus.EXECUTING)
            val startedAt = System.currentTimeMillis()
            val result = toolExecutor.executeTool(
                callId = toolCall.id,
                taskId = toolCall.taskId,
                toolName = toolCall.toolName,
                argumentsJson = toolCall.argumentsJson,
                resolver = resolver
            )
            val completedAt = System.currentTimeMillis()

            val finalResult = result.copy(
                toolName = toolCall.toolName,
                argumentsJson = toolCall.argumentsJson,
                environment = if (toolCall.toolName.startsWith("sandbox_")) "OPEN_SANDBOX" else "LOCAL_COMPUTER",
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = if (result.durationMs > 0) result.durationMs else (completedAt - startedAt),
                status = if (result.cancelled) ToolExecutionStatus.CANCELLED else if (result.success) ToolExecutionStatus.SUCCEEDED else ToolExecutionStatus.FAILED
            )

            onStatusChange?.invoke(finalResult.status)
            finalResult
        } finally {
            activeToolCallIds.remove("${toolCall.taskId}_${toolCall.id}")
        }
    }
}
