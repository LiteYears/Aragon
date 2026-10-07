package com.example.aragon.tools

import com.example.aragon.agent.ApprovalManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.AutonomyLevel
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ToolDispatcher(
    private val toolExecutor: ToolExecutor,
    private val approvalManager: ApprovalManager,
    private val toolRegistry: ToolRegistry
) {

    suspend fun dispatch(
        toolCall: ToolCall,
        resolver: WorkspacePathResolver,
        autonomyLevel: AutonomyLevel = AutonomyLevel.FULL
    ): ToolResult = withContext(Dispatchers.IO) {
        val args = runCatching { JSONObject(toolCall.argumentsJson) }.getOrDefault(JSONObject())

        // 1. Validate tool exists
        val toolDef = toolRegistry.getTool(toolCall.toolName)
        if (toolDef == null && toolCall.toolName !in listOf("text_editor", "browser_action", "verify_objective")) {
            return@withContext ToolResult(
                callId = toolCall.id,
                taskId = toolCall.taskId,
                success = false,
                exitCode = 1,
                stdout = "",
                stderr = "Tool '${toolCall.toolName}' is not registered in Aragon ToolDispatcher",
                durationMs = 0L,
                workingDirectory = "/workspace",
                errorType = "UNREGISTERED_TOOL"
            )
        }

        // 2. Security & Human Approval Check
        if (toolCall.toolName == "run_command") {
            val command = args.optString("command", "")
            val workDir = args.optString("workingDirectory", "/workspace")
            val requiredApproval = approvalManager.requiresApproval(command, workDir, autonomyLevel)

            if (requiredApproval != null) {
                val req = approvalManager.createRequest(
                    taskId = toolCall.taskId,
                    type = requiredApproval,
                    description = "Execution of sensitive command requested: $command",
                    risk = "Requires manual confirmation under policy level $autonomyLevel",
                    proposedAction = command
                )
                return@withContext ToolResult(
                    callId = toolCall.id,
                    taskId = toolCall.taskId,
                    success = false,
                    exitCode = 126,
                    stdout = "",
                    stderr = "Operation requires human approval (Request ID: ${req.id}). Status: Awaiting permission.",
                    durationMs = 0L,
                    workingDirectory = workDir,
                    cancelled = true,
                    terminationReason = "AWAITING_APPROVAL"
                )
            }
        }

        // 3. Dispatch to ToolExecutor
        toolExecutor.executeTool(
            callId = toolCall.id,
            taskId = toolCall.taskId,
            toolName = toolCall.toolName,
            argumentsJson = toolCall.argumentsJson,
            resolver = resolver
        )
    }
}
