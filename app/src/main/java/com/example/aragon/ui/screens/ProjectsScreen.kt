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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.sp
import com.example.aragon.domain.model.Project
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary

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
                containerColor = AmoledTextPrimary,
                contentColor = AmoledBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("new_project_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        },
        containerColor = AmoledBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("projects_screen")
        ) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Workspace Projects",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AmoledTextPrimary
            )
            Text(
                text = "Isolated workspaces with persistent instructions, memory, and deliverable storage.",
                style = MaterialTheme.typography.bodySmall,
                color = AmoledTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (projects.isEmpty()) {
                DefaultProjectCards(onStartTaskInProject)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(projects) { project ->
                        ProjectItemCard(project = project, onStartTask = { onStartTaskInProject(project) })
                        Spacer(modifier = Modifier.height(8.dp))
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
            onCreate = { name, desc, inst ->
                onCreateProject(name, desc, inst)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ProjectItemCard(
    project: Project,
    onStartTask: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = AmoledTextPrimary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                    Text(
                        text = "Path: ${project.workspacePath}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = AmoledTextSecondary
                    )
                }

                IconButton(onClick = onStartTask) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run Task", tint = AmoledTextPrimary)
                }
            }

            if (project.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmoledTextSecondary
                )
            }
        }
    }
}

@Composable
private fun DefaultProjectCards(onStartTaskInProject: (Project) -> Unit) {
    Column {
        Text(
            text = "Suggested Workspaces",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = AmoledTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        val defaults = listOf(
            Project(
                id = "proj_reports",
                name = "Executive Reports & Audit",
                description = "Standard workspace for compiling verified OpenXML DOCX administrative audits.",
                instructions = "Always output formatted .docx reports into /artifacts and verify OpenXML structure.",
                workspacePath = "/projects/reports"
            ),
            Project(
                id = "proj_sandbox",
                name = "OpenSandbox MicroVM Workspace",
                description = "Isolated microVM environment for executing Python scripts and data processing pipelines.",
                instructions = "Execute computations in OpenSandbox microVM container.",
                workspacePath = "/projects/sandbox"
            )
        )

        defaults.forEach { proj ->
            ProjectItemCard(project = proj, onStartTask = { onStartTaskInProject(proj) })
            Spacer(modifier = Modifier.height(8.dp))
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
    var inst by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledCard,
        title = {
            Text("Create Project Workspace", color = AmoledTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name", color = AmoledTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description", color = AmoledTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = inst,
                    onValueChange = { inst = it },
                    label = { Text("Instructions", color = AmoledTextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), desc.trim(), inst.trim())
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AmoledTextSecondary)
            }
        }
    )
}
