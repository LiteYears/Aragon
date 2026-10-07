package com.example.aragon.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
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
import com.example.aragon.domain.model.Artifact
import com.example.aragon.ui.components.ArtifactCard
import com.example.ui.theme.AragonObsidianBg

@Composable
fun ArtifactsScreen(
    artifacts: List<Artifact>
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filtered = when (selectedFilter) {
        "DOCX" -> artifacts.filter { it.filename.endsWith(".docx", ignoreCase = true) }
        "CODE" -> artifacts.filter {
            it.filename.endsWith(".py") || it.filename.endsWith(".js") || it.filename.endsWith(".html") || it.filename.endsWith(".sh")
        }
        "DATA" -> artifacts.filter {
            it.filename.endsWith(".json") || it.filename.endsWith(".csv") || it.filename.endsWith(".txt")
        }
        else -> artifacts
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AragonObsidianBg)
            .padding(horizontal = 16.dp)
            .testTag("artifacts_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Generated & Verified Artifacts",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Authentic files generated on the local Linux computer, verified via ArtifactValidator.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All", "DOCX" to "DOCX Reports", "CODE" to "Code / Web", "DATA" to "Data & Text").forEach { (k, v) ->
                FilterChip(
                    selected = selectedFilter == k,
                    onClick = { selectedFilter = k },
                    label = { Text(v) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No artifacts generated yet. Launch an agent task to produce verified documents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { artifact ->
                    ArtifactCard(artifact = artifact)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}
