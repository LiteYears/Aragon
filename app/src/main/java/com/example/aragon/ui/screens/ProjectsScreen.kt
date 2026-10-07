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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aragon.domain.model.Project
import com.example.ui.theme.AragonObsidianBg
import com.example.ui.theme.AragonSurface
import com.example.ui.theme.AragonSurfaceVariant

@Composable
fun ProjectsScreen(
    projects: List<Project>,
    onCreateProject: (name: String, desc: String, instructions: String) -> Unit,
    onStartTaskInProject: (Project) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_project_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        },
        containerColor = AragonObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("projects_screen")
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Reusable Project Environments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Projects maintain persistent workspace files, instructions, and memory across multiple tasks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (projects.isEmpty()) {
                // Show default suggestion cards
                DefaultProjectCards(onStartTaskInProject)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(projects) { project ->
                        ProjectItemCard(project = project, onStartTask = { onStartTaskInProject(project) })
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showDialog) {
        CreateProjectDialog(
            onDismiss = { showDialog = false },
            onCreate = { name, desc, instructions ->
                onCreateProject(name, desc, instructions)
                showDialog = false
            }
        )
    }
}

@Composable
private fun DefaultProjectCards(onStartTaskInProject: (Project) -> Unit) {
    val defaults = listOf(
        Project("coding", "Coding & Python Engineering", "Software development and automated scripting in Ubuntu Linux.", "Write clean modular code. Always test scripts and verify syntax before completion.", "/projects/coding"),
        Project("docs", "Document Creation", "Automated compilation of OpenXML Word documents and administrative reports.", "Ensure all documents conform to valid OpenXML schema with tables and headings.", "/projects/docs"),
        Project("web", "Web Projects", "Local web application creation, development server, and packaging.", "Build responsive UI using modern HTML5, CSS3, and JavaScript.", "/projects/web")
    )

    Column {
        defaults.forEach { proj ->
            ProjectItemCard(project = proj, onStartTask = { onStartTaskInProject(proj) })
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ProjectItemCard(project: Project, onStartTask: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AragonSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = project.workspacePath,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onStartTask, modifier = Modifier.testTag("project_run_btn")) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run Task",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (project.instructions.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AragonSurfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Instructions: ${project.instructions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String, instructions: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AragonSurface,
        title = { Text("New Project Environment", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Custom Instructions for Agent") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, desc, instructions) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
