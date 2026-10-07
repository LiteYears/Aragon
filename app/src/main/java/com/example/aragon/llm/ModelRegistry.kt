package com.example.aragon.llm

import com.example.aragon.domain.model.ModelCapabilities
import com.example.aragon.domain.model.ModelInfo

class ModelRegistry {

    private val cachedModels = mutableListOf<ModelInfo>()

    init {
        // Pre-populate with verified active NVIDIA NIM catalog models
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
            lower.contains("reason2") || lower.contains("reasoning") || lower.contains("lightning") -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = true,
                streaming = true,
                maxContextTokens = 131072L
            )
            lower.contains("vision") || lower.contains("vlm") -> ModelCapabilities(
                toolCalling = true,
                vision = true,
                reasoning = false,
                streaming = true,
                maxContextTokens = 131072L
            )
            lower.contains("gpt-oss") || lower.contains("llama-3.2") -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = true,
                streaming = true,
                maxContextTokens = 131072L
            )
            else -> ModelCapabilities(
                toolCalling = true,
                vision = false,
                reasoning = false,
                streaming = true,
                maxContextTokens = 65536L
            )
        }
    }

    private fun registerPredefinedModels() {
        val defaultList = listOf(
            ModelInfo(
                id = "meta/llama-3.2-11b-vision-instruct",
                name = "Llama 3.2 11B Vision Instruct",
                publisher = "Meta",
                capabilities = ModelCapabilities(toolCalling = true, vision = true, reasoning = false, streaming = true, maxContextTokens = 131072L),
                description = "Primary verified multimodal model with fast autonomous tool calling and vision."
            ),
            ModelInfo(
                id = "meta/llama-3.2-90b-vision-instruct",
                name = "Llama 3.2 90B Vision Instruct",
                publisher = "Meta",
                capabilities = ModelCapabilities(toolCalling = true, vision = true, reasoning = true, streaming = true, maxContextTokens = 131072L),
                description = "Large-scale multimodal foundation model with high reasoning capability."
            ),
            ModelInfo(
                id = "openai/gpt-oss-20b",
                name = "GPT-OSS 20B",
                publisher = "OpenAI / NVIDIA",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = true, streaming = true, maxContextTokens = 131072L),
                description = "Open reasoning model optimized for chain-of-thought planning and tool execution."
            ),
            ModelInfo(
                id = "nvidia/nemotron-3.5-lightning-30b-a3b",
                name = "Nemotron-3.5 Lightning 30B",
                publisher = "NVIDIA",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = true, streaming = true, maxContextTokens = 65536L),
                description = "High-efficiency NVIDIA enterprise reasoning model with fast generation."
            ),
            ModelInfo(
                id = "google/diffusiongemma-26b-a4b-it",
                name = "Diffusion Gemma 26B Instruct",
                publisher = "Google / NVIDIA",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = false, streaming = true, maxContextTokens = 65536L),
                description = "Instruction-tuned Gemma architecture for general analysis and document drafting."
            ),
            ModelInfo(
                id = "nvidia/nemotron-3-super-120b-a12b",
                name = "Nemotron-3 Super 120B",
                publisher = "NVIDIA",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = true, streaming = true, maxContextTokens = 65536L),
                description = "Large-scale NVIDIA super model for complex synthesis and multi-step plans."
            ),
            ModelInfo(
                id = "meta/muse-glimmer-30b",
                name = "Muse Glimmer 30B",
                publisher = "Meta",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = false, streaming = true, maxContextTokens = 65536L),
                description = "Instruction-tuned 30B model for synthesis and analytical document generation."
            ),
            ModelInfo(
                id = "nvidia/nemotron-3-ultra-550b-a55b",
                name = "Nemotron-3 Ultra 550B",
                publisher = "NVIDIA",
                capabilities = ModelCapabilities(toolCalling = true, vision = false, reasoning = true, streaming = true, maxContextTokens = 65536L),
                description = "Massive-scale enterprise reasoning model for complex architectural planning."
            )
        )
        cachedModels.addAll(defaultList)
    }
}
