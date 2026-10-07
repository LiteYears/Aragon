package com.example.aragon.agent

import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Task
import java.io.File
import java.util.zip.ZipFile

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

        // 1. DOCX Objective
        val expectedDocx = request.contains(".docx") || request.contains("docx") || request.contains("word document")
        if (expectedDocx) {
            val docxFiles = resolver.workspaceDir.walkTopDown()
                .filter { it.extension.equals("docx", ignoreCase = true) && !it.path.contains(".aragon") }
                .toList()

            if (docxFiles.isEmpty()) {
                checks.add(VerificationCheck("DOCX File Existence", false, "No .docx document found in workspace"))
            } else {
                for (doc in docxFiles) {
                    val report = ArtifactValidator.validate(doc)
                    if (!report.isValid) {
                        checks.add(VerificationCheck("DOCX Validation (${doc.name})", false, report.details))
                    } else {
                        // Strict OpenXML Inspection
                        val deepCheck = inspectDocxContent(doc)
                        checks.add(VerificationCheck("DOCX Deep Structural Check (${doc.name})", deepCheck.first, deepCheck.second))
                    }
                }
            }
        }

        // 2. XLSX Objective
        val expectedXlsx = request.contains(".xlsx") || request.contains("xlsx") || request.contains("spreadsheet") || request.contains("excel")
        if (expectedXlsx) {
            val xlsxFiles = resolver.workspaceDir.walkTopDown()
                .filter { it.extension.equals("xlsx", ignoreCase = true) && !it.path.contains(".aragon") }
                .toList()

            if (xlsxFiles.isEmpty()) {
                checks.add(VerificationCheck("XLSX File Existence", false, "No .xlsx spreadsheet found in workspace"))
            } else {
                for (xls in xlsxFiles) {
                    val report = ArtifactValidator.validate(xls)
                    checks.add(VerificationCheck("XLSX Validation (${xls.name})", report.isValid, report.details))
                }
            }
        }

        // 3. PDF Objective
        val expectedPdf = request.contains(".pdf") || request.contains("pdf")
        if (expectedPdf) {
            val pdfFiles = resolver.workspaceDir.walkTopDown()
                .filter { it.extension.equals("pdf", ignoreCase = true) && !it.path.contains(".aragon") }
                .toList()

            if (pdfFiles.isEmpty()) {
                checks.add(VerificationCheck("PDF File Existence", false, "No .pdf document found in workspace"))
            } else {
                for (pdf in pdfFiles) {
                    val report = ArtifactValidator.validate(pdf)
                    checks.add(VerificationCheck("PDF Validation (${pdf.name})", report.isValid, report.details))
                }
            }
        }

        // 4. Code / Script Objective
        val expectedPython = request.contains(".py") || request.contains("python script")
        if (expectedPython) {
            val pyFiles = resolver.workspaceDir.walkTopDown()
                .filter { it.extension.equals("py", ignoreCase = true) && !it.path.contains(".aragon") }
                .toList()
            if (pyFiles.isEmpty()) {
                checks.add(VerificationCheck("Python File Existence", false, "No .py file produced in workspace"))
            } else {
                for (py in pyFiles) {
                    val hasContent = py.length() > 0
                    checks.add(VerificationCheck("Python Script Non-Empty (${py.name})", hasContent, "${py.length()} bytes"))
                }
            }
        }

        // 5. Explicit Named File Check (e.g., hello.txt, data.csv)
        val fileRegex = "([a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+)".toRegex()
        val requestedFilenames = fileRegex.findAll(task.originalRequest).map { it.value }.toSet()
        for (targetName in requestedFilenames) {
            if (targetName.endsWith(".docx") || targetName.endsWith(".xlsx") || targetName.endsWith(".pdf") || targetName.endsWith(".py")) {
                continue // Already handled
            }
            val targetFile = File(resolver.workspaceDir, targetName)
            val exists = targetFile.exists() && targetFile.length() > 0
            checks.add(
                VerificationCheck(
                    name = "Named File Verification ($targetName)",
                    passed = exists,
                    details = if (exists) "File exists (${targetFile.length()} bytes)" else "File $targetName was not found or is empty"
                )
            )
        }

        // 6. Generic Objective Verification: At least one valid output produced and no fatal error
        if (task.lastError != null && task.status == com.example.aragon.domain.model.TaskStatus.FAILED) {
            checks.add(VerificationCheck("Fatal Error Check", false, "Task terminated with error: ${task.lastError}"))
        }

        val allPassed = checks.isNotEmpty() && checks.all { it.passed }
        val fallbackPassed = checks.isEmpty() && resolver.workspaceDir.listFiles()?.any { !it.name.startsWith(".aragon") } == true

        val verified = allPassed || fallbackPassed
        val summary = if (verified) {
            "Objective verified: ${checks.count { it.passed }}/${checks.size} structural verification checks passed."
        } else {
            "Objective verification unmet: ${checks.filter { !it.passed }.joinToString("; ") { it.details }}"
        }

        return VerificationResult(
            isVerified = verified,
            summary = summary,
            checks = checks
        )
    }

    private fun inspectDocxContent(file: File): Pair<Boolean, String> {
        return try {
            ZipFile(file).use { zip ->
                val docEntry = zip.getEntry("word/document.xml") ?: return Pair(false, "Missing word/document.xml")
                val text = zip.getInputStream(docEntry).bufferedReader().use { it.readText() }
                val hasText = text.contains("<w:t") || text.contains("<w:p")
                val hasTable = text.contains("<w:tbl")
                if (hasText) {
                    val tableNote = if (hasTable) " (includes tables)" else ""
                    Pair(true, "Document contains valid OpenXML content paragraphs$tableNote")
                } else {
                    Pair(false, "Document body is empty")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Error inspecting document.xml: ${e.message}")
        }
    }
}
