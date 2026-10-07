package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.FailedApproach
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.ToolResult

enum class ReplanDecisionType {
    CONTINUE_CURRENT_STEP,
    REPAIR_CURRENT_STEP,
    CHANGE_STRATEGY,
    SKIP_STEP,
    REQUEST_USER_INPUT,
    ABORT,
    COMPLETE
}

data class ReplanDecision(
    val type: ReplanDecisionType,
    val explanation: String,
    val suggestedTool: String? = null,
    val suggestedParameters: String? = null,
    val updatedSteps: List<PlanStep>? = null,
    val suggestedIntent: String? = null
)

class Replanner {

    fun analyzeAndReplan(
        task: Task,
        currentPlan: List<PlanStep>,
        failedApproaches: List<FailedApproach>,
        recentResults: List<Pair<String, ToolResult>>,
        artifacts: List<Artifact>,
        verificationResult: VerificationResult?,
        resolver: WorkspacePathResolver
    ): ReplanDecision {
        // 1. If verification already passed, complete immediately
        if (verificationResult?.isVerified == true) {
            return ReplanDecision(
                type = ReplanDecisionType.COMPLETE,
                explanation = "Verification succeeded: All required criteria and deliverables are satisfied.",
                suggestedIntent = "Deliver product artifacts and finalize session."
            )
        }

        val lastFailure = recentResults.lastOrNull { !it.second.success }
        val activeStep = currentPlan.find { it.status == StepStatus.IN_PROGRESS || it.status == StepStatus.PENDING }

        // 2. Check if a Python or command failure occurred due to missing library or environment
        if (lastFailure != null) {
            val (toolName, result) = lastFailure
            val err = result.stderr.lowercase()

            if (err.contains("no module named") || err.contains("modulenotfounderror") || err.contains("nameerror")) {
                return ReplanDecision(
                    type = ReplanDecisionType.CHANGE_STRATEGY,
                    explanation = "Python environment is missing an external dependency. Switching strategy to built-in OpenXML document generator or standard library script.",
                    suggestedTool = "python_execute",
                    suggestedParameters = """{"code": "# Standard library fallback\n"}""",
                    suggestedIntent = "Switch to built-in generator or standard library without external dependencies."
                )
            }

            if (err.contains("permission denied") || result.exitCode == 126) {
                return ReplanDecision(
                    type = ReplanDecisionType.REPAIR_CURRENT_STEP,
                    explanation = "Permission denied on file or directory. Repairing file permissions or executing in /workspace.",
                    suggestedTool = "run_command",
                    suggestedParameters = """{"command": "chmod +x ${result.workingDirectory}"}""",
                    suggestedIntent = "Apply execution permissions and retry."
                )
            }

            if (result.timedOut) {
                return ReplanDecision(
                    type = ReplanDecisionType.CHANGE_STRATEGY,
                    explanation = "Previous operation timed out. Splitting into smaller incremental operations.",
                    suggestedTool = "run_command",
                    suggestedIntent = "Split long operation into incremental steps."
                )
            }
        }

        // 3. Check if too many approaches failed (e.g. 4+ failures) -> halt instead of looping forever
        if (failedApproaches.size >= 4) {
            return ReplanDecision(
                type = ReplanDecisionType.ABORT,
                explanation = "Exhausted viable strategies (${failedApproaches.size} attempts failed). Halting execution to prevent destructive loop.",
                suggestedIntent = "Execution blocked due to repeated strategy failure."
            )
        }

        // 4. Default: repair active step and continue with explicit intent
        return ReplanDecision(
            type = ReplanDecisionType.REPAIR_CURRENT_STEP,
            explanation = "Adjusting parameters and repairing current step (${activeStep?.title ?: "execution"}).",
            suggestedIntent = "Retry step with adjusted parameters."
        )
    }
}
