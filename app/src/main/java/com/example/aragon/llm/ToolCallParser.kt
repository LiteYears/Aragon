package com.example.aragon.llm

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

sealed class ToolParseResult {
    object NoToolCall : ToolParseResult()
    data class Success(val calls: List<LlmToolCall>, val partialFailure: Failure? = null) : ToolParseResult()
    data class Failure(val rawSnippet: String, val reason: String) : ToolParseResult()
}

object ToolCallParser {

    /**
     * Parses native OpenAI/NVIDIA NIM tool calls array from choice message.
     */
    fun parseFromOpenAiMessage(message: JSONObject): List<LlmToolCall> {
        val toolCallsList = mutableListOf<LlmToolCall>()
        val tcArr = message.optJSONArray("tool_calls") ?: return emptyList()

        for (i in 0 until tcArr.length()) {
            val tc = tcArr.optJSONObject(i) ?: continue
            val id = tc.optString("id", "").ifBlank { "call_${System.currentTimeMillis()}_$i" }
            val func = tc.optJSONObject("function") ?: JSONObject()
            val rawName = func.optString("name", "").trim()
            val name = if (rawName.isBlank()) "unknown_tool" else rawName

            val argsRaw = func.opt("arguments")
            val argumentsJson = when (argsRaw) {
                is JSONObject -> argsRaw.toString()
                is String -> argsRaw.ifBlank { "{}" }
                else -> "{}"
            }

            toolCallsList.add(
                LlmToolCall(
                    id = id,
                    name = name,
                    argumentsJson = normalizeJsonArguments(argumentsJson)
                )
            )
        }

        return toolCallsList
    }

    /**
     * Parses structured tool calls from text content, strictly distinguishing:
     * - NoToolCall: text genuinely contains no tool call markers or structures
     * - Success: one or more valid tool calls extracted (may carry partialFailure if one call was malformed)
     * - Failure: text appears to contain a tool invocation but parsing failed (malformed JSON, broken XML, etc.)
     */
    fun parse(content: String): ToolParseResult {
        if (content.isBlank()) return ToolParseResult.NoToolCall

        val extracted = mutableListOf<LlmToolCall>()
        var foundMarker = false
        var failureReason: String? = null
        var failureSnippet: String? = null

        // 1. Check for XML style: <tool_call>...</tool_call> or <tool>...</tool> or <function_call>...</function_call>
        val xmlPattern = Pattern.compile("<(?:tool_call|tool|function_call)>([\\s\\S]*?)(?:</(?:tool_call|tool|function_call)>|$)", Pattern.CASE_INSENSITIVE)
        val xmlMatcher = xmlPattern.matcher(content)
        while (xmlMatcher.find()) {
            foundMarker = true
            val block = xmlMatcher.group(1)?.trim() ?: continue
            val calls = parseJsonToolBlocks(block)
            if (calls.isNotEmpty()) {
                extracted.addAll(calls)
            } else {
                failureReason = "Malformed JSON or parameters inside XML tool tag"
                failureSnippet = block.take(300)
            }
        }
        if (extracted.isNotEmpty()) {
            val partialFail = if (foundMarker && failureReason != null) {
                ToolParseResult.Failure(failureSnippet ?: "", failureReason)
            } else null
            return ToolParseResult.Success(extracted, partialFail)
        }
        if (foundMarker && failureReason != null) return ToolParseResult.Failure(failureSnippet ?: "", failureReason)

        // 1b. Check for Claude-style XML invoke blocks: <invoke name="tool_name">...<parameter name="key">val</parameter>...</invoke>
        val claudeInvokePattern = Pattern.compile("<invoke\\s+name=[\"']?([^\"'>\\s]+)[\"']?>([\\s\\S]*?)(?:</invoke>|$)", Pattern.CASE_INSENSITIVE)
        val claudeMatcher = claudeInvokePattern.matcher(content)
        while (claudeMatcher.find()) {
            foundMarker = true
            val toolName = claudeMatcher.group(1)?.trim() ?: continue
            val body = claudeMatcher.group(2) ?: ""
            val paramPattern = Pattern.compile("<parameter\\s+name=[\"']?([^\"'>\\s]+)[\"']?>([\\s\\S]*?)</parameter>", Pattern.CASE_INSENSITIVE)
            val paramMatcher = paramPattern.matcher(body)
            val argsObj = JSONObject()
            var hasParams = false
            while (paramMatcher.find()) {
                val pName = paramMatcher.group(1)?.trim() ?: continue
                val pVal = paramMatcher.group(2)?.trim() ?: ""
                argsObj.put(pName, pVal)
                hasParams = true
            }
            val argsJson = if (hasParams) argsObj.toString() else normalizeJsonArguments(body.trim())
            extracted.add(
                LlmToolCall(
                    id = "call_xml_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                    name = toolName,
                    argumentsJson = argsJson
                )
            )
        }
        if (extracted.isNotEmpty()) return ToolParseResult.Success(extracted)

        // 1c. Check for <function=tool_name>{"arg": "val"}</function>
        val funcTagPattern = Pattern.compile("<function=([^>]+)>([\\s\\S]*?)(?:</function>|$)", Pattern.CASE_INSENSITIVE)
        val funcTagMatcher = funcTagPattern.matcher(content)
        while (funcTagMatcher.find()) {
            foundMarker = true
            val toolName = funcTagMatcher.group(1)?.trim() ?: continue
            val body = funcTagMatcher.group(2)?.trim() ?: "{}"
            extracted.add(
                LlmToolCall(
                    id = "call_func_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                    name = toolName,
                    argumentsJson = normalizeJsonArguments(body)
                )
            )
        }
        if (extracted.isNotEmpty()) return ToolParseResult.Success(extracted)

        // 2. Check for Markdown code blocks with tool_call, json, or tool
        val codeBlockPattern = Pattern.compile("```(?:tool_call|json|tool)?\\s*([\\s\\S]*?)(?:```|$)", Pattern.CASE_INSENSITIVE)
        val codeMatcher = codeBlockPattern.matcher(content)
        while (codeMatcher.find()) {
            val block = codeMatcher.group(1)?.trim() ?: continue
            val looksLikeTool = block.contains("\"name\"") || block.contains("\"tool\"") || block.contains("\"function\"")
            if (looksLikeTool) {
                foundMarker = true
                val calls = parseJsonToolBlocks(block)
                if (calls.isNotEmpty()) {
                    extracted.addAll(calls)
                } else {
                    failureReason = "Malformed or unclosed JSON tool block inside code fence"
                    failureSnippet = block.take(300)
                }
            }
        }
        if (extracted.isNotEmpty()) {
            val partialFail = if (foundMarker && failureReason != null) {
                ToolParseResult.Failure(failureSnippet ?: "", failureReason)
            } else null
            return ToolParseResult.Success(extracted, partialFail)
        }
        if (foundMarker && failureReason != null) return ToolParseResult.Failure(failureSnippet ?: "", failureReason)

        // 3. Check for ReAct style: Action: <tool_name>\nAction Input: <json/str>
        val reactPattern = Pattern.compile("Action:\\s*([a-zA-Z0-9_]+)\\s*\\n+Action Input:\\s*([\\s\\S]+?)(?:\\n\\n|$)", Pattern.CASE_INSENSITIVE)
        val reactMatcher = reactPattern.matcher(content)
        while (reactMatcher.find()) {
            foundMarker = true
            val toolName = reactMatcher.group(1)?.trim() ?: continue
            val inputStr = reactMatcher.group(2)?.trim() ?: "{}"
            val argsJson = if (inputStr.startsWith("{") && inputStr.endsWith("}")) {
                inputStr
            } else {
                JSONObject().put("command", inputStr).toString()
            }
            extracted.add(
                LlmToolCall(
                    id = "call_react_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                    name = toolName,
                    argumentsJson = normalizeJsonArguments(argsJson)
                )
            )
        }
        if (extracted.isNotEmpty()) return ToolParseResult.Success(extracted)

        // 4. Check if the content is raw JSON representing a tool call
        val trimmed = content.trim()
        val isRawJsonTool = (trimmed.startsWith("{") || trimmed.startsWith("[")) &&
            (trimmed.contains("\"name\"") || trimmed.contains("\"tool\"") || trimmed.contains("\"function\""))
        if (isRawJsonTool) {
            foundMarker = true
            val calls = parseJsonToolBlocks(trimmed)
            if (calls.isNotEmpty()) {
                return ToolParseResult.Success(calls)
            } else {
                return ToolParseResult.Failure(trimmed.take(300), "Malformed or truncated raw JSON tool call")
            }
        }

        // Incomplete / truncated XML or tool tags at the end of content
        if (trimmed.contains("<tool_call>") || trimmed.contains("<invoke ") || trimmed.contains("<function=")) {
            return ToolParseResult.Failure(trimmed.takeLast(300), "Truncated XML tool tag")
        }

        return ToolParseResult.NoToolCall
    }

    /**
     * Parses structured tool calls from text content (XML tags, Markdown codeblocks, ReAct formats, raw JSON).
     */
    fun parseFromContent(content: String): List<LlmToolCall> {
        return when (val res = parse(content)) {
            is ToolParseResult.Success -> res.calls
            else -> emptyList()
        }
    }

    /**
     * Parses a JSON string which may be a single tool-call object or an array of tool-call objects.
     */
    fun parseJsonToolBlocks(jsonText: String): List<LlmToolCall> {
        val trimmed = jsonText.trim()
        val results = mutableListOf<LlmToolCall>()

        // Check if array format: [ { "name": "...", "arguments": { ... } }, ... ]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val jsonArr = runCatching { JSONArray(trimmed) }.getOrNull()
            if (jsonArr != null) {
                for (i in 0 until jsonArr.length()) {
                    val item = jsonArr.optJSONObject(i) ?: continue
                    parseJsonToolObject(item)?.let { results.add(it) }
                }
                if (results.isNotEmpty()) return results
            }
        }

        // Single object format
        val jsonObj = runCatching { JSONObject(trimmed) }.getOrNull()
        if (jsonObj != null) {
            parseJsonToolObject(jsonObj)?.let { results.add(it) }
        }

        return results
    }

    private fun parseJsonToolObject(json: JSONObject): LlmToolCall? {
        val name = json.optString("name", "").ifBlank {
            json.optString("tool", "").ifBlank {
                json.optString("function", "")
            }
        }.trim()

        if (name.isBlank()) return null

        val argsObj = json.opt("arguments") ?: json.opt("parameters") ?: json.opt("input")
        val argsStr = when (argsObj) {
            is JSONObject -> argsObj.toString()
            is String -> argsObj.ifBlank { "{}" }
            else -> {
                // If the root object itself has parameters directly without 'arguments' wrapper
                val clone = JSONObject(json.toString())
                clone.remove("name")
                clone.remove("tool")
                clone.remove("function")
                clone.remove("id")
                clone.remove("type")
                if (clone.length() > 0) clone.toString() else "{}"
            }
        }

        val id = json.optString("id", "").ifBlank { "call_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}" }

        return LlmToolCall(
            id = id,
            name = name,
            argumentsJson = normalizeJsonArguments(argsStr)
        )
    }

    private fun normalizeJsonArguments(args: String): String {
        var clean = args.trim()
        if (clean.isBlank()) return "{}"

        // Strip code fence if model wrapped inside arguments string
        if (clean.startsWith("```") && clean.endsWith("```")) {
            clean = clean.substringAfter("\n").substringBeforeLast("```").trim()
        }

        // Unwrap double-quoted / escaped JSON string (e.g. "\"{\\\"cmd\\\": \\\"ls\\\"}\"")
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length > 2) {
            val unquoted = runCatching {
                val arr = JSONArray("[$clean]")
                arr.getString(0)
            }.getOrNull()
            if (unquoted != null && unquoted.trim().startsWith("{")) {
                clean = unquoted.trim()
            }
        }

        return runCatching {
            val parsed = JSONObject(clean)
            parsed.toString()
        }.getOrElse {
            // Preserve raw content if it appears to be a JSON object so dispatcher can report exact syntax error
            if (clean.startsWith("{") && clean.endsWith("}")) {
                clean
            } else {
                "{}"
            }
        }
    }

    /**
     * Detects if the model's text response claims to have executed or generated deliverables
     * purely in conversational prose without an actual tool call.
     */
    fun isClaimingExecutionWithoutToolCall(content: String): Boolean {
        if (content.isBlank()) return false
        val lower = content.lowercase()
        val claimsAction = lower.contains("i have created") ||
                lower.contains("i created") ||
                lower.contains("i have written") ||
                lower.contains("i wrote") ||
                lower.contains("i have executed") ||
                lower.contains("i executed") ||
                lower.contains("i ran the command") ||
                lower.contains("file created:") ||
                lower.contains("created file:") ||
                lower.contains("successfully generated")
        return claimsAction
    }

    /**
     * Determines whether a tool call ID is synthetic, generic, or static (e.g. call_1, tool_call_1, default),
     * rather than a provider-native high-entropy identifier (e.g. call_p8dF719vK..., toolu_01...).
     */
    fun isSyntheticOrGenericId(id: String): Boolean {
        if (id.isBlank()) return true
        val trimmed = id.trim()
        if (trimmed.length < 10) return true

        val lower = trimmed.lowercase()
        val genericPlaceholders = setOf(
            "default", "null", "undefined", "none", "call", "tool_call", "tool",
            "call_default", "tool_default", "call_null", "call_undefined",
            "call_run_command", "call_tool", "tool_call_1", "tool_call_0", "call_0", "call_1",
            "function_call", "action_input", "chatcmpl_tool_call"
        )
        if (lower in genericPlaceholders) return true

        // Sequential numbers or arbitrary-length digit counters like call_1, tool_call_00000001
        if (lower.matches(Regex("^(call_|tool_call_|tool_|action_|func_)?[0-9]+$"))) return true

        // Pure word identifiers without digits like call_execute_command, call_file_read
        if (lower.matches(Regex("^(call_|tool_call_|tool_|action_|func_)?[a-z_]+$")) && !lower.matches(Regex(".*[0-9].*"))) {
            return true
        }

        return false
    }
}

