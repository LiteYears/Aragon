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

    private val baseUrl = "https://integrate.api.nvidia.com/v1"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.nvidiaApiKey.value.trim()
        if (apiKey.isBlank()) {
            return@withContext modelRegistry.getAllModels()
        }

        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
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

                val resultList = mutableListOf<ModelInfo>()
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val id = m.optString("id", "")
                    if (id.isNotBlank()) {
                        val publisher = m.optString("owned_by", id.substringBefore("/"))
                        val name = id.substringAfter("/").replace("-", " ")
                            .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

                        resultList.add(
                            ModelInfo(
                                id = id,
                                name = name,
                                publisher = publisher,
                                capabilities = modelRegistry.resolveCapabilities(id),
                                description = "NVIDIA NIM catalog model ($id)"
                            )
                        )
                    }
                }

                if (resultList.isNotEmpty()) {
                    modelRegistry.updateModels(resultList)
                    return@withContext resultList
                }
            }
        } catch (e: Exception) {
            // Network fallback to cached registry
        }
        modelRegistry.getAllModels()
    }

    override suspend fun complete(request: LlmRequest): LlmResponse = withContext(Dispatchers.IO) {
        val apiKey = preferencesManager.nvidiaApiKey.value.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Aragon NIM API key is not configured. Please enter your key in Settings.")
        }

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
                val errorMsg = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull() ?: body
                throw IllegalStateException("NVIDIA NIM API Error (${response.code}): $errorMsg")
            }

            parseResponseJson(body)
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val apiKey = preferencesManager.nvidiaApiKey.value.trim()
        if (apiKey.isBlank()) {
            emit(LlmStreamEvent.Error(IllegalStateException("Aragon NIM API key is not configured. Please enter your key in Settings.")))
            return@flow
        }

        val payload = buildRequestJson(request, stream = true)
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
                    val err = response.body?.string() ?: ""
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

                val finalToolCalls = toolCallMap.values.map { (id, name, args) ->
                    LlmToolCall(
                        id = id.ifEmpty { "call_${System.currentTimeMillis()}" },
                        name = name,
                        argumentsJson = args.toString()
                    )
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
            msgObj.put("content", m.content)
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
        val content = message.optString("content", "")
        val reasoning = message.optString("reasoning_content", "").ifEmpty { null }
        val finishReason = choice.optString("finish_reason", "")

        val toolCallsList = mutableListOf<LlmToolCall>()
        val tcArr = message.optJSONArray("tool_calls")
        if (tcArr != null) {
            for (i in 0 until tcArr.length()) {
                val tc = tcArr.getJSONObject(i)
                val id = tc.optString("id", "call_$i")
                val func = tc.optJSONObject("function") ?: JSONObject()
                val name = func.optString("name", "")
                val arguments = func.optString("arguments", "{}")
                toolCallsList.add(LlmToolCall(id = id, name = name, argumentsJson = arguments))
            }
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
