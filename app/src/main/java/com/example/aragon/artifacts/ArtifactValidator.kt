package com.example.aragon.artifacts

import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

data class ValidationReport(
    val isValid: Boolean,
    val mimeType: String,
    val details: String
)

object ArtifactValidator {

    fun validate(file: File): ValidationReport {
        if (!file.exists()) {
            return ValidationReport(false, "application/octet-stream", "File does not exist")
        }
        if (file.length() == 0L) {
            return ValidationReport(false, "application/octet-stream", "File is empty (0 bytes)")
        }

        val extension = file.extension.lowercase()
        return when (extension) {
            "docx" -> validateDocx(file)
            "pdf" -> validatePdf(file)
            "png" -> validatePng(file)
            "jpg", "jpeg" -> validateJpeg(file)
            "apk" -> validateApk(file)
            "zip" -> validateZip(file)
            "xlsx" -> validateXlsx(file)
            "csv" -> validateCsv(file)
            "json" -> validateJson(file)
            "py", "txt", "md", "html", "js", "ts", "kt", "sh" -> validateText(file, extension)
            else -> validateGeneric(file)
        }
    }

    private fun validateDocx(file: File): ValidationReport {
        return try {
            ZipFile(file).use { zip ->
                val hasContentTypes = zip.getEntry("[Content_Types].xml") != null
                val hasDocumentXml = zip.getEntry("word/document.xml") != null
                if (hasContentTypes && hasDocumentXml) {
                    ValidationReport(
                        isValid = true,
                        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        details = "Valid OpenXML DOCX (contains [Content_Types].xml and word/document.xml)"
                    )
                } else {
                    ValidationReport(
                        isValid = false,
                        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        details = "Invalid DOCX: missing [Content_Types].xml or word/document.xml"
                    )
                }
            }
        } catch (e: Exception) {
            ValidationReport(
                isValid = false,
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                details = "Invalid DOCX: corrupted ZIP structure (${e.message})"
            )
        }
    }

    private fun validatePdf(file: File): ValidationReport {
        return try {
            val header = ByteArray(5)
            FileInputStream(file).use { it.read(header) }
            val headerStr = String(header, Charsets.US_ASCII)
            if (headerStr.startsWith("%PDF-")) {
                ValidationReport(true, "application/pdf", "Valid PDF header ($headerStr)")
            } else {
                ValidationReport(false, "application/pdf", "Invalid PDF: missing %PDF- magic signature")
            }
        } catch (e: Exception) {
            ValidationReport(false, "application/pdf", "Error reading PDF: ${e.message}")
        }
    }

    private fun validatePng(file: File): ValidationReport {
        return try {
            val header = ByteArray(8)
            FileInputStream(file).use { it.read(header) }
            val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            if (header.contentEquals(pngMagic)) {
                ValidationReport(true, "image/png", "Valid PNG 8-byte magic header")
            } else {
                ValidationReport(false, "image/png", "Invalid PNG: signature mismatch")
            }
        } catch (e: Exception) {
            ValidationReport(false, "image/png", "Error reading PNG: ${e.message}")
        }
    }

    private fun validateJpeg(file: File): ValidationReport {
        return try {
            val header = ByteArray(2)
            FileInputStream(file).use { it.read(header) }
            if (header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) {
                ValidationReport(true, "image/jpeg", "Valid JPEG SOI marker")
            } else {
                ValidationReport(false, "image/jpeg", "Invalid JPEG: missing SOI marker")
            }
        } catch (e: Exception) {
            ValidationReport(false, "image/jpeg", "Error reading JPEG: ${e.message}")
        }
    }

    private fun validateApk(file: File): ValidationReport {
        return try {
            ZipFile(file).use { zip ->
                val hasManifest = zip.getEntry("AndroidManifest.xml") != null
                if (hasManifest) {
                    ValidationReport(true, "application/vnd.android.package-archive", "Valid APK package with AndroidManifest.xml")
                } else {
                    ValidationReport(false, "application/vnd.android.package-archive", "Invalid APK: missing AndroidManifest.xml")
                }
            }
        } catch (e: Exception) {
            ValidationReport(false, "application/vnd.android.package-archive", "Invalid APK structure: ${e.message}")
        }
    }

    private fun validateZip(file: File): ValidationReport {
        return try {
            ZipFile(file).use {
                ValidationReport(true, "application/zip", "Valid ZIP archive (${it.size()} entries)")
            }
        } catch (e: Exception) {
            ValidationReport(false, "application/zip", "Corrupt ZIP archive: ${e.message}")
        }
    }

    private fun validateXlsx(file: File): ValidationReport {
        return try {
            ZipFile(file).use { zip ->
                val hasWorkbook = zip.getEntry("xl/workbook.xml") != null
                if (hasWorkbook) {
                    ValidationReport(true, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Valid OpenXML XLSX")
                } else {
                    ValidationReport(false, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Invalid XLSX: missing xl/workbook.xml")
                }
            }
        } catch (e: Exception) {
            ValidationReport(false, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Corrupt XLSX: ${e.message}")
        }
    }

    private fun validateCsv(file: File): ValidationReport {
        val lines = file.readLines()
        return if (lines.isNotEmpty()) {
            ValidationReport(true, "text/csv", "Valid CSV (${lines.size} rows)")
        } else {
            ValidationReport(false, "text/csv", "Empty CSV file")
        }
    }

    private fun validateJson(file: File): ValidationReport {
        val content = file.readText().trim()
        val isValid = (content.startsWith("{") && content.endsWith("}")) ||
                (content.startsWith("[") && content.endsWith("]"))
        return ValidationReport(isValid, "application/json", if (isValid) "Valid JSON content" else "Malformed JSON brackets")
    }

    private fun validateText(file: File, ext: String): ValidationReport {
        val mime = when (ext) {
            "html" -> "text/html"
            "md" -> "text/markdown"
            "py" -> "text/x-python"
            "js" -> "text/javascript"
            "kt" -> "text/x-kotlin"
            else -> "text/plain"
        }
        val length = file.length()
        return ValidationReport(length > 0, mime, "Valid source/text ($length bytes)")
    }

    private fun validateGeneric(file: File): ValidationReport {
        return ValidationReport(file.length() > 0, "application/octet-stream", "Generic binary/file (${file.length()} bytes)")
    }
}
