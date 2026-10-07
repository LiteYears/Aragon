package com.example.aragon.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.aragon.AragonApplication
import com.example.aragon.domain.model.Artifact
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

data class ParsedDocx(
    val title: String,
    val paragraphs: List<String>,
    val tables: List<List<List<String>>>,
    val rawText: String
)

@Composable
fun ArtifactViewerDialog(
    artifact: Artifact,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    var fileContent by remember { mutableStateOf<String?>(null) }
    var parsedDocx by remember { mutableStateOf<ParsedDocx?>(null) }
    var realFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artifact) {
        withContext(Dispatchers.IO) {
            val resolver = AragonApplication.instance.workspaceManager.getPathResolver(artifact.taskId)
            val f = resolver.resolve(artifact.logicalPath)
            realFile = if (f.exists()) f else null

            if (f.exists()) {
                val ext = f.extension.lowercase()
                if (ext == "docx") {
                    parsedDocx = extractDocxDetails(f)
                } else if (f.length() < 1024 * 1024) { // Under 1MB
                    fileContent = runCatching { f.readText() }.getOrNull()
                }
            }
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = AmoledBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .testTag("artifact_viewer_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AmoledCard)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = AmoledInteractive,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = AmoledTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artifact.filename,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmoledTextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "${formatBytes(artifact.size)} • ${artifact.logicalPath}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = AmoledTextSecondary,
                            maxLines = 1
                        )
                    }

                    // Quick Action: Download
                    IconButton(
                        onClick = {
                            realFile?.let { f ->
                                val success = saveToDownloads(context, f, artifact.mimeType)
                                val msg = if (success) "Saved to Downloads: ${f.name}" else "Failed to save file"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("viewer_download_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download", tint = AmoledTextPrimary)
                    }

                    // Quick Action: Share
                    IconButton(
                        onClick = {
                            realFile?.let { shareFile(context, it, artifact.mimeType) }
                        },
                        modifier = Modifier.testTag("viewer_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = AmoledTextPrimary)
                    }

                    // Close Button
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("viewer_close_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AmoledTextSecondary)
                    }
                }

                Divider(color = AmoledBorder, thickness = 1.dp)

                // Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AmoledElevated)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            realFile?.let { f ->
                                val ok = saveToDownloads(context, f, artifact.mimeType)
                                val msg = if (ok) "Saved to Downloads folder ✓" else "Could not save file"
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmoledTextPrimary,
                            contentColor = AmoledBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("viewer_download_bar_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (fileContent != null || parsedDocx != null) {
                        OutlinedButton(
                            onClick = {
                                val text = fileContent ?: parsedDocx?.rawText.orEmpty()
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "Copied content to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                            border = BorderStroke(1.dp, AmoledBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy", fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            realFile?.let { openFileExternally(context, it, artifact.mimeType) }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open In App", fontSize = 13.sp)
                    }
                }

                Divider(color = AmoledBorder, thickness = 1.dp)

                // Main Viewer Body
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AmoledBg)
                        .padding(16.dp)
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Loading deliverable...", color = AmoledTextSecondary)
                            }
                        }

                        parsedDocx != null -> {
                            // Rich DOCX OpenXML Document Viewer
                            DocxRenderView(parsed = parsedDocx!!)
                        }

                        fileContent != null -> {
                            // Formatted Code / Text Viewer with Line Numbers
                            CodeTextViewer(
                                content = fileContent!!,
                                verticalScroll = verticalScroll,
                                horizontalScroll = horizontalScroll
                            )
                        }

                        else -> {
                            // Binary / Structured Overview
                            BinaryDeliverableOverview(
                                artifact = artifact,
                                realFile = realFile,
                                onDownload = {
                                    realFile?.let { f ->
                                        saveToDownloads(context, f, artifact.mimeType)
                                        Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpen = {
                                    realFile?.let { openFileExternally(context, it, artifact.mimeType) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocxRenderView(parsed: ParsedDocx) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        Surface(
            color = AmoledCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AmoledSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AmoledSuccess.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "OPENXML DOCX DELIVERABLE",
                            color = AmoledSuccess,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = parsed.title.ifBlank { "Executive Performance Audit" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmoledTextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = AmoledBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Render Paragraphs
                parsed.paragraphs.forEach { para ->
                    Text(
                        text = para,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmoledTextSecondary,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Render Tables if any
                if (parsed.tables.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Document Tables",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    parsed.tables.forEach { table ->
                        Surface(
                            color = AmoledElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                table.forEachIndexed { rowIndex, row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                                color = if (rowIndex == 0) AmoledTextPrimary else AmoledTextSecondary,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    if (rowIndex < table.size - 1) {
                                        Divider(color = AmoledBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun CodeTextViewer(
    content: String,
    verticalScroll: androidx.compose.foundation.ScrollState,
    horizontalScroll: androidx.compose.foundation.ScrollState
) {
    val lines = remember(content) { content.lines() }

    Surface(
        color = AmoledCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(verticalScroll)
        ) {
            Row(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                // Line Numbers Column
                Column(modifier = Modifier.padding(end = 16.dp)) {
                    lines.indices.forEach { index ->
                        Text(
                            text = "${index + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = AmoledTextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Vertical Divider Line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .background(AmoledBorder)
                        .padding(vertical = 2.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Content Lines
                Column {
                    lines.forEach { line ->
                        Text(
                            text = line.ifEmpty { " " },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = AmoledTextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BinaryDeliverableOverview(
    artifact: Artifact,
    realFile: File?,
    onDownload: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = AmoledCard,
            shape = CircleShape,
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = AmoledTextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = artifact.filename,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AmoledTextPrimary
        )

        Text(
            text = "${formatBytes(artifact.size)} • ${artifact.mimeType}",
            style = MaterialTheme.typography.bodyMedium,
            color = AmoledTextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = AmoledElevated,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Deliverable Details",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmoledTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Logical Path: ${artifact.logicalPath}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = AmoledTextSecondary
                )
                Text(
                    text = "Validation: ${if (artifact.valid) "Verified Valid Deliverable ✓" else "Unverified"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (artifact.valid) AmoledSuccess else AmoledTextSecondary
                )
                if (artifact.validationDetails.isNotBlank()) {
                    Text(
                        text = artifact.validationDetails,
                        style = MaterialTheme.typography.bodySmall,
                        color = AmoledTextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save to Downloads", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpen,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                border = BorderStroke(1.dp, AmoledBorder),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open in App")
            }
        }
    }
}

private fun extractDocxDetails(file: File): ParsedDocx {
    val paragraphs = mutableListOf<String>()
    val tables = mutableListOf<List<List<String>>>()
    var title = file.nameWithoutExtension.replace("_", " ")
    val rawSb = StringBuilder()

    runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }

            // Extract text in paragraphs <w:p>
            val pRegex = "<w:p[^>]*>(.*?)</w:p>".toRegex(RegexOption.DOT_MATCHES_ALL)
            pRegex.findAll(xml).forEach { pMatch ->
                val pContent = pMatch.groupValues[1]
                val tRegex = "<w:t[^>]*>(.*?)</w:t>".toRegex()
                val text = tRegex.findAll(pContent).map { it.groupValues[1] }.joinToString("").trim()
                if (text.isNotBlank()) {
                    paragraphs.add(text)
                    rawSb.append(text).append("\n\n")
                    if (title.isBlank() || title == file.nameWithoutExtension) {
                        title = text
                    }
                }
            }

            // Extract table rows
            val tblRegex = "<w:tbl[^>]*>(.*?)</w:tbl>".toRegex(RegexOption.DOT_MATCHES_ALL)
            tblRegex.findAll(xml).forEach { tblMatch ->
                val tableRows = mutableListOf<List<String>>()
                val trRegex = "<w:tr[^>]*>(.*?)</w:tr>".toRegex(RegexOption.DOT_MATCHES_ALL)
                trRegex.findAll(tblMatch.groupValues[1]).forEach { trMatch ->
                    val cells = mutableListOf<String>()
                    val tcRegex = "<w:tc[^>]*>(.*?)</w:tc>".toRegex(RegexOption.DOT_MATCHES_ALL)
                    tcRegex.findAll(trMatch.groupValues[1]).forEach { tcMatch ->
                        val tRegex = "<w:t[^>]*>(.*?)</w:t>".toRegex()
                        val cellText = tRegex.findAll(tcMatch.groupValues[1]).map { it.groupValues[1] }.joinToString(" ").trim()
                        cells.add(cellText)
                    }
                    if (cells.isNotEmpty()) {
                        tableRows.add(cells)
                    }
                }
                if (tableRows.isNotEmpty()) {
                    tables.add(tableRows)
                }
            }
        }
    }

    return ParsedDocx(
        title = title,
        paragraphs = paragraphs,
        tables = tables,
        rawText = rawSb.toString()
    )
}

fun saveToDownloads(context: Context, file: File, mimeType: String): Boolean {
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            true
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val dest = File(downloadsDir, file.name)
            file.copyTo(dest, overwrite = true)
            true
        }
    }.getOrDefault(false)
}

fun shareFile(context: Context, file: File, mimeType: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Deliverable"))
    }
}

fun openFileExternally(context: Context, file: File, mimeType: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
