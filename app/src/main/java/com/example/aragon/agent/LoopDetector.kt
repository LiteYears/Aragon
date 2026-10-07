package com.example.aragon.agent

import com.example.aragon.domain.model.ToolResult

class LoopDetector(
    private val maxRepeatedFailures: Int = 3,
    private val maxSameActions: Int = 3
) {
    private val history = mutableListOf<ActionRecord>()

    data class ActionRecord(
        val toolName: String,
        val argsSignature: String,
        val success: Boolean,
        val exitCode: Int,
        val errorSummary: String
    )

    fun record(toolName: String, args: String, result: ToolResult): LoopAnalysis {
        val sig = "$toolName:${args.trim()}"
        val record = ActionRecord(
            toolName = toolName,
            argsSignature = sig,
            success = result.success,
            exitCode = result.exitCode,
            errorSummary = result.errorMessage ?: result.stderr.take(100)
        )
        history.add(record)

        // 1. Check repeated identical failures
        val recentFailures = history.takeLast(maxRepeatedFailures)
        if (recentFailures.size >= maxRepeatedFailures &&
            recentFailures.all { !it.success && it.argsSignature == sig }
        ) {
            return LoopAnalysis(
                isLooping = true,
                reason = "Identical action failed $maxRepeatedFailures times consecutively ($toolName). Forcing diagnosis and replan.",
                recommendedAction = "DIAGNOSE_AND_REPLAN"
            )
        }

        // 2. Check repeated identical calls even if success (e.g. infinite file reading or listing)
        val recentCalls = history.takeLast(maxSameActions + 1)
        if (recentCalls.size > maxSameActions &&
            recentCalls.all { it.argsSignature == sig }
        ) {
            return LoopAnalysis(
                isLooping = true,
                reason = "Tool '$toolName' called repeatedly with identical parameters with no new outcome. Halting loop.",
                recommendedAction = "BREAK_LOOP"
            )
        }

        return LoopAnalysis(isLooping = false, reason = "", recommendedAction = "CONTINUE")
    }

    fun reset() {
        history.clear()
    }
}

data class LoopAnalysis(
    val isLooping: Boolean,
    val reason: String,
    val recommendedAction: String
)
