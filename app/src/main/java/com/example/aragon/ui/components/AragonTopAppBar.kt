package com.example.aragon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AragonTopAppBar(
    selectedModel: String,
    onOpenModelSelector: () -> Unit,
    onOpenComputerStatus: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AmoledBg
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = AmoledCard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "A",
                            color = AmoledTextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ARAGON",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        color = AmoledTextPrimary
                    )
                    Text(
                        text = "Autonomous Agent Runtime",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmoledTextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        },
        actions = {
            // Model Selector Pill
            Surface(
                color = AmoledCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenModelSelector)
                    .testTag("model_selector_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Model",
                        modifier = Modifier.size(13.dp),
                        tint = AmoledTextPrimary
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = selectedModel.substringAfter("/").take(13),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = AmoledTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Computer Status Pill
            Surface(
                color = AmoledCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenComputerStatus)
                    .testTag("computer_status_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AmoledSuccess)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "Computer Status",
                        modifier = Modifier.size(14.dp),
                        tint = AmoledSuccess
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    )
}
