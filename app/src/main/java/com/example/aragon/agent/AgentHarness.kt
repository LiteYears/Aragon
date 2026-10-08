package com.example.aragon.agent

import com.example.aragon.artifacts.ArtifactManager
import com.example.aragon.computer.UbuntuManager
import com.example.aragon.computer.WorkspaceManager
import com.example.aragon.data.local.PlanStepDao
import com.example.aragon.data.local.ProjectDao
import com.example.aragon.data.local.TaskDao
import com.example.aragon.data.local.TimelineEventDao
import com.example.aragon.data.local.ToolExecutionDao
import com.example.aragon.data.preferences.PreferencesManager
import com.example.aragon.llm.LlmProvider
import com.example.aragon.tools.ToolDispatcher
import com.example.aragon.tools.ToolExecutor
import com.example.aragon.tools.ToolRegistry

/**
 * V2 AgentHarness — delegates lifecycle, planning, coordinator, and ReAct loop
 * to the robust SessionOrchestrator.
 */
class AgentHarness(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val planStepDao: PlanStepDao,
    private val toolExecutionDao: ToolExecutionDao,
    private val timelineEventDao: TimelineEventDao,
    private val artifactManager: ArtifactManager,
    private val workspaceManager: WorkspaceManager,
    private val ubuntuManager: UbuntuManager,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val llmProvider: LlmProvider,
    private val preferencesManager: PreferencesManager,
    private val openSandboxManager: com.example.aragon.opensandbox.OpenSandboxManager? = null,
    val approvalManager: ApprovalManager = ApprovalManager(),
    val toolDispatcher: ToolDispatcher = ToolDispatcher(toolExecutor, approvalManager, toolRegistry, toolExecutionDao),
    val orchestrator: SessionOrchestrator = SessionOrchestrator(
        taskDao = taskDao,
        projectDao = projectDao,
        planStepDao = planStepDao,
        toolExecutionDao = toolExecutionDao,
        timelineEventDao = timelineEventDao,
        artifactManager = artifactManager,
        workspaceManager = workspaceManager,
        ubuntuManager = ubuntuManager,
        toolRegistry = toolRegistry,
        toolDispatcher = toolDispatcher,
        llmProvider = llmProvider,
        preferencesManager = preferencesManager,
        approvalManager = approvalManager,
        openSandboxManager = openSandboxManager
    )
) {
    fun startTask(taskId: String) {
        orchestrator.startSession(taskId)
    }

    fun pauseTask(taskId: String) {
        orchestrator.pauseSession(taskId)
    }

    fun cancelTask(taskId: String) {
        orchestrator.cancelSession(taskId)
    }

    fun approvePlanAndExecute(taskId: String) {
        orchestrator.approvePlanAndExecute(taskId)
    }

    fun respondToApproval(requestId: String, approved: Boolean) {
        orchestrator.handleApproval(requestId, approved)
    }
}
