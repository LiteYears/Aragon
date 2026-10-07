package com.example.aragon.agent

import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Task
import java.io.File

data class VerificationCheck(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class VerificationResult(
    val isVerified: Boolean,
    val summary: String,
    val checks: List<VerificationCheck>
)

class VerificationEngine {

    fun verifyTaskObjective(task: Task, resolver: WorkspacePathResolver): VerificationResult {
        val checks = mutableListOf<VerificationCheck>()
        val request = task.originalRequest.lowercase()

        // 1. Check if specific artifacts were requested
        val expectedDocx = request.contains(".docx") || request.contains("docx") || request.contains("word document")
        val expectedPdf = request.contains(".pdf") || request.contains("pdf")
        val expectedFile = request.contains("create") || request.contains("write") || request.contains("file") || request.contains("hello.txt")

        if (expectedDocx) {
            val docxFiles = resolver.workspaceDir.walkTopDown().filter { it.extension.equals("docx", ignoreCase = true) }.toList()
            if (docxFiles.isEmpty()) {
                checks.add(VerificationCheck("DOCX File Existence", false, "No .docx file found in workspace"))
            } else {
                for (doc in docxFiles) {
                    val report = ArtifactValidator.validate(doc)
                    checks.add(
                        VerificationCheck(
                            name = "DOCX Validation (${doc.name})",
                            passed = report.isValid,
                            details = report.details
                        )
                    )
                }
            }
        }

        if (expectedPdf) {
            val pdfFiles = resolver.workspaceDir.walkTopDown().filter { it.extension.equals("pdf", ignoreCase = true) }.toList()
            if (pdfFiles.isEmpty()) {
                checks.add(VerificationCheck("PDF File Existence", false, "No .pdf file found in workspace"))
            } else {
                for (pdf in pdfFiles) {
                    val report = ArtifactValidator.validate(pdf)
                    checks.add(
                        VerificationCheck(
                            name = "PDF Validation (${pdf.name})",
                            passed = report.isValid,
                            details = report.details
                        )
                    )
                }
            }
        }

        // Generic check: Workspace must not have unresolved fatal failure
        if (task.lastError != null && task.status == com.example.aragon.domain.model.TaskStatus.FAILED) {
            checks.add(VerificationCheck("Error State Check", false, "Task terminated with error: ${task.lastError}"))
        }

        // Check file creation benchmark (e.g. hello.txt)
        if (request.contains("hello.txt")) {
            val hello = File(resolver.workspaceDir, "hello.txt")
            val exists = hello.exists() && hello.readText().contains("Hello Aragon")
            checks.add(VerificationCheck("hello.txt Content Verification", exists, if (exists) "File contains 'Hello Aragon'" else "File missing or content mismatch"))
        }

        val allPassed = checks.isNotEmpty() && checks.all { it.passed }
        val fallbackPassed = checks.isEmpty() && resolver.workspaceDir.listFiles()?.any { !it.name.startsWith(".aragon") } == true

        val verified = allPassed || fallbackPassed
        val summary = if (verified) {
            "Objective verified: ${checks.count { it.passed }}/${checks.size} structural checks passed."
        } else {
            "Objective verification failed: ${checks.filter { !it.passed }.joinToString("; ") { it.details }}"
        }

        return VerificationResult(
            isVerified = verified,
            summary = summary,
            checks = checks
        )
    }
}
