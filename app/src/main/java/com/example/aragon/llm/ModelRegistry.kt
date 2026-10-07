package com.example.aragon.llm

import com.example.aragon.domain.model.ModelCapabilities
import com.example.aragon.domain.model.ModelInfo

class ModelRegistry {

    private val cachedModels = mutableListOf<ModelInfo>()

    init {
        // Pre-populate with standard NVIDIA NIM catalog models
        registerPredefinedModels()
    }

    fun getAllModels(): List<ModelInfo> = synchronized(this) {
        cachedModels.toList()
    }

    fun getModel(id: String): ModelInfo? = synchronized(this) {
        cachedModels.find { it.id == id }
    }

    fun updateModels(models: List<ModelInfo>) = synchronized(this) {
        cachedModels.clear()
        cachedModels.addAll(models)
    }

    fun resolveCapabilities(modelId: String): ModelCapabilities {
        val lower = modelId.lowercase()
        return when {
            lower.contains("deepseek-r1") || lower.contains("reasoning") -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = true,
                streaming = true,
                maxContextTokens = 131072L
            )
            lower.contains("vision") || lower.contains("pixtral") -> ModelCapabilities(
                toolCalling = true,
                vision = true,
                reasoning = false,
                streaming = true,
                maxContextTokens = 131072L
            )
            lower.contains("llama-3.3") || lower.contains("llama-3.1") -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = false,
                streaming = true,
                maxContextTokens = 131072L
            )
            lower.contains("mixtral") || lower.contains("mistral") -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = false,
                streaming = true,
                maxContextTokens = 65536L
            )
            else -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = false,
                streaming = true,
                maxContextTokens = 32768L
            )
        }
    }

    private fun registerPredefinedModels() {
        val defaultList = listOf(
            ModelInfo(
                id = "meta/llama-3.3-70b-instruct",
                name = "Llama 3.3 70B Instruct",
                publisher = "Meta",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = false, streaming = true, maxContextTokens = 131072L),
                description = "Recommended for autonomous tool execution and fast structured decisions."
            ),
            ModelInfo(
                id = "deepseek-ai/deepseek-r1",
                name = "DeepSeek R1",
                publisher = "DeepSeek",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = true, streaming = true, maxContextTokens = 131072L),
                description = "Advanced reasoning model for complex planning, debugging, and verification."
            ),
            ModelInfo(
                id = "meta/llama-3.2-90b-vision-instruct",
                name = "Llama 3.2 90B Vision Instruct",
                publisher = "Meta",
                capabilities = ModelCapabilities(toolCalling = true, vision = true, reasoning = false, streaming = true, maxContextTokens = 131072L),
                description = "Multimodal vision model for document image reconstruction and diagram analysis."
            ),
            ModelInfo(
                id = "mistralai/mixtral-8x22b-instruct",
                name = "Mixtral 8x22B Instruct",
                publisher = "Mistral AI",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = false, streaming = true, maxContextTokens = 65536L),
                description = "High-throughput sparse mixture-of-experts for coding and file transformation."
            ),
            ModelInfo(
                id = "nvidia/nemotron-4-340b-instruct",
                name = "Nemotron-4 340B Instruct",
                publisher = "NVIDIA",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = false, streaming = true, maxContextTokens = 4096L),
                description = "Flagship NVIDIA enterprise model optimized for structured synthetic data."
            )
        )
        cachedModels.addAll(defaultList)
    }
}
