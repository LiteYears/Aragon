package com.example.aragon.agent

import com.example.aragon.domain.model.ToolResult

enum class LoopType {
    NONE,
    EXACT_REPETITION,
    SEMANTIC_REPETITION,
    NO_PROGRESS,
    STAGNATION,
    FAILURE_LOOP,
    OSCILLATION
}

data class LoopAnalysis(
    val isLooping: Boolean,
    val loopType: LoopType = LoopType.NONE,
    val reason: String = "",
    val recommendedAction: String = "CONTINUE",
    val isCritical: Boolean = false,
    val shouldTerminateBlocked: Boolean = false
)

class LoopDetector(
    private val maxRepeatedFailures: Int = 3,
    private val maxSameActions: Int = 2,
    private val maxNoProgressSteps: Int = 4
) {
    private val history = mutableListOf<ActionRecord>()
    private var consecutiveLoopDetections = 0
    private var unreconciledUnknownAction: ActionRecord? = null

    data class ActionRecord(
        val toolName: String,
        val argsSignature: String,
        val success: Boolean,
        val exitCode: Int,
        val outputHash: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Synchronized
    fun record(toolName: String, args: String, result: ToolResult): LoopAnalysis {
        val sig = "$toolName:${normalizeArgs(args)}"
        val outputSnippet = (if (result.stdout.isNotBlank()) result.stdout else result.stderr).take(200)
        val record = ActionRecord(
            toolName = toolName,
            argsSignature = sig,
            success = result.success,
            exitCode = result.exitCode,
            outputHash = outputSnippet.hashCode()
        )
        history.add(record)

        val isUnknownDeath = result.status == com.example.aragon.domain.model.ToolExecutionStatus.UNKNOWN_AFTER_PROCESS_DEATH ||
            result.terminationReason == "PROCESS_DIED_BEFORE_RESULT" ||
            (result.errorType == "PROCESS_TERMINATED" && result.exitCode == -1)
        if (isUnknownDeath) {
            unreconciledUnknownAction = record
        } else if (unreconciledUnknownAction != null && ToolDispatcher.isReadOnly(toolName, args)) {
            // A read-only inspection tool executed against the environment: state has been reconciled
            unreconciledUnknownAction = null
        }

        val analysis = evaluateLoop(toolName, sig, record)
        if (analysis.isLooping) {
            consecutiveLoopDetections++
            val isCritical = consecutiveLoopDetections >= 2 || analysis.loopType == LoopType.EXACT_REPETITION
            val shouldTerminate = consecutiveLoopDetections >= 2
            return analysis.copy(
                isCritical = isCritical,
                shouldTerminateBlocked = shouldTerminate,
                recommendedAction = if (shouldTerminate) "TERMINATE_BLOCKED" else analysis.recommendedAction
            )
        } else {
            consecutiveLoopDetections = 0
            return analysis
        }
    }

    private fun evaluateLoop(toolName: String, sig: String, record: ActionRecord): LoopAnalysis {
        // 1. Exact Repetition Check (same tool and exact arguments repeated)
        val recentSame = history.takeLast(maxSameActions)
        if (recentSame.size >= maxSameActions && recentSame.all { it.argsSignature == sig }) {
            return LoopAnalysis(
                isLooping = true,
                loopType = LoopType.EXACT_REPETITION,
                reason = "Exact repetition: tool '$toolName' was executed $maxSameActions times with identical arguments.",
                recommendedAction = "DIAGNOSE_AND_REPLAN"
            )
        }

        // 2. Failure Loop (consecutive failures on same tool or commands)
        val recentFailures = history.takeLast(maxRepeatedFailures)
        if (recentFailures.size >= maxRepeatedFailures && recentFailures.all { !it.success }) {
            val tools = recentFailures.map { it.toolName }.distinct().joinToString(", ")
            return LoopAnalysis(
                isLooping = true,
                loopType = LoopType.FAILURE_LOOP,
                reason = "Failure loop: $maxRepeatedFailures consecutive tool invocations failed across [$tools].",
                recommendedAction = "CHANGE_STRATEGY"
            )
        }

        // 3. Oscillation Check (A -> B -> A -> B)
        if (history.size >= 4) {
            val last4 = history.takeLast(4)
            val a1 = last4[0].argsSignature
            val b1 = last4[1].argsSignature
            val a2 = last4[2].argsSignature
            val b2 = last4[3].argsSignature
            if (a1 == a2 && b1 == b2 && a1 != b1) {
                return LoopAnalysis(
                    isLooping = true,
                    loopType = LoopType.OSCILLATION,
                    reason = "Oscillation detected: alternating between two actions repeatedly without resolution.",
                    recommendedAction = "BREAK_OSCILLATION_AND_REPLAN"
                )
            }
        }

        // 4. Semantic Repetition (same output hash / error repeatedly despite slight arg variation)
        val recentOutputHashes = history.takeLast(maxRepeatedFailures)
        if (recentOutputHashes.size >= maxRepeatedFailures &&
            recentOutputHashes.all { !it.success && it.outputHash == record.outputHash && it.toolName == toolName }
        ) {
            return LoopAnalysis(
                isLooping = true,
                loopType = LoopType.SEMANTIC_REPETITION,
                reason = "Semantic repetition: tool '$toolName' producing identical error repeatedly.",
                recommendedAction = "CHANGE_STRATEGY"
            )
        }

        // 5. Stagnation / No Progress Check (reading/inspecting the same files repeatedly without changes)
        if (history.size >= maxNoProgressSteps) {
            val lastN = history.takeLast(maxNoProgressSteps)
            val readOnlyTools = setOf("file_list", "inspect_file", "search_files", "browser_session", "file_read")
            val allReadOnly = lastN.all { it.toolName in readOnlyTools }
            val uniqueSigs = lastN.map { it.argsSignature }.distinct().size
            if (allReadOnly && uniqueSigs <= 2) {
                return LoopAnalysis(
                    isLooping = true,
                    loopType = LoopType.STAGNATION,
                    reason = "Stagnation detected: agent executed $maxNoProgressSteps consecutive read-only operations without generating outputs or changing system state.",
                    recommendedAction = "TERMINATE_BLOCKED"
                )
            }
        }

        return LoopAnalysis(isLooping = false, loopType = LoopType.NONE, recommendedAction = "CONTINUE")
    }

    @Synchronized
    fun checkPreDispatch(toolName: String, args: String): LoopAnalysis? {
        val sig = "$toolName:${normalizeArgs(args)}"
        val unknown = unreconciledUnknownAction
        if (unknown != null) {
            // Read-only tools are always permitted to inspect and reconcile workspace state
            if (ToolDispatcher.isReadOnly(toolName, args)) {
                return null
            }
            // Blind repetition of the exact unverified mutation without prior inspection is blocked
            if (sig == unknown.argsSignature) {
                return LoopAnalysis(
                    isLooping = true,
                    loopType = LoopType.EXACT_REPETITION,
                    reason = "Pre-dispatch intercept: Tool invocation '$toolName' previously terminated with UNKNOWN status (process death). Repeating the exact unverified mutation is blocked until current workspace state is reconciled via read-only inspection.",
                    recommendedAction = "RECONCILE_OR_INSPECT_FIRST"
                )
            }
        }
        return null
    }

    private fun normalizeArgs(args: String): String {
        return args.replace("\\s+".toRegex(), " ").trim()
    }

    @Synchronized
    fun reset() {
        history.clear()
        consecutiveLoopDetections = 0
    }
}
