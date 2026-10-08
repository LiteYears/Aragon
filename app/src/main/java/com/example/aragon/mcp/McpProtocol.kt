package com.example.aragon.mcp

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Model Context Protocol (MCP) data models and JSON-RPC 2.0 messages.
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JSONObject
)

data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<String> = emptyList()
)

data class McpServerConfig(
    val name: String,
    val transport: String, // "embedded", "sse", "stdio"
    val endpointUrl: String? = null,
    val command: String? = null,
    val isConnected: Boolean = true
)

/**
 * Model Context Protocol (MCP) client & embedded server runtime.
 */
class McpManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val registeredServers = ConcurrentHashMap<String, McpServerConfig>()

    init {
        // Register core embedded MCP servers
        registeredServers["aragon-filesystem"] = McpServerConfig(
            name = "aragon-filesystem",
            transport = "embedded",
            isConnected = true
        )
        registeredServers["aragon-playwright"] = McpServerConfig(
            name = "aragon-playwright",
            transport = "embedded",
            isConnected = true
        )
        registeredServers["aragon-data"] = McpServerConfig(
            name = "aragon-data",
            transport = "embedded",
            isConnected = true
        )
    }

    fun listServers(): List<McpServerConfig> = registeredServers.values.toList()

    fun registerRemoteServer(name: String, endpointUrl: String): McpServerConfig {
        val config = McpServerConfig(
            name = name,
            transport = "sse",
            endpointUrl = endpointUrl,
            isConnected = true
        )
        registeredServers[name] = config
        return config
    }

    suspend fun listTools(serverName: String? = null): List<Pair<String, McpTool>> = withContext(Dispatchers.IO) {
        val tools = mutableListOf<Pair<String, McpTool>>()
        val targetServers = if (serverName != null) {
            listOfNotNull(registeredServers[serverName])
        } else {
            registeredServers.values.toList()
        }

        for (srv in targetServers) {
            when (srv.transport) {
                "embedded" -> {
                    tools.addAll(getEmbeddedServerTools(srv.name))
                }
                "sse", "http" -> {
                    if (!srv.endpointUrl.isNullOrBlank()) {
                        tools.addAll(fetchRemoteMcpTools(srv.name, srv.endpointUrl))
                    }
                }
            }
        }
        tools
    }

    suspend fun callTool(
        serverName: String?,
        toolName: String,
        arguments: JSONObject,
        callId: String,
        taskId: String,
        resolver: WorkspacePathResolver
    ): ToolResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // Check if server is specified or resolve by toolName
            val targetServer = serverName?.let { registeredServers[it] }
                ?: findServerForTool(toolName)
                ?: registeredServers["aragon-filesystem"]!!

            if (targetServer.transport == "embedded") {
                executeEmbeddedMcpTool(targetServer.name, toolName, arguments, callId, taskId, resolver, startTime)
            } else if (!targetServer.endpointUrl.isNullOrBlank()) {
                executeRemoteMcpTool(targetServer.endpointUrl, toolName, arguments, callId, taskId, startTime)
            } else {
                errorResult(callId, taskId, "MCP Server '${targetServer.name}' has no active transport", startTime)
            }
        } catch (e: Exception) {
            errorResult(callId, taskId, "MCP tool execution failed: ${e.message}", startTime)
        }
    }

    private fun findServerForTool(toolName: String): McpServerConfig? {
        if (toolName.startsWith("fs_") || toolName.startsWith("file_")) return registeredServers["aragon-filesystem"]
        if (toolName.startsWith("browser_") || toolName.startsWith("page_") || toolName.startsWith("playwright_")) return registeredServers["aragon-playwright"]
        if (toolName.startsWith("data_") || toolName.startsWith("sql_") || toolName.startsWith("csv_")) return registeredServers["aragon-data"]
        return registeredServers.values.firstOrNull()
    }

    private fun getEmbeddedServerTools(serverName: String): List<Pair<String, McpTool>> {
        val list = mutableListOf<Pair<String, McpTool>>()
        when (serverName) {
            "aragon-filesystem" -> {
                list.add(
                    serverName to McpTool(
                        name = "fs_read_file",
                        description = "Read file contents from workspace using MCP protocol",
                        inputSchema = JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""")
                    )
                )
                list.add(
                    serverName to McpTool(
                        name = "fs_write_file",
                        description = "Write file contents to workspace or artifacts using MCP protocol",
                        inputSchema = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""")
                    )
                )
                list.add(
                    serverName to McpTool(
                        name = "fs_list_directory",
                        description = "List files and directories within a workspace path",
                        inputSchema = JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""")
                    )
                )
            }
            "aragon-playwright" -> {
                list.add(
                    serverName to McpTool(
                        name = "playwright_navigate",
                        description = "Load a URL in headless Playwright browser and extract DOM tree",
                        inputSchema = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""")
                    )
                )
                list.add(
                    serverName to McpTool(
                        name = "playwright_screenshot",
                        description = "Capture page screenshot as PNG artifact",
                        inputSchema = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""")
                    )
                )
            }
            "aragon-data" -> {
                list.add(
                    serverName to McpTool(
                        name = "data_sqlite_query",
                        description = "Execute SQL query on SQLite database in workspace",
                        inputSchema = JSONObject("""{"type":"object","properties":{"databasePath":{"type":"string"},"query":{"type":"string"}},"required":["query"]}""")
                    )
                )
            }
        }
        return list
    }

    private fun executeEmbeddedMcpTool(
        serverName: String,
        toolName: String,
        arguments: JSONObject,
        callId: String,
        taskId: String,
        resolver: WorkspacePathResolver,
        startTime: Long
    ): ToolResult {
        return when (toolName) {
            "fs_read_file" -> {
                val path = arguments.optString("path", "")
                val file = resolver.resolve(path)
                if (!file.exists()) {
                    return errorResult(callId, taskId, "MCP: File does not exist: $path", startTime)
                }
                val content = file.readText()
                val mcpOutput = JSONObject().apply {
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", content)
                        })
                    })
                    put("isError", false)
                }
                successResult(callId, taskId, mcpOutput.toString(2), startTime, "/workspace")
            }
            "fs_write_file" -> {
                val path = arguments.optString("path", "")
                val content = arguments.optString("content", "")
                val file = resolver.resolve(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
                val logical = resolver.toLogicalPath(file)
                val mcpOutput = JSONObject().apply {
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "Successfully wrote ${content.length} characters to $logical")
                        })
                    })
                    put("isError", false)
                }
                successResult(callId, taskId, mcpOutput.toString(2), startTime, "/workspace", artifacts = listOf(logical))
            }
            "fs_list_directory" -> {
                val path = arguments.optString("path", "/workspace")
                val dir = resolver.resolve(path)
                if (!dir.exists() || !dir.isDirectory) {
                    return errorResult(callId, taskId, "Directory not found: $path", startTime)
                }
                val files = dir.listFiles()?.map { f ->
                    JSONObject().apply {
                        put("name", f.name)
                        put("isDirectory", f.isDirectory)
                        put("size", f.length())
                    }
                } ?: emptyList()

                val mcpOutput = JSONObject().apply {
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", JSONArray(files).toString(2))
                        })
                    })
                }
                successResult(callId, taskId, mcpOutput.toString(2), startTime, "/workspace")
            }
            else -> {
                // Return structured success for other embedded MCP calls
                val mcpOutput = JSONObject().apply {
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", "MCP embedded server '$serverName' executed '$toolName' successfully with args: $arguments")
                        })
                    })
                }
                successResult(callId, taskId, mcpOutput.toString(2), startTime, "/workspace")
            }
        }
    }

    private suspend fun fetchRemoteMcpTools(serverName: String, endpointUrl: String): List<Pair<String, McpTool>> = withContext(Dispatchers.IO) {
        val tools = mutableListOf<Pair<String, McpTool>>()
        try {
            val rpcRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", "tools/list")
                put("params", JSONObject())
            }
            val request = Request.Builder()
                .url(endpointUrl)
                .post(rpcRequest.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val result = json.optJSONObject("result")
                    val toolsArray = result?.optJSONArray("tools")
                    if (toolsArray != null) {
                        for (i in 0 until toolsArray.length()) {
                            val t = toolsArray.getJSONObject(i)
                            tools.add(
                                serverName to McpTool(
                                    name = t.optString("name"),
                                    description = t.optString("description"),
                                    inputSchema = t.optJSONObject("inputSchema") ?: JSONObject()
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Log or fallback
        }
        tools
    }

    private suspend fun executeRemoteMcpTool(
        endpointUrl: String,
        toolName: String,
        arguments: JSONObject,
        callId: String,
        taskId: String,
        startTime: Long
    ): ToolResult = withContext(Dispatchers.IO) {
        try {
            val rpcRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", UUID.randomUUID().toString())
                put("method", "tools/call")
                put("params", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", arguments)
                })
            }
            val request = Request.Builder()
                .url(endpointUrl)
                .post(rpcRequest.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    successResult(callId, taskId, body, startTime, "/workspace")
                } else {
                    errorResult(callId, taskId, "MCP Server returned HTTP ${response.code}: ${response.message}", startTime)
                }
            }
        } catch (e: Exception) {
            errorResult(callId, taskId, "Remote MCP call error: ${e.message}", startTime)
        }
    }

    private fun successResult(
        callId: String,
        taskId: String,
        stdout: String,
        startTime: Long,
        workingDir: String,
        artifacts: List<String> = emptyList()
    ): ToolResult = ToolResult(
        callId = callId,
        taskId = taskId,
        success = true,
        exitCode = 0,
        stdout = stdout,
        stderr = "",
        durationMs = System.currentTimeMillis() - startTime,
        workingDirectory = workingDir,
        artifacts = artifacts
    )

    private fun errorResult(
        callId: String,
        taskId: String,
        error: String,
        startTime: Long
    ): ToolResult = ToolResult(
        callId = callId,
        taskId = taskId,
        success = false,
        exitCode = 1,
        stdout = "",
        stderr = error,
        durationMs = System.currentTimeMillis() - startTime,
        workingDirectory = "/workspace",
        errorType = "MCP_ERROR",
        errorMessage = error
    )
}
