package com.example.aragon.computer

import java.io.File

/**
 * Single authority for path translation between logical sandbox paths
 * (/workspace, /process, /artifacts, /projects, /tmp) and actual device files.
 * The model NEVER sees Android private paths like /data/data/...
 */
class WorkspacePathResolver(
    private val taskRootDir: File
) {
    val workspaceDir: File = File(taskRootDir, "workspace").apply { mkdirs() }
    val processDir: File = File(workspaceDir, "process").apply { mkdirs() }
    val artifactsDir: File = File(workspaceDir, "artifacts").apply { mkdirs() }
    val projectsDir: File = File(taskRootDir.parentFile ?: taskRootDir, "projects").apply { mkdirs() }
    val tmpDir: File = File(taskRootDir, "tmp").apply { mkdirs() }

    val aragonDir: File = File(workspaceDir, ".aragon").apply { mkdirs() }
    val stateDir: File = File(aragonDir, "state").apply { mkdirs() }
    val memoryDir: File = File(aragonDir, "memory").apply { mkdirs() }
    val plansDir: File = File(aragonDir, "plans").apply { mkdirs() }
    val observationsDir: File = File(aragonDir, "observations").apply { mkdirs() }
    val runtimeDir: File = File(aragonDir, "runtime").apply { mkdirs() }
    val scriptsDir: File = File(runtimeDir, "scripts").apply { mkdirs() }
    val checkpointsDir: File = File(aragonDir, "checkpoints").apply { mkdirs() }
    val logsDir: File = File(aragonDir, "logs").apply { mkdirs() }

    /**
     * Translates a logical path (such as "/workspace/report.docx", "/process/scratch.py", "/artifacts/report.docx")
     * to a real File in the sandbox.
     */
    fun resolve(logicalPath: String): File {
        val clean = logicalPath.trim().replace("\\", "/")

        return when {
            clean.startsWith("/workspace/process") -> {
                val rel = clean.removePrefix("/workspace/process").trimStart('/')
                sanitizeAndResolve(processDir, rel)
            }
            clean.startsWith("/process") -> {
                val rel = clean.removePrefix("/process").trimStart('/')
                sanitizeAndResolve(processDir, rel)
            }
            clean.startsWith("/workspace/artifacts") -> {
                val rel = clean.removePrefix("/workspace/artifacts").trimStart('/')
                sanitizeAndResolve(artifactsDir, rel)
            }
            clean.startsWith("/artifacts") -> {
                val rel = clean.removePrefix("/artifacts").trimStart('/')
                sanitizeAndResolve(artifactsDir, rel)
            }
            clean.startsWith("/projects") -> {
                val rel = clean.removePrefix("/projects").trimStart('/')
                sanitizeAndResolve(projectsDir, rel)
            }
            clean.startsWith("/tmp") -> {
                val rel = clean.removePrefix("/tmp").trimStart('/')
                sanitizeAndResolve(tmpDir, rel)
            }
            clean.startsWith("/workspace") -> {
                val rel = clean.removePrefix("/workspace").trimStart('/')
                sanitizeAndResolve(workspaceDir, rel)
            }
            clean.startsWith("/home/aragon") -> {
                val rel = clean.removePrefix("/home/aragon").trimStart('/')
                sanitizeAndResolve(workspaceDir, rel)
            }
            clean.startsWith("/") -> {
                val rel = clean.trimStart('/')
                sanitizeAndResolve(workspaceDir, rel)
            }
            else -> {
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
        val procCanonical = processDir.canonicalPath
        val artCanonical = artifactsDir.canonicalPath
        val projCanonical = projectsDir.canonicalPath
        val tmpCanonical = tmpDir.canonicalPath

        return when {
            canonical == procCanonical -> "/process"
            canonical.startsWith("$procCanonical/") -> {
                "/process/" + canonical.removePrefix("$procCanonical/").replace("\\", "/")
            }
            canonical == artCanonical -> "/artifacts"
            canonical.startsWith("$artCanonical/") -> {
                "/artifacts/" + canonical.removePrefix("$artCanonical/").replace("\\", "/")
            }
            canonical == projCanonical -> "/projects"
            canonical.startsWith("$projCanonical/") -> {
                "/projects/" + canonical.removePrefix("$projCanonical/").replace("\\", "/")
            }
            canonical == tmpCanonical -> "/tmp"
            canonical.startsWith("$tmpCanonical/") -> {
                "/tmp/" + canonical.removePrefix("$tmpCanonical/").replace("\\", "/")
            }
            canonical == wsCanonical -> "/workspace"
            canonical.startsWith("$wsCanonical/") -> {
                "/workspace/" + canonical.removePrefix("$wsCanonical/").replace("\\", "/")
            }
            else -> {
                "/workspace/" + realFile.name
            }
        }
    }

    private fun sanitizeAndResolve(baseDir: File, relativePath: String): File {
        val normalized = relativePath.split("/")
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

        return File(baseDir, normalized)
    }
}
