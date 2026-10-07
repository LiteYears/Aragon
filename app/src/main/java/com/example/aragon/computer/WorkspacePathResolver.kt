package com.example.aragon.computer

import java.io.File

/**
 * Single authority for path translation between logical sandbox paths
 * (e.g. /workspace, /home/aragon, /artifacts, /tmp) and actual device files.
 * The model NEVER sees /data/data/...
 */
class WorkspacePathResolver(
    private val taskRootDir: File
) {
    val workspaceDir: File = File(taskRootDir, "workspace").apply { mkdirs() }
    val aragonDir: File = File(workspaceDir, ".aragon").apply { mkdirs() }
    val stateDir: File = File(aragonDir, "state").apply { mkdirs() }
    val memoryDir: File = File(aragonDir, "memory").apply { mkdirs() }
    val plansDir: File = File(aragonDir, "plans").apply { mkdirs() }
    val observationsDir: File = File(aragonDir, "observations").apply { mkdirs() }
    val runtimeDir: File = File(aragonDir, "runtime").apply { mkdirs() }
    val artifactsDir: File = File(taskRootDir, "artifacts").apply { mkdirs() }
    val logsDir: File = File(taskRootDir, "logs").apply { mkdirs() }
    val tmpDir: File = File(taskRootDir, "tmp").apply { mkdirs() }

    /**
     * Translates a logical path (such as "/workspace/test.py" or "test.py") to a real File.
     */
    fun resolve(logicalPath: String): File {
        val clean = logicalPath.trim()
            .replace("\\", "/")

        return when {
            clean.startsWith("/workspace") -> {
                val rel = clean.removePrefix("/workspace").trimStart('/')
                sanitizeAndResolve(workspaceDir, rel)
            }
            clean.startsWith("/artifacts") -> {
                val rel = clean.removePrefix("/artifacts").trimStart('/')
                sanitizeAndResolve(artifactsDir, rel)
            }
            clean.startsWith("/tmp") -> {
                val rel = clean.removePrefix("/tmp").trimStart('/')
                sanitizeAndResolve(tmpDir, rel)
            }
            clean.startsWith("/home/aragon") -> {
                val rel = clean.removePrefix("/home/aragon").trimStart('/')
                sanitizeAndResolve(workspaceDir, rel)
            }
            clean.startsWith("/") -> {
                // Any other root path is mapped inside workspace to prevent escape
                val rel = clean.trimStart('/')
                sanitizeAndResolve(workspaceDir, rel)
            }
            else -> {
                // Relative path maps directly relative to workspace
                sanitizeAndResolve(workspaceDir, clean)
            }
        }
    }

    /**
     * Converts an actual device File back to its logical path exposed to the LLM.
     */
    fun toLogicalPath(realFile: File): String {
        val canonical = realFile.canonicalPath
        val wsCanonical = workspaceDir.canonicalPath
        val artCanonical = artifactsDir.canonicalPath
        val tmpCanonical = tmpDir.canonicalPath

        return when {
            canonical == wsCanonical -> "/workspace"
            canonical.startsWith("$wsCanonical/") -> {
                "/workspace/" + canonical.removePrefix("$wsCanonical/").replace("\\", "/")
            }
            canonical == artCanonical -> "/artifacts"
            canonical.startsWith("$artCanonical/") -> {
                "/artifacts/" + canonical.removePrefix("$artCanonical/").replace("\\", "/")
            }
            canonical.startsWith("$tmpCanonical/") -> {
                "/tmp/" + canonical.removePrefix("$tmpCanonical/").replace("\\", "/")
            }
            else -> {
                "/workspace/" + realFile.name
            }
        }
    }

    private fun sanitizeAndResolve(baseDir: File, relativePath: String): File {
        // Prevent directory traversal attacks
        val normalized = relativePath.split("/")
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

        return File(baseDir, normalized)
    }
}
