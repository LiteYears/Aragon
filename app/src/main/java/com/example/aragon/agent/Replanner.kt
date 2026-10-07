package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.FailedApproach
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.ToolResult
import java.util.UUID

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
    val updatedSteps: List<PlanStep>? = null
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
        // 1. If verification already passed, complete
        if (verificationResult?.isVerified == true) {
            return ReplanDecision(
                type = ReplanDecisionType.COMPLETE,
                explanation = "Verification succeeded: All required criteria and artifacts are satisfied."
            )
        }

        val lastFailure = recentResults.lastOrNull { !it.second.success }
        val activeStep = currentPlan.find { it.status == StepStatus.IN_PROGRESS || it.status == StepStatus.PENDING }

        // 2. Check if a Python or command failure occurred due to missing library or environment
        if (lastFailure != null) {
            val (toolName, result) = lastFailure
            val err = result.stderr.lowercase()

            if (err.contains("no module named") || err.contains("modulenotfounderror") || err.contains("nameerror")) {
                // Diagnose python environment issue -> change strategy to built-in generator or fallback
                return ReplanDecision(
                    type = ReplanDecisionType.CHANGE_STRATEGY,
                    explanation = "Python environment is missing an external dependency. Switching strategy to built-in OpenXML document generator or standard library script.",
                    suggestedTool = "python_execute",
                    suggestedParameters = """{"code": "# Standard library fallback\n"}"""
                )
            }

            if (err.contains("permission denied") || result.exitCode == 126) {
                return ReplanDecision(
                    type = ReplanDecisionType.REPAIR_CURRENT_STEP,
                    explanation = "Permission denied on file or directory. Repairing file permissions or executing in /workspace.",
                    suggestedTool = "run_command",
                    suggestedParameters = """{"command": "chmod +x ${result.workingDirectory}"}"""
                )
            }

            if (result.timedOut) {
                return ReplanDecision(
                    type = ReplanDecisionType.CHANGE_STRATEGY,
                    explanation = "Previous operation timed out. Splitting into smaller incremental operations.",
                    suggestedTool = "run_command"
                )
            }
        }

        // 3. Check if too many approaches failed (e.g. 5+ failures)
        if (failedApproaches.size >= 5) {
            return ReplanDecision(
                type = ReplanDecisionType.REQUEST_USER_INPUT,
                explanation = "Multiple strategies failed (${failedApproaches.size} attempts). Requesting guidance or additional environment permissions."
            )
        }

        // 4. Default: repair active step and continue
        return ReplanDecision(
            type = ReplanDecisionType.REPAIR_CURRENT_STEP,
            explanation = "Adjusting arguments and repairing current step (${activeStep?.title ?: "execution"})."
        )
    }
}
