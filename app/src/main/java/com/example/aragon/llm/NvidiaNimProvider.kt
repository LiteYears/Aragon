package com.example.aragon.llm

import com.example.aragon.data.preferences.PreferencesManager
import com.example.aragon.domain.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class NvidiaNimProvider(
    private val preferencesManager: PreferencesManager,
    private val modelRegistry: ModelRegistry,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : LlmProvider {

    private val baseUrl: String
        get() = preferencesManager.endpoint.value.trim().removeSuffix("/")

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val VERIFIED_FALLBACK_MODEL = "meta/llama-3.2-11b-vision-instruct"
        private val DEPRECATED_MODELS = setOf(
            "meta/llama-3.3-70b-instruct"
        )
    }

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.nvidiaApiKey.value.trim()
        val currentUrl = baseUrl
        if (apiKey.isBlank()) {
            return@withContext modelRegistry.getAllModels()
        }

        try {
            val request = Request.Builder()
                .url("$currentUrl/models")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext modelRegistry.getAllModels()
                }
                val bodyStr = response.body?.string() ?: return@withContext modelRegistry.getAllModels()
                val json = JSONObject(bodyStr)
                val data = json.optJSONArray("data") ?: return@withContext modelRegistry.getAllModels()

                // Known active models verified to be accessible without 404 Account Function errors
                val verifiedIds = setOf(
                    VERIFIED_FALLBACK_MODEL,
                    "meta/llama-3.2-90b-vision-instruct",
                    "openai/gpt-oss-20b",
                    "nvidia/nemotron-3.5-lightning-30b-a3b",
                    "google/diffusiongemma-26b-a4b-it",
                    "nvidia/nemotron-3-super-120b-a12b",
                    "meta/muse-glimmer-30b",
                    "nvidia/ising-calibration-1.5-31b",
                    "nvidia/nemotron-3-ultra-550b-a55b"
                )

                val resultList = mutableListOf<ModelInfo>()
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val id = m.optString("id", "")
                    // If connecting to standard integrate.api.nvidia.com, filter to verified allowed models
                    // to avoid giving users 404 'Function not found for account' on unsupported catalog items
                    val isSupported = if (currentUrl.contains("nvidia.com")) {
                        id in verifiedIds
                    } else {
                        id.isNotBlank() && id !in DEPRECATED_MODELS
                    }

                    if (isSupported) {
                        val publisher = m.optString("owned_by", id.substringBefore("/"))
                        val name = id.substringAfter("/").replace("-", " ")
                            .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

                        resultList.add(
                            ModelInfo(
                                id = id,
                                name = name,
                                publisher = publisher,
                                capabilities = modelRegistry.resolveCapabilities(id),
                                description = "NVIDIA NIM verified model ($id)"
                            )
                        )
                    }
                }

                if (resultList.isNotEmpty()) {
                    val verifiedOrder = listOf(
                        VERIFIED_FALLBACK_MODEL,
                        "meta/llama-3.2-90b-vision-instruct",
                        "openai/gpt-oss-20b",
                        "nvidia/nemotron-3.5-lightning-30b-a3b",
                        "google/diffusiongemma-26b-a4b-it",
                        "nvidia/nemotron-3-super-120b-a12b",
                        "meta/muse-glimmer-30b",
                        "nvidia/nemotron-3-ultra-550b-a55b"
                    )
                    val sortedList = resultList.sortedBy { m ->
                        val idx = verifiedOrder.indexOf(m.id)
                        if (idx != -1) idx else 100 + resultList.indexOf(m)
                    }

                    modelRegistry.updateModels(sortedList)
                    return@withContext sortedList
                }
            }
        } catch (e: Exception) {
            // Network fallback to cached registry
        }
        modelRegistry.getAllModels()
    }

    suspend fun testConnection(
        apiKeyOverride: String? = null,
        endpointOverride: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKeyOverride?.trim()?.ifBlank { null }
            ?: preferencesManager.nvidiaApiKey.value.trim()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is empty. Please enter your key."))
        }

        val url = (endpointOverride?.trim()?.removeSuffix("/")?.ifBlank { null }
            ?: baseUrl).removeSuffix("/")

        val pingModel = VERIFIED_FALLBACK_MODEL
        val payload = JSONObject().apply {
            put("model", pingModel)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "ping")
                })
            })
            put("max_tokens", 5)
            put("temperature", 0.1)
        }

        val httpRequest = Request.Builder()
            .url("$url/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = parseErrorMessage(response.code, body)
                    return@withContext Result.failure(IllegalStateException("Endpoint returned HTTP ${response.code}: $err"))
                }
                Result.success("Connection verified! Model $pingModel responded successfully (HTTP 200).")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.nvidiaApiKey.value.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Aragon NIM API key is not configured. Please enter your key in Settings.")
        }

        try {
            executeComplete(request, apiKey)
        } catch (e: Exception) {
            val errorMsg = e.message.orEmpty()
            // If the model failed because of tool choice or function permission (404/410/400),
            // automatically retry with VERIFIED_FALLBACK_MODEL or without unsupported tool calling
            if (shouldFallbackModel(errorMsg)) {
                if (request.model != VERIFIED_FALLBACK_MODEL) {
                    val fallbackReq = request.copy(model = VERIFIED_FALLBACK_MODEL)
                    preferencesManager.setSelectedModel(VERIFIED_FALLBACK_MODEL)
                    return@withContext executeComplete(fallbackReq, apiKey)
                } else if (!request.tools.isNullOrEmpty() && (errorMsg.contains("tool", ignoreCase = true) || errorMsg.contains("400"))) {
                    // Retry fallback model without tool calling headers
                    val noToolsReq = request.copy(tools = null)
                    return@withContext executeComplete(noToolsReq, apiKey)
                }
            }
            throw e
        }
    }

    private fun shouldFallbackModel(errorMessage: String): Boolean {
        val lower = errorMessage.lowercase()
        return lower.contains("not found for account") ||
                lower.contains("404") ||
                lower.contains("end of life") ||
                lower.contains("410") ||
                lower.contains("function") ||
                lower.contains("auto tool choice") ||
                lower.contains("tool choice requires") ||
                lower.contains("tool-call") ||
                lower.contains("unsupported")
    }

    private fun executeComplete(request: LlmRequest, apiKey: String): LlmResponse {
        val payload = buildRequestJson(request, stream = false)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, body)
                throw IllegalStateException("NVIDIA NIM API Error (${response.code}): $errorMsg")
            }

            return parseResponseJson(body)
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val apiKey = preferencesManager.nvidiaApiKey.value.trim()
        if (apiKey.isBlank()) {
            emit(LlmStreamEvent.Error(IllegalStateException("Aragon NIM API key is not configured. Please enter your key in Settings.")))
            return@flow
        }

        val effectiveModel = if (request.model in DEPRECATED_MODELS) VERIFIED_FALLBACK_MODEL else request.model
        val effectiveRequest = request.copy(model = effectiveModel)

        val payload = buildRequestJson(effectiveRequest, stream = true)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = parseErrorMessage(response.code, response.body?.string() ?: "")
                    emit(LlmStreamEvent.Error(IllegalStateException("NVIDIA NIM Streaming Error (${response.code}): $err")))
                    return@use
                }

                val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
                var line: String?
                val fullContent = StringBuilder()
                val fullReasoning = StringBuilder()
                val toolCallMap = mutableMapOf<Int, Triple<String, String, StringBuilder>>() // index -> (id, name, args)

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line!!.trim()
                    if (currentLine.isEmpty()) continue
                    if (currentLine.startsWith("data:")) {
                        val dataStr = currentLine.removePrefix("data:").trim()
                        if (dataStr == "[DONE]") {
                            break
                        }

                        val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
                        val choices = chunkJson.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue

                        val choice = choices.getJSONObject(0)
                        val delta = choice.optJSONObject("delta") ?: continue

                        // 1. Reasoning delta
                        val reasoningDelta = delta.optString("reasoning_content", "")
                        if (reasoningDelta.isNotEmpty()) {
                            fullReasoning.append(reasoningDelta)
                            emit(LlmStreamEvent.ReasoningDelta(reasoningDelta))
                        }

                        // 2. Content delta
                        val contentDelta = delta.optString("content", "")
                        if (contentDelta.isNotEmpty()) {
                            fullContent.append(contentDelta)
                            emit(LlmStreamEvent.ContentDelta(contentDelta))
                        }

                        // 3. Tool call delta
                        val toolCalls = delta.optJSONArray("tool_calls")
                        if (toolCalls != null) {
                            for (t in 0 until toolCalls.length()) {
                                val tc = toolCalls.getJSONObject(t)
                                val index = tc.optInt("index", t)
                                val id = tc.optString("id", "")
                                val func = tc.optJSONObject("function")
                                val name = func?.optString("name", "") ?: ""
                                val argsDelta = func?.optString("arguments", "") ?: ""

                                val existing = toolCallMap[index]
                                if (existing == null) {
                                    val argsSb = StringBuilder(argsDelta)
                                    toolCallMap[index] = Triple(id, name, argsSb)
                                } else {
                                    val currentId = if (id.isNotEmpty()) id else existing.first
                                    val currentName = if (name.isNotEmpty()) name else existing.second
                                    existing.third.append(argsDelta)
                                    toolCallMap[index] = Triple(currentId, currentName, existing.third)
                                }

                                emit(LlmStreamEvent.ToolCallDelta(index, id.ifEmpty { null }, name.ifEmpty { null }, argsDelta))
                            }
                        }
                    }
                }

                var finalToolCalls = toolCallMap.values.map { (id, name, args) ->
                    LlmToolCall(
                        id = id.ifEmpty { "call_${System.currentTimeMillis()}" },
                        name = name,
                        argumentsJson = args.toString()
                    )
                }
                if (finalToolCalls.isEmpty() && fullContent.isNotEmpty()) {
                    finalToolCalls = ToolCallParser.parseFromContent(fullContent.toString())
                }

                emit(
                    LlmStreamEvent.Completed(
                        LlmResponse(
                            content = fullContent.toString(),
                            reasoning = fullReasoning.toString().ifEmpty { null },
                            toolCalls = finalToolCalls
                        )
                    )
                )
            }
        } catch (e: Exception) {
            emit(LlmStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun parseErrorMessage(code: Int, body: String): String {
        return runCatching {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            val detail = json.optString("detail", "")
            val title = json.optString("title", "")
            val msg = errObj?.optString("message", "") ?: detail.ifEmpty { title }

            when {
                detail.contains("Not found for account") ->
                    "Model function permission not granted for account (404): $detail"
                detail.contains("end of life") || code == 410 ->
                    "Model reached end-of-life on NVIDIA NIM (410 Gone): $detail"
                msg.isNotBlank() ->
                    msg
                else ->
                    "HTTP $code: $body"
            }
        }.getOrDefault("HTTP $code: $body")
    }

    private fun buildRequestJson(request: LlmRequest, stream: Boolean): JSONObject {
        val root = JSONObject()
        root.put("model", request.model)
        root.put("stream", stream)
        root.put("temperature", request.temperature)
        root.put("max_tokens", request.maxTokens)

        val messagesArr = JSONArray()
        for (m in request.messages) {
            val msgObj = JSONObject()
            msgObj.put("role", m.role.name.lowercase())
            if (m.content.isNotEmpty() || m.toolCalls.isNullOrEmpty()) {
                msgObj.put("content", m.content)
            } else {
                msgObj.put("content", JSONObject.NULL)
            }
            if (m.name != null) msgObj.put("name", m.name)
            if (m.toolCallId != null) msgObj.put("tool_call_id", m.toolCallId)

            if (!m.toolCalls.isNullOrEmpty()) {
                val tcArr = JSONArray()
                for (tc in m.toolCalls) {
                    val tcObj = JSONObject()
                    tcObj.put("id", tc.id)
                    tcObj.put("type", "function")
                    val funcObj = JSONObject()
                    funcObj.put("name", tc.name)
                    funcObj.put("arguments", tc.argumentsJson)
                    tcObj.put("function", funcObj)
                    tcArr.put(tcObj)
                }
                msgObj.put("tool_calls", tcArr)
            }
            messagesArr.put(msgObj)
        }
        root.put("messages", messagesArr)

        if (!request.tools.isNullOrEmpty()) {
            val toolsArr = JSONArray()
            request.tools.forEach { toolsArr.put(it) }
            root.put("tools", toolsArr)
            root.put("tool_choice", "auto")
        }

        return root
    }

    private fun parseResponseJson(jsonStr: String): LlmResponse {
        val root = JSONObject(jsonStr)
        val choices = root.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            return LlmResponse(content = "")
        }

        val choice = choices.getJSONObject(0)
        val message = choice.optJSONObject("message") ?: JSONObject()
        val content = if (message.isNull("content")) "" else message.optString("content", "").let { if (it == "null") "" else it }
        val reasoningRaw = if (!message.isNull("reasoning_content")) {
            message.optString("reasoning_content", "")
        } else if (!message.isNull("reasoning")) {
            message.optString("reasoning", "")
        } else {
            ""
        }
        val reasoning = reasoningRaw.takeIf { it.isNotBlank() && it != "null" }
        val finishReason = choice.optString("finish_reason", "")

        var toolCallsList = ToolCallParser.parseFromOpenAiMessage(message)
        if (toolCallsList.isEmpty() && content.isNotBlank()) {
            toolCallsList = ToolCallParser.parseFromContent(content)
        }

        val usage = root.optJSONObject("usage")
        val totalTokens = usage?.optInt("total_tokens")

        return LlmResponse(
            content = content,
            reasoning = reasoning,
            toolCalls = toolCallsList,
            finishReason = finishReason,
            totalTokens = totalTokens
        )
    }
}
