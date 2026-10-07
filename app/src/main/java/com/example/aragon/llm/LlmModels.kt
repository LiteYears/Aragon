package com.example.aragon.llm

import com.example.aragon.domain.model.ModelCapabilities
import com.example.aragon.domain.model.ModelInfo

enum class LlmRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class LlmMessage(
    val role: LlmRole,
    val content: String,
    val name: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<LlmToolCall>? = null
)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val tools: List<org.json.JSONObject>? = null,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 4096,
    val stream: Boolean = false
)

data class LlmResponse(
    val content: String,
    val reasoning: String? = null,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val finishReason: String? = null,
    val totalTokens: Int? = null
)

sealed class LlmStreamEvent {
    data class ReasoningDelta(val text: String) : LlmStreamEvent()
    data class ContentDelta(val text: String) : LlmStreamEvent()
    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String
    ) : LlmStreamEvent()
    data class Completed(val response: LlmResponse) : LlmStreamEvent()
    data class Error(val throwable: Throwable) : LlmStreamEvent()
}
