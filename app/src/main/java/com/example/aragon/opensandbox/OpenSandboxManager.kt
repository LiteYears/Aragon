package com.example.aragon.opensandbox

import com.example.aragon.data.preferences.PreferencesManager
import com.example.aragon.domain.model.ExecutionBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class OpenSandboxManager(
    private val preferencesManager: PreferencesManager,
    private val client: OpenSandboxClient = OpenSandboxClient()
) {

    private val _activeSandbox = MutableStateFlow<OpenSandboxInstance?>(null)
    val activeSandbox: StateFlow<OpenSandboxInstance?> = _activeSandbox.asStateFlow()

    private val _lastHealth = MutableStateFlow<OpenSandboxHealth?>(null)
    val lastHealth: StateFlow<OpenSandboxHealth?> = _lastHealth.asStateFlow()

    suspend fun getOrEnsureSandbox(): Result<OpenSandboxInstance> = withContext(Dispatchers.IO) {
        val current = _activeSandbox.value
        if (current != null && current.status == "running") {
            return@withContext Result.success(current)
        }

        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value
        val image = preferencesManager.openSandboxImage.value

        val spawnResult = client.createSandbox(
            serverUrl = serverUrl,
            apiKey = apiKey,
            imageUri = image
        )

        spawnResult.onSuccess { instance ->
            _activeSandbox.value = instance
            preferencesManager.setOpenSandboxActiveId(instance.id)
        }

        spawnResult
    }

    suspend fun spawnSandbox(customImage: String? = null): Result<OpenSandboxInstance> = withContext(Dispatchers.IO) {
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value
        val image = customImage ?: preferencesManager.openSandboxImage.value

        val result = client.createSandbox(
            serverUrl = serverUrl,
            apiKey = apiKey,
            imageUri = image
        )

        result.onSuccess { instance ->
            _activeSandbox.value = instance
            preferencesManager.setOpenSandboxActiveId(instance.id)
        }

        result
    }

    suspend fun terminateSandbox(): Result<Boolean> = withContext(Dispatchers.IO) {
        val current = _activeSandbox.value
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value

        if (current != null) {
            val res = client.deleteSandbox(serverUrl, apiKey, current.id)
            _activeSandbox.value = null
            preferencesManager.setOpenSandboxActiveId(null)
            return@withContext res
        }

        _activeSandbox.value = null
        preferencesManager.setOpenSandboxActiveId(null)
        Result.success(true)
    }

    suspend fun executeCommand(
        command: String,
        workingDir: String = "/workspace",
        timeoutMs: Long = 30000L
    ): OpenSandboxExecResult = withContext(Dispatchers.IO) {
        val sandboxRes = getOrEnsureSandbox()
        val sandboxId = sandboxRes.getOrNull()?.id ?: "osb_default"
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value

        val execResult = client.executeCommand(
            serverUrl = serverUrl,
            apiKey = apiKey,
            sandboxId = sandboxId,
            command = command,
            workingDir = workingDir,
            timeoutMs = timeoutMs
        )

        execResult.getOrElse { err ->
            OpenSandboxExecResult(
                exitCode = 1,
                stdout = "",
                stderr = "OpenSandbox execution error: ${err.message}",
                durationMs = 0L,
                isEmulated = false
            )
        }
    }

    suspend fun executePythonCode(
        code: String,
        timeoutMs: Long = 30000L
    ): OpenSandboxCodeResult = withContext(Dispatchers.IO) {
        val sandboxRes = getOrEnsureSandbox()
        val sandboxId = sandboxRes.getOrNull()?.id ?: "osb_default"
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value

        val codeResult = client.executePythonCode(
            serverUrl = serverUrl,
            apiKey = apiKey,
            sandboxId = sandboxId,
            code = code,
            timeoutMs = timeoutMs
        )

        codeResult.getOrElse { err ->
            OpenSandboxCodeResult(
                exitCode = 1,
                stdout = "",
                stderr = "OpenSandbox code execution error: ${err.message}",
                durationMs = 0L,
                isEmulated = false
            )
        }
    }

    suspend fun writeFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val sandboxRes = getOrEnsureSandbox()
        val sandboxId = sandboxRes.getOrNull()?.id ?: "osb_default"
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value

        client.writeFile(serverUrl, apiKey, sandboxId, path, content).getOrDefault(false)
    }

    suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        val sandboxRes = getOrEnsureSandbox()
        val sandboxId = sandboxRes.getOrNull()?.id ?: "osb_default"
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value

        client.readFile(serverUrl, apiKey, sandboxId, path)
    }

    suspend fun checkHealth(): OpenSandboxHealth = withContext(Dispatchers.IO) {
        val serverUrl = preferencesManager.openSandboxServerUrl.value
        val apiKey = preferencesManager.openSandboxApiKey.value
        val health = client.checkHealth(serverUrl, apiKey)
        val fullHealth = health.copy(
            activeSandboxId = _activeSandbox.value?.id,
            activeImage = _activeSandbox.value?.image ?: preferencesManager.openSandboxImage.value,
            backendType = preferencesManager.executionBackend.value
        )
        _lastHealth.value = fullHealth
        fullHealth
    }

    suspend fun testConnection(url: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val health = client.checkHealth(url, apiKey)
        if (health.isLiveServer) {
            Result.success("Connected to OpenSandbox cluster at $url (${health.latencyMs} ms, ${health.activeSandboxesCount} active sandboxes)")
        } else {
            Result.success("OpenSandbox engine ready. ${health.statusMessage}")
        }
    }

    suspend fun syncSandboxToLocal(resolver: com.example.aragon.computer.WorkspacePathResolver): List<java.io.File> = withContext(Dispatchers.IO) {
        val sandboxId = _activeSandbox.value?.id ?: preferencesManager.openSandboxActiveId.value ?: "osb_default"
        val synced = mutableListOf<java.io.File>()
        val emulated = client.getAllEmulatedFiles(sandboxId)
        for ((logicalPath, content) in emulated) {
            val target = resolver.resolve(logicalPath)
            target.parentFile?.mkdirs()
            if (!target.exists() || target.readText() != content) {
                target.writeText(content)
                synced.add(target)
            }
        }
        synced
    }
}

