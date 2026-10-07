package com.example.aragon.agent

import com.example.aragon.domain.model.ToolResult

enum class LoopType {
    NONE,
    EXACT_REPETITION,
    SEMANTIC_REPETITION,
    NO_PROGRESS,
    FAILURE_LOOP,
    OSCILLATION
}

data class LoopAnalysis(
    val isLooping: Boolean,
    val loopType: LoopType = LoopType.NONE,
    val reason: String = "",
    val recommendedAction: String = "CONTINUE"
)

class LoopDetector(
    private val maxRepeatedFailures: Int = 3,
    private val maxSameActions: Int = 3,
    private val maxNoProgressSteps: Int = 6
) {
    private val history = mutableListOf<ActionRecord>()

    data class ActionRecord(
        val toolName: String,
        val argsSignature: String,
        val success: Boolean,
        val exitCode: Int,
        val outputHash: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

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

        // 2. Failure Loop (consecutive failures on same tool)
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
                    reason = "Oscillation detected: alternating between two actions repeatedly.",
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

        // 5. No Progress Check (many actions with identical output and no files/artifacts produced)
        if (history.size >= maxNoProgressSteps) {
            val lastN = history.takeLast(maxNoProgressSteps)
            if (lastN.all { it.toolName == "file_list" || it.toolName == "inspect_file" || it.toolName == "search_files" }) {
                return LoopAnalysis(
                    isLooping = true,
                    loopType = LoopType.NO_PROGRESS,
                    reason = "No progress: agent is reading/listing files repeatedly without executing changes.",
                    recommendedAction = "FORCE_EXECUTION"
                )
            }
        }

        return LoopAnalysis(isLooping = false, loopType = LoopType.NONE, recommendedAction = "CONTINUE")
    }

    private fun normalizeArgs(args: String): String {
        return args.replace("\\s+".toRegex(), " ").trim()
    }

    fun reset() {
        history.clear()
    }
}
