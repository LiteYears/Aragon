package com.example.aragon.agent

import java.io.File

sealed interface Observation {
    val timestamp: Long
    val summary: String
    val fullOutputRef: String?

    data class Terminal(
        override val timestamp: Long = System.currentTimeMillis(),
        val command: String,
        val exitCode: Int,
        val stdoutSnippet: String,
        val stderrSnippet: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class FileOps(
        override val timestamp: Long = System.currentTimeMillis(),
        val operation: String,
        val logicalPath: String,
        val success: Boolean,
        val details: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class Browser(
        override val timestamp: Long = System.currentTimeMillis(),
        val action: String,
        val url: String,
        val title: String,
        val extractedTextSnippet: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class Process(
        override val timestamp: Long = System.currentTimeMillis(),
        val processId: Long,
        val status: String,
        val cpuUsagePercent: Float,
        val memoryUsageMb: Long,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class ArtifactObs(
        override val timestamp: Long = System.currentTimeMillis(),
        val filename: String,
        val logicalPath: String,
        val mimeType: String,
        val sizeBytes: Long,
        val isValid: Boolean,
        val stage: String,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation

    data class Environment(
        override val timestamp: Long = System.currentTimeMillis(),
        val isUbuntuReady: Boolean,
        val isPythonAvailable: Boolean,
        val isNetworkAvailable: Boolean,
        val freeStorageMb: Long,
        override val summary: String,
        override val fullOutputRef: String? = null
    ) : Observation
}
