package com.example.aragon.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.io.File

@Composable
fun ArtifactCard(
    artifact: Artifact,
    modifier: Modifier = Modifier,
    onView: ((Artifact) -> Unit)? = null
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onView?.invoke(artifact) }
            .testTag("artifact_card_${artifact.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = AmoledInteractive,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = AmoledTextPrimary,
                            modifier = Modifier.size(20.dp)
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

                if (artifact.verified) {
                    Surface(
                        color = AmoledSuccess.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AmoledSuccess.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = AmoledSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verified",
                                color = AmoledSuccess,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            if (artifact.validationDetails.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AmoledElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AmoledBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = artifact.validationDetails,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmoledTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Minimalist Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action: View File In-App
                Button(
                    onClick = { onView?.invoke(artifact) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmoledTextPrimary,
                        contentColor = AmoledBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("view_artifact_btn")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Download Button
                OutlinedButton(
                    onClick = {
                        val file = getRealFile(artifact)
                        if (file != null) {
                            val ok = saveToDownloads(context, file, artifact.mimeType)
                            val msg = if (ok) "Downloaded: ${file.name}" else "Download failed"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                    border = BorderStroke(1.dp, AmoledBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("download_artifact_btn")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 12.sp)
                }

                // Share Button
                OutlinedButton(
                    onClick = {
                        val file = getRealFile(artifact)
                        if (file != null) {
                            shareFile(context, file, artifact.mimeType)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                    border = BorderStroke(1.dp, AmoledBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("share_artifact_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                }

                // Open Externally Button
                OutlinedButton(
                    onClick = {
                        val file = getRealFile(artifact)
                        if (file != null) {
                            openFileExternally(context, file, artifact.mimeType)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                    border = BorderStroke(1.dp, AmoledBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("open_artifact_btn")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

private fun getRealFile(artifact: Artifact): File? {
    val resolver = AragonApplication.instance.workspaceManager.getPathResolver(artifact.taskId)
    val f = resolver.resolve(artifact.logicalPath)
    return if (f.exists()) f else null
}
