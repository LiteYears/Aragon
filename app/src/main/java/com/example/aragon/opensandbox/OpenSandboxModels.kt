package com.example.aragon.opensandbox

import com.example.aragon.domain.model.ExecutionBackend

data class OpenSandboxInstance(
    val id: String,
    val status: String = "running", // running, pending, stopped, terminated
    val image: String = "opensandbox/python:3.12",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val execEndpoint: String? = null,
    val isEmulated: Boolean = false
)

data class OpenSandboxExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val isEmulated: Boolean = false
)

data class OpenSandboxCodeResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val isEmulated: Boolean = false
)

data class OpenSandboxFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long
)

data class OpenSandboxHealth(
    val isAvailable: Boolean,
    val serverUrl: String,
    val activeSandboxId: String?,
    val activeImage: String,
    val latencyMs: Long,
    val statusMessage: String,
    val activeSandboxesCount: Int = 0,
    val backendType: ExecutionBackend = ExecutionBackend.OPEN_SANDBOX,
    val isLiveServer: Boolean = false
)
