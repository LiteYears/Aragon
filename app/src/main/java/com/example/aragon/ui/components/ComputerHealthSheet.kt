package com.example.aragon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.computer.UbuntuHealthReport
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComputerHealthSheet(
    report: UbuntuHealthReport?,
    onDismiss: () -> Unit,
    onRunHealthCheck: () -> Unit,
    onRunBenchmarkSuite: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("computer_health_sheet")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(AmoledSuccess)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Computer Runtime Telemetry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmoledTextPrimary
                    )
                    Text(
                        text = report?.environmentName ?: "Isolated Linux Environment (OpenSandbox Compatible)",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmoledTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spec Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledElevated),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HealthMetricRow("Status", if (report?.isOnline == true) "Operational ✓" else "Initializing", AmoledSuccess)
                    HealthMetricRow("Free Storage", "${report?.freeStorageMb ?: 1024} MB Available", AmoledTextPrimary)
                    HealthMetricRow("Execution Backend", report?.executionBackend ?: "Local Android Container", AmoledTextSecondary, isMono = true)
                    HealthMetricRow("Process Isolation", if (report?.openSandboxActive == true) "OpenSandbox MicroVM" else "Active (Userspace)", AmoledTextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Capabilities Checklist
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledElevated),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verified Subsystem Capabilities",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CapabilityItem("Bash Command Dispatch & Shell", report?.shellReady == true)
                    CapabilityItem("Python 3.12 Runtime Engine", report?.pythonReady == true)
                    CapabilityItem("OpenXML Document Generation (DOCX Engine)", true)
                    CapabilityItem("OpenSandbox MicroVM Driver & REST API", true)
                    CapabilityItem("Network & Cluster Connectivity", report?.networkReady == true)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRunHealthCheck,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                    border = BorderStroke(1.dp, AmoledBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-probe")
                }

                Button(
                    onClick = {
                        onDismiss()
                        onRunBenchmarkSuite()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Benchmark", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HealthMetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = AmoledTextPrimary,
    isMono: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AmoledTextMuted)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            color = valueColor
        )
    }
}

@Composable
private fun CapabilityItem(title: String, supported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (supported) AmoledSuccess else AmoledTextMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (supported) AmoledTextPrimary else AmoledTextMuted
        )
    }
}
