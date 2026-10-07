package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.WorkerTask
import com.example.aragon.tools.ToolDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File

data class CoordinatedExecutionPlan(
    val requiresParallelWorkers: Boolean,
    val subObjectives: List<String> = emptyList(),
    val rationale: String = ""
)

data class CoordinatorResult(
    val reducedSummary: String,
    val workerTasks: List<WorkerTask>,
    val artifactsProduced: List<String>
)

class CoordinatorAgent(
    private val toolDispatcher: ToolDispatcher,
    private val maxConcurrency: Int = 3
) {

    fun evaluateTaskDecomposition(task: Task): CoordinatedExecutionPlan {
        val req = task.originalRequest.lowercase()
        val isParallelCandidate = req.contains("compare") || req.contains("research") ||
                req.contains("competitors") || req.contains("benchmark") ||
                req.contains("multiple sources") || req.contains("in-depth analysis")

        if (!isParallelCandidate) {
            return CoordinatedExecutionPlan(
                requiresParallelWorkers = false,
                rationale = "Task is sequential or single-objective; running single-agent harness."
            )
        }

        // Generate 2-3 focused sub-objectives
        val subObjectives = when {
            req.contains("competitor") || req.contains("compare") -> listOf(
                "Gather market landscape and leader feature matrix",
                "Analyze technical differentiators and architectural trade-offs",
                "Compile pricing and user feedback benchmarks"
            )
            req.contains("research") -> listOf(
                "Collect primary literature and verified documentation",
                "Extract quantitative metrics and performance comparisons"
            )
            else -> listOf(
                "Source independent data points and verification metrics",
                "Aggregate synthesis and risk factors"
            )
        }

        return CoordinatedExecutionPlan(
            requiresParallelWorkers = true,
            subObjectives = subObjectives.take(maxConcurrency),
            rationale = "Task benefits from parallel information gathering; spawning ${subObjectives.size} workers."
        )
    }

    suspend fun executeParallelSubtasks(
        parentTask: Task,
        subObjectives: List<String>,
        resolver: WorkspacePathResolver
    ): CoordinatorResult = coroutineScope {
        val workerAgents = subObjectives.mapIndexed { idx, subObj ->
            val workerId = "worker_${idx + 1}"
            val workerWorkspace = "/workspace/process/workers/$workerId"
            WorkerAgent(
                workerId = workerId,
                parentTaskId = parentTask.id,
                objective = subObj,
                isolatedWorkspacePath = workerWorkspace,
                toolDispatcher = toolDispatcher
            )
        }

        // Execute workers concurrently with structured concurrency
        val deferredList = workerAgents.map { worker ->
            async { worker.execute(resolver) }
        }

        val completedTasks = deferredList.awaitAll()

        // Reducer: Synthesize all worker findings into unified report
        val reducedSb = StringBuilder()
        reducedSb.appendLine("# Synthesized Intelligence Report")
        reducedSb.appendLine("Coordinated Parent Task: ${parentTask.title}")
        reducedSb.appendLine("Workers Executed: ${completedTasks.size}")
        reducedSb.appendLine()

        val allArtifacts = mutableListOf<String>()
        completedTasks.forEach { wt ->
            reducedSb.appendLine("## Sub-Objective: ${wt.objective}")
            reducedSb.appendLine("Status: ${wt.status}")
            reducedSb.appendLine(wt.result ?: "No data produced")
            reducedSb.appendLine()
            allArtifacts.addAll(wt.artifacts)
        }

        val synthesisFile = File(resolver.processDir, "coordinated_synthesis.md")
        synthesisFile.writeText(reducedSb.toString())
        allArtifacts.add(resolver.toLogicalPath(synthesisFile))

        CoordinatorResult(
            reducedSummary = reducedSb.toString(),
            workerTasks = completedTasks,
            artifactsProduced = allArtifacts
        )
    }
}
