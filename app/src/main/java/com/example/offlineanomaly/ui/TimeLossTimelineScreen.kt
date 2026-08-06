package com.example.offlineanomaly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.offlineanomaly.viewmodel.TimeLossViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.setValue
data class TimeLossEpisode(
    val appName: String,
    val date: String,
    val startTime: String,
    val durationMins: Int,
    val severity: String
)

@Composable
fun TimeLossTimelineScreen(
    padding: PaddingValues,
    viewModel: TimeLossViewModel = viewModel()
) {
    val episodes by viewModel.episodes.collectAsState()

    var showDebugMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentScenario = com.example.offlineanomaly.repo.MockManager.currentScenario

    val displayEpisodes = when (currentScenario) {
        "BURNOUT" -> listOf(
            TimeLossEpisode("YouTube", "Today", "2:15 AM", 145, "Critical"),
            TimeLossEpisode("Whatsapp", "Yesterday", "11:45 PM", 82, "Critical"),
            TimeLossEpisode("Instagram", "April 02", "3:20 PM", 45, "Warning")
        )
        "RECOVERY" -> listOf(
            TimeLossEpisode("Twitter", "Yesterday", "10:00 PM", 25, "Warning"),
            TimeLossEpisode("Reddit", "March 31", "11:30 PM", 30, "Warning")
        )
        "OPTIMAL" -> emptyList()
        else -> episodes
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(text = "Behavioral Anomalies", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(text = "Detected shifts from your 7-day baseline", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
            }
        }

        // 4. EMPTY STATE
        if (displayEpisodes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No anomalies detected yet.", fontWeight = FontWeight.Bold)
                        Text("Baseline is stable.", color = Color.Gray)
                    }
                }
            }
        }

        // 5. SHOW EPISODES (Mock or Real)
        items(displayEpisodes) { ep ->
            TimelineCard(ep)
        }

//        item {
//            Spacer(Modifier.height(300.dp))
//            androidx.compose.material3.Button(
//                onClick = { showDebugMenu = true },
//                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
//                    containerColor = Color.Red.copy(alpha = 0.1f)
//                ),
//                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
//            ) {
//                Text("DEBUG: CHANGE HISTORY", color = Color.Gray)
//            }
//            Spacer(Modifier.height(100.dp))
//        }
    }

    // 6. DEBUG DIALOG
    if (showDebugMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDebugMenu = false },
            title = { Text("Set History Scenario") },
            text = {
                Column {
                    listOf("OPTIMAL", "RECOVERY", "BURNOUT").forEach { scenario ->
                        androidx.compose.material3.TextButton(onClick = {
                            com.example.offlineanomaly.repo.MockManager.currentScenario = scenario
                            showDebugMenu = false
                        }) {
                            Text("Show $scenario History")
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDebugMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun TimelineCard(ep: TimeLossEpisode) {
    // Determine color based on severity string
    val statusColor = if (ep.severity == "Critical") Color(0xFFE57373) else Color(0xFFFFB74D)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${ep.appName} Anomaly",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${ep.date} • Started at ${ep.startTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${ep.durationMins}m",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimeLossTimelineScreenPreview() {
    MaterialTheme {
        TimeLossTimelineScreen(padding = PaddingValues(0.dp))
    }
}