package com.example.aragon.llm

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

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
            val name = func.optString("name", "").trim()
            if (name.isBlank()) continue

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
     * Parses structured tool calls from text content (XML tags, Markdown codeblocks, ReAct formats, raw JSON).
     */
    fun parseFromContent(content: String): List<LlmToolCall> {
        if (content.isBlank()) return emptyList()

        val extracted = mutableListOf<LlmToolCall>()

        // 1. Check for XML style: <tool_call>...</tool_call> or <tool>...</tool>
        val xmlPattern = Pattern.compile("<(?:tool_call|tool|function_call)>([\\s\\S]*?)</(?:tool_call|tool|function_call)>", Pattern.CASE_INSENSITIVE)
        val xmlMatcher = xmlPattern.matcher(content)
        while (xmlMatcher.find()) {
            val block = xmlMatcher.group(1)?.trim() ?: continue
            parseJsonToolBlock(block)?.let { extracted.add(it) }
        }
        if (extracted.isNotEmpty()) return extracted

        // 2. Check for Markdown code blocks with tool_call or json
        val codeBlockPattern = Pattern.compile("```(?:tool_call|json|tool)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE)
        val codeMatcher = codeBlockPattern.matcher(content)
        while (codeMatcher.find()) {
            val block = codeMatcher.group(1)?.trim() ?: continue
            parseJsonToolBlock(block)?.let { extracted.add(it) }
        }
        if (extracted.isNotEmpty()) return extracted

        // 3. Check for ReAct style: Action: <tool_name>\nAction Input: <json/str>
        val reactPattern = Pattern.compile("Action:\\s*([a-zA-Z0-9_]+)\\s*\\n+Action Input:\\s*([\\s\\S]+?)(?:\\n\\n|$)", Pattern.CASE_INSENSITIVE)
        val reactMatcher = reactPattern.matcher(content)
        while (reactMatcher.find()) {
            val toolName = reactMatcher.group(1)?.trim() ?: continue
            val inputStr = reactMatcher.group(2)?.trim() ?: "{}"
            val argsJson = if (inputStr.startsWith("{") && inputStr.endsWith("}")) {
                inputStr
            } else {
                JSONObject().put("command", inputStr).toString()
            }
            extracted.add(
                LlmToolCall(
                    id = "call_react_${System.currentTimeMillis()}",
                    name = toolName,
                    argumentsJson = normalizeJsonArguments(argsJson)
                )
            )
        }
        if (extracted.isNotEmpty()) return extracted

        // 4. Check if the entire content is a single JSON object representing a tool call
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            parseJsonToolBlock(trimmed)?.let { extracted.add(it) }
        }

        return extracted
    }

    private fun parseJsonToolBlock(jsonText: String): LlmToolCall? {
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null

        // Format A: { "name": "run_command", "arguments": { ... } } or "arguments": "{...}"
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
                val clone = JSONObject(jsonText)
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
        return runCatching {
            val parsed = JSONObject(args)
            parsed.toString()
        }.getOrElse {
            "{}"
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
}
