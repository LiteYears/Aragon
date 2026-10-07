package com.example.aragon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.domain.model.Artifact
import com.example.aragon.ui.components.ArtifactCard
import com.example.aragon.ui.components.ArtifactViewerDialog
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary

@Composable
fun ArtifactsScreen(
    artifacts: List<Artifact>
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var viewingArtifact by remember { mutableStateOf<Artifact?>(null) }

    val filtered = artifacts.filter { art ->
        val matchesSearch = searchQuery.isBlank() ||
                art.filename.contains(searchQuery, ignoreCase = true) ||
                art.logicalPath.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (selectedFilter) {
            "DOCX" -> art.filename.endsWith(".docx", ignoreCase = true)
            "CODE" -> art.filename.endsWith(".py", ignoreCase = true) ||
                    art.filename.endsWith(".js", ignoreCase = true) ||
                    art.filename.endsWith(".html", ignoreCase = true) ||
                    art.filename.endsWith(".sh", ignoreCase = true)
            "DATA" -> art.filename.endsWith(".json", ignoreCase = true) ||
                    art.filename.endsWith(".csv", ignoreCase = true) ||
                    art.filename.endsWith(".txt", ignoreCase = true) ||
                    art.filename.endsWith(".md", ignoreCase = true)
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBg)
            .padding(horizontal = 16.dp)
            .testTag("artifacts_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Title and description
        Text(
            text = "Generated Deliverables",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = AmoledTextPrimary
        )
        Text(
            text = "Verified outputs and documents generated in the autonomous sandbox.",
            style = MaterialTheme.typography.bodySmall,
            color = AmoledTextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search files by name or path...", color = AmoledTextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = AmoledTextSecondary, modifier = Modifier.size(18.dp))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AmoledCard,
                unfocusedContainerColor = AmoledCard,
                focusedBorderColor = AmoledTextPrimary,
                unfocusedBorderColor = AmoledBorder,
                focusedTextColor = AmoledTextPrimary,
                unfocusedTextColor = AmoledTextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("artifacts_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Minimalist Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All (${artifacts.size})", "DOCX" to "DOCX Reports", "CODE" to "Code / Scripts", "DATA" to "Data & Markdown").forEach { (k, v) ->
                FilterChip(
                    selected = selectedFilter == k,
                    onClick = { selectedFilter = k },
                    label = { Text(v, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmoledInteractive,
                        selectedLabelColor = AmoledTextPrimary,
                        containerColor = AmoledCard,
                        labelColor = AmoledTextSecondary
                    ),
                    border = BorderStroke(1.dp, if (selectedFilter == k) AmoledTextPrimary else AmoledBorder),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    color = AmoledCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = AmoledTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (artifacts.isEmpty()) "No deliverables produced yet" else "No matching deliverables found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AmoledTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (artifacts.isEmpty()) "Run an autonomous agent task to generate documents, scripts, and reports." else "Try adjusting your search query or filter chips.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmoledTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { artifact ->
                    ArtifactCard(
                        artifact = artifact,
                        onView = { viewingArtifact = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }

    viewingArtifact?.let { art ->
        ArtifactViewerDialog(
            artifact = art,
            onDismiss = { viewingArtifact = null }
        )
    }
}
