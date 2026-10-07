package com.example.aragon.llm

import com.example.aragon.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    suspend fun listModels(): List<ModelInfo>

    suspend fun complete(
        request: LlmRequest
    ): LlmResponse

    fun stream(
        request: LlmRequest
    ): Flow<LlmStreamEvent>
}
