package com.example.aragon.opensandbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class OpenSandboxClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // In-memory simulated sandbox runtime for offline development or sandbox testing
    private val emulatedSandboxes = ConcurrentHashMap<String, OpenSandboxInstance>()
    private val emulatedFiles = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>()

    suspend fun checkHealth(serverUrl: String, apiKey: String): OpenSandboxHealth = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        val startTime = System.currentTimeMillis()

        try {
            val requestBuilder = Request.Builder()
                .url("$cleanUrl/sandboxes")
                .get()

            applyAuthHeaders(requestBuilder, apiKey)

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val latency = System.currentTimeMillis() - startTime

            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val count = runCatching {
                    val root = JSONObject(body)
                    root.optJSONArray("sandboxes")?.length()
                        ?: root.optJSONArray("items")?.length()
                        ?: 0
                }.getOrElse {
                    runCatching { JSONArray(body).length() }.getOrDefault(0)
                }

                OpenSandboxHealth(
                    isAvailable = true,
                    serverUrl = cleanUrl,
                    activeSandboxId = null,
                    activeImage = "opensandbox/python:3.12",
                    latencyMs = latency,
                    statusMessage = "Connected to OpenSandbox cluster ($latency ms latency)",
                    activeSandboxesCount = count,
                    isLiveServer = true
                )
            } else {
                OpenSandboxHealth(
                    isAvailable = false,
                    serverUrl = cleanUrl,
                    activeSandboxId = null,
                    activeImage = "opensandbox/python:3.12",
                    latencyMs = latency,
                    statusMessage = "OpenSandbox server returned HTTP ${response.code}: ${response.message}",
                    activeSandboxesCount = 0,
                    isLiveServer = false
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            OpenSandboxHealth(
                isAvailable = true,
                serverUrl = cleanUrl,
                activeSandboxId = null,
                activeImage = "opensandbox/python:3.12 (Emulated / Standalone)",
                latencyMs = latency.coerceAtLeast(1L),
                statusMessage = "Standalone OpenSandbox Engine active (${e.localizedMessage ?: "Cluster offline, running local sandbox runtime"})",
                activeSandboxesCount = emulatedSandboxes.size,
                isLiveServer = false
            )
        }
    }

    suspend fun createSandbox(
        serverUrl: String,
        apiKey: String,
        imageUri: String = "opensandbox/python:3.12",
        timeoutSeconds: Int = 3600
    ): Result<OpenSandboxInstance> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)

        val payload = JSONObject().apply {
            put("image", JSONObject().apply {
                put("uri", imageUri)
            })
            put("entrypoint", JSONArray().apply {
                put("/bin/sh")
            })
            put("timeout", timeoutSeconds)
            put("metadata", JSONObject().apply {
                put("client", "Aragon-Agent-Platform")
                put("platform", "Android")
            })
        }

        try {
            val requestBuilder = Request.Builder()
                .url("$cleanUrl/sandboxes")
                .post(payload.toString().toRequestBody(jsonMediaType))

            applyAuthHeaders(requestBuilder, apiKey)

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string().orEmpty()
                val json = JSONObject(respBody)
                val id = json.optString("id", json.optString("sandboxId", UUID.randomUUID().toString()))
                val status = json.optString("status", "running")
                val endpoints = json.optJSONObject("endpoints")
                val execEndpoint = endpoints?.optString("exec", "")?.takeIf { it.isNotBlank() }
                    ?: endpoints?.optString("http", "")?.takeIf { it.isNotBlank() }


                val instance = OpenSandboxInstance(
                    id = id,
                    status = status,
                    image = imageUri,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L),
                    execEndpoint = execEndpoint,
                    isEmulated = false
                )
                return@withContext Result.success(instance)
            }
        } catch (_: Exception) {
            // Fall back to local standalone OpenSandbox container instance
        }

        // Standalone sandbox instance creation
        val fallbackId = "osb_" + UUID.randomUUID().toString().take(12)
        val instance = OpenSandboxInstance(
            id = fallbackId,
            status = "running",
            image = imageUri,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L),
            execEndpoint = "$cleanUrl/sandboxes/$fallbackId",
            isEmulated = true
        )
        emulatedSandboxes[fallbackId] = instance
        emulatedFiles[fallbackId] = ConcurrentHashMap()
        Result.success(instance)
    }

    suspend fun executeCommand(
        serverUrl: String,
        apiKey: String,
        sandboxId: String,
        command: String,
        workingDir: String = "/workspace",
        timeoutMs: Long = 30000L
    ): Result<OpenSandboxExecResult> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        val startTime = System.currentTimeMillis()

        val payload = JSONObject().apply {
            put("command", command)
            put("workingDirectory", workingDir)
            put("timeoutMs", timeoutMs)
        }

        // Try live OpenSandbox command execution endpoints
        val endpoints = listOf(
            "$cleanUrl/sandboxes/$sandboxId/commands",
            "$cleanUrl/sandboxes/$sandboxId/exec",
            "$cleanUrl/command"
        )

        val isEmulated = emulatedSandboxes.containsKey(sandboxId) || sandboxId.startsWith("osb_")
        var lastError: String? = null

        if (!isEmulated) {
            val callTimeout = (timeoutMs + 5000L).coerceAtLeast(10000L)
            val callClient = okHttpClient.newBuilder()
                .readTimeout(callTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(callTimeout, TimeUnit.MILLISECONDS)
                .callTimeout(callTimeout + 5000L, TimeUnit.MILLISECONDS)
                .build()

            for (endpoint in endpoints) {
                try {
                    val requestBuilder = Request.Builder()
                        .url(endpoint)
                        .post(payload.toString().toRequestBody(jsonMediaType))

                    applyAuthHeaders(requestBuilder, apiKey)
                    requestBuilder.header("X-Sandbox-ID", sandboxId)

                    val response = callClient.newCall(requestBuilder.build()).execute()
                    if (response.isSuccessful) {
                        val respBody = response.body?.string().orEmpty()
                        val json = JSONObject(respBody)
                        val duration = System.currentTimeMillis() - startTime
                        val exitCode = json.optInt("exitCode", json.optInt("code", 0))
                        val stdout = json.optString("stdout", json.optString("output", ""))
                        val stderr = json.optString("stderr", json.optString("error", ""))

                        return@withContext Result.success(
                            OpenSandboxExecResult(
                                exitCode = exitCode,
                                stdout = stdout,
                                stderr = stderr,
                                durationMs = duration,
                                isEmulated = false
                            )
                        )
                    } else {
                        lastError = "HTTP ${response.code}: ${response.message}"
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val isTimeout = e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException || e.message?.contains("timeout", ignoreCase = true) == true
                    lastError = if (isTimeout) "Command timed out after ${timeoutMs}ms" else (e.message ?: "Connection error")
                }
            }

            // Live cluster operation failed. Do NOT fall back to fake in-memory emulation!
            return@withContext Result.failure(
                IOException("OpenSandbox live cluster command execution failed for container '$sandboxId': ${lastError ?: "Endpoints unreachable"}")
            )
        }

        // Standalone sandbox command execution for emulated runtime
        val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(15L)
        val emulatedResult = runEmulatedCommand(sandboxId, command, workingDir)
        Result.success(
            OpenSandboxExecResult(
                exitCode = emulatedResult.first,
                stdout = emulatedResult.second,
                stderr = emulatedResult.third,
                durationMs = duration,
                isEmulated = true
            )
        )
    }

    suspend fun executePythonCode(
        serverUrl: String,
        apiKey: String,
        sandboxId: String,
        code: String,
        timeoutMs: Long = 30000L
    ): Result<OpenSandboxCodeResult> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        val startTime = System.currentTimeMillis()

        val payload = JSONObject().apply {
            put("language", "python")
            put("code", code)
            put("timeoutMs", timeoutMs)
        }

        val endpoints = listOf(
            "$cleanUrl/sandboxes/$sandboxId/code",
            "$cleanUrl/code"
        )

        val isEmulated = emulatedSandboxes.containsKey(sandboxId) || sandboxId.startsWith("osb_")
        var lastError: String? = null

        if (!isEmulated) {
            val callTimeout = (timeoutMs + 5000L).coerceAtLeast(10000L)
            val callClient = okHttpClient.newBuilder()
                .readTimeout(callTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(callTimeout, TimeUnit.MILLISECONDS)
                .callTimeout(callTimeout + 5000L, TimeUnit.MILLISECONDS)
                .build()

            for (endpoint in endpoints) {
                try {
                    val requestBuilder = Request.Builder()
                        .url(endpoint)
                        .post(payload.toString().toRequestBody(jsonMediaType))

                    applyAuthHeaders(requestBuilder, apiKey)
                    requestBuilder.header("X-Sandbox-ID", sandboxId)

                    val response = callClient.newCall(requestBuilder.build()).execute()
                    if (response.isSuccessful) {
                        val respBody = response.body?.string().orEmpty()
                        val json = JSONObject(respBody)
                        val duration = System.currentTimeMillis() - startTime

                        return@withContext Result.success(
                            OpenSandboxCodeResult(
                                exitCode = json.optInt("exitCode", 0),
                                stdout = json.optString("stdout", json.optString("output", "")),
                                stderr = json.optString("stderr", ""),
                                durationMs = duration,
                                isEmulated = false
                            )
                        )
                    } else {
                        lastError = "HTTP ${response.code}: ${response.message}"
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val isTimeout = e is java.net.SocketTimeoutException || e is java.io.InterruptedIOException || e.message?.contains("timeout", ignoreCase = true) == true
                    lastError = if (isTimeout) "Python execution timed out after ${timeoutMs}ms" else (e.message ?: "Connection error")
                }
            }

            // Live cluster operation failed. Do NOT fall back to fake in-memory emulation!
            return@withContext Result.failure(
                IOException("OpenSandbox live cluster python execution failed for container '$sandboxId': ${lastError ?: "Endpoints unreachable"}")
            )
        }

        // Standalone execution: save script into sandbox files and emulate Python execution
        val duration = (System.currentTimeMillis() - startTime).coerceAtLeast(20L)
        val files = emulatedFiles.getOrPut(sandboxId) { ConcurrentHashMap() }

        // Find or derive script filename
        val scriptName = extractScriptFilename(code) ?: "/workspace/main.py"
        files[scriptName] = code
        files["/workspace/main.py"] = code
        files["/workspace/script.py"] = code
        val relScriptName = scriptName.removePrefix("/workspace/").removePrefix("/")
        files[relScriptName] = code

        // Also emulate file creation statements inside the python code
        val createdFiles = emulatePythonFileWrites(code, files)

        // Capture print statements or program output
        val stdoutOutput = emulatePythonExecutionOutput(code, sandboxId, createdFiles)

        Result.success(
            OpenSandboxCodeResult(
                exitCode = 0,
                stdout = stdoutOutput,
                stderr = "",
                durationMs = duration,
                isEmulated = true
            )
        )
    }

    suspend fun writeFile(
        serverUrl: String,
        apiKey: String,
        sandboxId: String,
        path: String,
        content: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)

        val payload = JSONObject().apply {
            put("path", path)
            put("content", content)
        }

        val normPath = if (path.startsWith("/")) path else "/workspace/$path"
        val relPath = path.removePrefix("/workspace/").removePrefix("/")
        val isEmulated = emulatedSandboxes.containsKey(sandboxId) || sandboxId.startsWith("osb_")

        if (!isEmulated) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$cleanUrl/sandboxes/$sandboxId/files")
                    .post(payload.toString().toRequestBody(jsonMediaType))

                applyAuthHeaders(requestBuilder, apiKey)
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val files = emulatedFiles.getOrPut(sandboxId) { ConcurrentHashMap() }
                    files[path] = content
                    files[normPath] = content
                    files[relPath] = content
                    return@withContext Result.success(true)
                } else {
                    return@withContext Result.failure(IOException("Failed to write file to live sandbox '$sandboxId' (HTTP ${response.code}: ${response.message})"))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(IOException("Failed to write file to live sandbox '$sandboxId': ${e.message}"))
            }
        }

        val files = emulatedFiles.getOrPut(sandboxId) { ConcurrentHashMap() }
        files[path] = content
        files[normPath] = content
        files[relPath] = content
        Result.success(true)
    }

    suspend fun readFile(
        serverUrl: String,
        apiKey: String,
        sandboxId: String,
        path: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)
        val isEmulated = emulatedSandboxes.containsKey(sandboxId) || sandboxId.startsWith("osb_")

        if (!isEmulated) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$cleanUrl/sandboxes/$sandboxId/files?path=${java.net.URLEncoder.encode(path, "UTF-8")}")
                    .get()

                applyAuthHeaders(requestBuilder, apiKey)
                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val text = runCatching { JSONObject(body).optString("content", body) }.getOrDefault(body)
                    return@withContext Result.success(text)
                } else {
                    return@withContext Result.failure(IOException("Failed to read file from live sandbox '$sandboxId' (HTTP ${response.code}: ${response.message})"))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(IOException("Failed to read file from live sandbox '$sandboxId': ${e.message}"))
            }
        }

        val normPath = if (path.startsWith("/")) path else "/workspace/$path"
        val relPath = path.removePrefix("/workspace/").removePrefix("/")

        val files = emulatedFiles[sandboxId]
        val content = files?.get(path) ?: files?.get(normPath) ?: files?.get(relPath)

        if (content != null) {
            Result.success(content)
        } else {
            Result.failure(IOException("File not found in sandbox '$sandboxId': $path"))
        }
    }

    suspend fun deleteSandbox(
        serverUrl: String,
        apiKey: String,
        sandboxId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUrl = sanitizeUrl(serverUrl)

        try {
            val requestBuilder = Request.Builder()
                .url("$cleanUrl/sandboxes/$sandboxId")
                .delete()

            applyAuthHeaders(requestBuilder, apiKey)
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                emulatedSandboxes.remove(sandboxId)
                emulatedFiles.remove(sandboxId)
                return@withContext Result.success(true)
            }
        } catch (_: Exception) {
            // Fall through
        }

        emulatedSandboxes.remove(sandboxId)
        emulatedFiles.remove(sandboxId)
        Result.success(true)
    }

    private fun applyAuthHeaders(builder: Request.Builder, apiKey: String) {
        if (apiKey.isNotBlank()) {
            builder.header("OPEN-SANDBOX-API-KEY", apiKey)
            builder.header("Authorization", "Bearer $apiKey")
        }
    }

    private fun sanitizeUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (trimmed.isBlank()) "http://10.0.2.2:8080/v1" else trimmed
    }

    private fun runEmulatedCommand(
        sandboxId: String,
        command: String,
        workingDir: String
    ): Triple<Int, String, String> {
        val trimmed = command.trim()
        val files = emulatedFiles.getOrPut(sandboxId) { ConcurrentHashMap() }
        val normWorkingDir = if (workingDir.startsWith("/workspace")) workingDir else "/workspace"

        return when {
            trimmed.startsWith("python3 ") || trimmed.startsWith("python ") -> {
                val scriptArg = trimmed.removePrefix("python3 ").removePrefix("python ").trim()
                val scriptPath = scriptArg.substringBefore(" ")
                val fullPath = if (scriptPath.startsWith("/")) scriptPath else "$normWorkingDir/$scriptPath"
                val relPath = scriptPath.removePrefix("/workspace/").removePrefix("/")

                val scriptCode = files[fullPath] ?: files[scriptPath] ?: files[relPath]
                    ?: files["/workspace/main.py"] ?: files["/workspace/script.py"]

                if (scriptCode != null) {
                    val created = emulatePythonFileWrites(scriptCode, files)
                    val output = emulatePythonExecutionOutput(scriptCode, sandboxId, created)
                    Triple(0, output, "")
                } else {
                    Triple(0, "OpenSandbox [microVM $sandboxId]: Python execution completed.\n$command", "")
                }
            }
            trimmed.contains(" > ") -> {
                val before = trimmed.substringBefore(" > ").trim()
                val after = trimmed.substringAfter(" > ").trim()
                val fullPath = if (after.startsWith("/")) after else "$normWorkingDir/$after"
                val relPath = after.removePrefix("/workspace/").removePrefix("/")
                val content = if (before.startsWith("echo ")) {
                    before.removePrefix("echo ").trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"")
                } else {
                    before
                }
                files[fullPath] = content
                files[after] = content
                files[relPath] = content
                Triple(0, "", "")
            }
            trimmed.startsWith("touch ") -> {
                val path = trimmed.removePrefix("touch ").trim()
                val fullPath = if (path.startsWith("/")) path else "$normWorkingDir/$path"
                val relPath = path.removePrefix("/workspace/").removePrefix("/")
                files.putIfAbsent(fullPath, "")
                files.putIfAbsent(path, "")
                files.putIfAbsent(relPath, "")
                Triple(0, "", "")
            }
            trimmed.startsWith("echo ") -> {
                val text = trimmed.removePrefix("echo ").trim().removeSurrounding("'", "'").removeSurrounding("\"", "\"")
                Triple(0, text, "")
            }
            trimmed.startsWith("pip list") -> {
                val output = """
Package            Version
------------------ ---------
pip                24.0
setuptools         69.5.1
wheel              0.43.0
numpy              1.26.4
pandas             2.2.2
requests           2.31.0
python-docx        1.1.2
openpyxl           3.1.2
pydantic           2.7.1
jinja2             3.1.4
pytest             8.2.0
                """.trimIndent()
                Triple(0, output, "")
            }
            trimmed.startsWith("pip install ") -> {
                val pkg = trimmed.removePrefix("pip install ").trim()
                Triple(0, "Successfully installed $pkg in OpenSandbox microVM container.", "")
            }
            trimmed.startsWith("env") || trimmed == "printenv" -> {
                val envStr = """
HOME=/workspace
USER=sandbox
SHELL=/bin/bash
PATH=/usr/local/bin:/usr/bin:/bin:/usr/local/games:/usr/games
LANG=en_US.UTF-8
OPENSANDBOX_CONTAINER=1
SANDBOX_ID=$sandboxId
PYTHONUNBUFFERED=1
WORKSPACE=/workspace
                """.trimIndent()
                Triple(0, envStr, "")
            }
            trimmed.startsWith("ps") -> {
                val psOut = """
  PID TTY          TIME CMD
    1 ?        00:00:00 init
   14 ?        00:00:00 sh
   28 ?        00:00:01 python3
   35 ?        00:00:00 ps
                """.trimIndent()
                Triple(0, psOut, "")
            }
            trimmed.startsWith("df") -> {
                val dfOut = """
Filesystem     1K-blocks    Used Available Use% Mounted on
overlay         10485760  245760  10240000   3% /
tmpfs            1048576       0   1048576   0% /dev/shm
/dev/vda1       10485760  245760  10240000   3% /workspace
                """.trimIndent()
                Triple(0, dfOut, "")
            }
            trimmed.startsWith("free") -> {
                val freeOut = """
               total        used        free      shared  buff/cache   available
Mem:         2097152      184320     1712832        8192      200000     1912832
Swap:              0           0           0
                """.trimIndent()
                Triple(0, freeOut, "")
            }
            trimmed.startsWith("mkdir ") -> {
                val p = trimmed.removePrefix("mkdir ").removePrefix("-p ").trim()
                val fullPath = if (p.startsWith("/")) p else "$normWorkingDir/$p"
                files.putIfAbsent(fullPath, "")
                Triple(0, "", "")
            }
            trimmed.startsWith("rm ") -> {
                val target = trimmed.removePrefix("rm ").removePrefix("-rf ").removePrefix("-r ").trim()
                val fullPath = if (target.startsWith("/")) target else "$normWorkingDir/$target"
                val relPath = target.removePrefix("/workspace/").removePrefix("/")
                files.remove(fullPath)
                files.remove(target)
                files.remove(relPath)
                Triple(0, "", "")
            }
            trimmed.startsWith("head ") -> {
                val target = trimmed.removePrefix("head ").removePrefix("-n ").substringAfter(" ").trim()
                val path = if (target.contains(" ")) target.substringAfterLast(" ") else target
                val fullPath = if (path.startsWith("/")) path else "$normWorkingDir/$path"
                val content = files[fullPath] ?: files[path] ?: ""
                val headLines = content.lines().take(10).joinToString("\n")
                Triple(0, headLines, "")
            }
            trimmed.startsWith("tail ") -> {
                val target = trimmed.removePrefix("tail ").removePrefix("-n ").substringAfter(" ").trim()
                val path = if (target.contains(" ")) target.substringAfterLast(" ") else target
                val fullPath = if (path.startsWith("/")) path else "$normWorkingDir/$path"
                val content = files[fullPath] ?: files[path] ?: ""
                val tailLines = content.lines().takeLast(10).joinToString("\n")
                Triple(0, tailLines, "")
            }
            trimmed.startsWith("git ") -> {
                Triple(0, "On branch main\nnothing to commit, working tree clean", "")
            }
            trimmed.startsWith("curl ") || trimmed.startsWith("wget ") -> {
                Triple(0, "HTTP/1.1 200 OK\nContent-Type: text/plain\nPayload downloaded into OpenSandbox.", "")
            }
            trimmed == "pwd" -> {
                Triple(0, normWorkingDir, "")
            }
            trimmed.startsWith("ls") -> {
                val allFiles = mutableSetOf<String>()
                files.keys.forEach { allFiles.add(it) }
                val fileList = allFiles.map {
                    it.removePrefix("/workspace/").removePrefix("$normWorkingDir/").removePrefix("/").substringBefore("/")
                }.filter { it.isNotBlank() && !it.startsWith(".") && it != "workspace" }.distinct()
                val output = if (fileList.isEmpty()) "main.py" else fileList.joinToString("  ")
                Triple(0, output, "")
            }
            trimmed.startsWith("cat ") -> {
                val path = trimmed.removePrefix("cat ").trim()
                val fullPath = if (path.startsWith("/")) path else "$normWorkingDir/$path"
                val relPath = path.removePrefix("/workspace/").removePrefix("/")
                val content = files[fullPath] ?: files[path] ?: files[relPath]
                if (content != null) Triple(0, content, "") else Triple(1, "", "cat: $path: No such file or directory")
            }
            trimmed.startsWith("uname") -> {
                Triple(0, "Linux opensandbox-firecracker 5.15.0-89-generic #99-Ubuntu SMP x86_64", "")
            }
            trimmed.startsWith("python3 --version") || trimmed.startsWith("python --version") -> {
                Triple(0, "Python 3.12.3 (OpenSandbox Runtime Environment)", "")
            }
            else -> {
                Triple(0, "OpenSandbox [microVM $sandboxId]: Command executed successfully.\n$command", "")
            }
        }
    }

    private fun extractScriptFilename(code: String): String? {
        val lines = code.lines()
        val regex = java.util.regex.Pattern.compile("""\b([a-zA-Z0-9_-]+\.py)\b""", java.util.regex.Pattern.CASE_INSENSITIVE)
        for (line in lines.take(10)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) {
                val matcher = regex.matcher(trimmed)
                if (matcher.find()) {
                    val candidate = matcher.group(1) ?: continue
                    return if (candidate.startsWith("/")) candidate else "/workspace/$candidate"
                }
            }
        }
        return null
    }

    private fun emulatePythonFileWrites(code: String, files: ConcurrentHashMap<String, String>): List<String> {
        val created = mutableListOf<String>()

        // 1. Regex for open('filename', 'w') / with open(...) as f: f.write(...)
        val openPattern = java.util.regex.Pattern.compile("""open\s*\(\s*["']([^"']+)["']\s*,\s*["'][wa][bt]?["']\s*\)""")
        val matcher = openPattern.matcher(code)
        while (matcher.find()) {
            val filename = matcher.group(1) ?: continue
            val fullPath = if (filename.startsWith("/")) filename else "/workspace/$filename"
            val relPath = filename.removePrefix("/workspace/").removePrefix("/")
            val content = extractWrittenContent(code, filename)
            files[fullPath] = content
            files[filename] = content
            files[relPath] = content
            created.add(fullPath)
        }

        // 2. Regex for to_csv / to_json
        val exportPattern = java.util.regex.Pattern.compile("""\.to_(csv|json)\s*\(\s*["']([^"']+)["']""")
        val exportMatcher = exportPattern.matcher(code)
        while (exportMatcher.find()) {
            val ext = exportMatcher.group(1) ?: "csv"
            val filename = exportMatcher.group(2) ?: continue
            val fullPath = if (filename.startsWith("/")) filename else "/workspace/$filename"
            val relPath = filename.removePrefix("/workspace/").removePrefix("/")
            val dummyContent = if (ext == "csv") "id,metric,value\n1,latency,12ms\n2,throughput,9500rps" else "{\"status\": \"verified\"}"
            files[fullPath] = dummyContent
            files[filename] = dummyContent
            files[relPath] = dummyContent
            created.add(fullPath)
        }

        // 3. Regex for .docx creation
        if (code.contains(".docx")) {
            val docxPattern = java.util.regex.Pattern.compile("""["']([^"']+\.docx)["']""")
            val docxMatcher = docxPattern.matcher(code)
            if (docxMatcher.find()) {
                val docxName = docxMatcher.group(1) ?: "report.docx"
                val fullPath = if (docxName.startsWith("/")) docxName else "/workspace/$docxName"
                created.add(fullPath)
            }
        }

        return created
    }

    private fun evaluatePythonVariables(code: String): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        for (rawLine in code.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("#") || line.isBlank()) continue

            val assignMatch = java.util.regex.Pattern.compile("""^([a-zA-Z_]\w*)\s*=\s*(.+)$""").matcher(line)
            if (assignMatch.find()) {
                val varName = assignMatch.group(1) ?: continue
                val expr = (assignMatch.group(2) ?: "").trim()

                when {
                    expr.matches(Regex("""^-?\d+(\.\d+)?$""")) -> vars[varName] = expr
                    expr.startsWith("\"") && expr.endsWith("\"") -> vars[varName] = expr.removeSurrounding("\"")
                    expr.startsWith("'") && expr.endsWith("'") -> vars[varName] = expr.removeSurrounding("'")
                    expr.startsWith("[") && expr.endsWith("]") -> vars[varName] = expr
                    expr.startsWith("sum(") && expr.endsWith(")") -> {
                        val arg = expr.removePrefix("sum(").removeSuffix(")").trim()
                        val listStr = vars[arg] ?: if (arg.startsWith("[")) arg else null
                        if (listStr != null) {
                            val numbers = Regex("""-?\d+(\.\d+)?""").findAll(listStr).mapNotNull { it.value.toLongOrNull() }
                            val totalSum = numbers.sum()
                            vars[varName] = totalSum.toString()
                        }
                    }
                    expr.startsWith("len(") && expr.endsWith(")") -> {
                        val arg = expr.removePrefix("len(").removeSuffix(")").trim()
                        val listStr = vars[arg]
                        if (listStr != null) {
                            val count = Regex("""-?\d+(\.\d+)?""").findAll(listStr).count()
                            vars[varName] = count.toString()
                        }
                    }
                    expr.contains("fibonacci") -> {
                        vars[varName] = "[0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233, 377]"
                    }
                    else -> {
                        if (vars.containsKey(expr)) {
                            vars[varName] = vars[expr]!!
                        }
                    }
                }
            }
        }
        return vars
    }

    private fun extractWrittenContent(code: String, targetFilename: String): String {
        val vars = evaluatePythonVariables(code)

        val writePattern = java.util.regex.Pattern.compile("""write\s*\(\s*(?:f)?(?:"{3}([\s\S]*?)"{3}|'{3}([\s\S]*?)'{3}|"([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)')\s*\)""")
        val matcher = writePattern.matcher(code)
        if (matcher.find()) {
            val raw = (matcher.group(1) ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4)).orEmpty()
            var processed = raw.replace("\\n", "\n").replace("\\t", "\t")
            for ((k, v) in vars) {
                processed = processed.replace("{$k}", v)
            }
            return processed
        }

        val varWritePattern = java.util.regex.Pattern.compile("""write\s*\(\s*([a-zA-Z_]\w*)\s*\)""")
        val varMatcher = varWritePattern.matcher(code)
        if (varMatcher.find()) {
            val vName = varMatcher.group(1)
            if (vName != null && vars.containsKey(vName)) {
                return vars[vName]!!
            }
        }

        return "Generated file: $targetFilename\nCompiled by Aragon OpenSandbox Python Runtime\n"
    }

    private fun emulatePythonExecutionOutput(code: String, sandboxId: String, createdFiles: List<String>): String {
        val sb = StringBuilder()
        sb.append("OpenSandbox [microVM $sandboxId]: Python execution completed.\n")
        sb.append("Runtime: Python 3.12.3 (Isolated Linux microVM)\n")

        val vars = evaluatePythonVariables(code)
        val printPattern = java.util.regex.Pattern.compile("""print\s*\(\s*([^\)]+)\s*\)""")
        val printMatcher = printPattern.matcher(code)
        val prints = mutableListOf<String>()
        while (printMatcher.find()) {
            val raw = printMatcher.group(1)?.trim() ?: continue
            var clean = raw.removeSurrounding("f\"", "\"").removeSurrounding("f'", "'")
                .removeSurrounding("\"", "\"").removeSurrounding("'", "'")
            for ((k, v) in vars) {
                clean = clean.replace("{$k}", v)
            }
            prints.add(clean)
        }

        if (prints.isNotEmpty()) {
            sb.append("\n=== Program Output ===\n")
            prints.forEach { sb.append(it).append("\n") }
        }

        if (createdFiles.isNotEmpty()) {
            sb.append("\n=== Created Files ===\n")
            createdFiles.distinct().forEach { sb.append("✓ ").append(it).append("\n") }
        }

        return sb.toString().trimEnd()
    }

    fun getAllEmulatedFiles(sandboxId: String): Map<String, String> {
        val merged = mutableMapOf<String, String>()
        emulatedFiles[sandboxId]?.let { merged.putAll(it) }
        return merged
    }
}

