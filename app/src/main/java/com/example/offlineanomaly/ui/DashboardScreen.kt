package com.example.offlineanomaly.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.offlineanomaly.viewmodel.DashboardViewModel
import com.example.offlineanomaly.repo.MockManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(externalPadding: PaddingValues) {
    val viewModel: DashboardViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDebugMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Behavior Analysis", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.toggleDemoMode() }) {
                        Icon(
                            imageVector = if (state.isDemoMode) Icons.Default.Person else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Demo",
                            tint = if (state.isDemoMode) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isDemoMode) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("SIMULATION MODE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(selected = state.mode == DashboardMode.DAILY, onClick = { viewModel.switchMode(DashboardMode.DAILY) }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Daily") }
                SegmentedButton(selected = state.mode == DashboardMode.WEEKLY, onClick = { viewModel.switchMode(DashboardMode.WEEKLY) }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Weekly") }
            }

            if (state.mode == DashboardMode.DAILY) {
                WeekCalendarStrip(selectedDate = state.selectedDate, onDateClick = { viewModel.changeDate(it) })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusSquareCard(Modifier.weight(1f), "Behavior Risk", "${(state.anomalyPercent * 100).toInt()}%", state.anomalyStatus, if (state.anomalyPercent > 0.6f) Color(0xFFB00020) else MaterialTheme.colorScheme.primary)
                StatusSquareCard(Modifier.weight(1f), "Stability", "${state.stabilityScore}%", state.stabilityStatus, if (state.stabilityScore < 50) Color(0xFFB00020) else MaterialTheme.colorScheme.secondary)
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Behavioral Insights", style = MaterialTheme.typography.titleSmall)
                    Text(state.behaviorLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(state.behaviorReason, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state.topApps.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Top Time Consumers", style = MaterialTheme.typography.titleMedium)
                        state.topApps.forEach { app ->
                            Row(Modifier.padding(vertical = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(getCategoryColor(app.category)))
                                    Spacer(Modifier.width(12.dp))
                                    Text(app.name)
                                }
                                Text("${app.minutes}m", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category Breakdown", style = MaterialTheme.typography.titleMedium)
                        Text("${state.totalMinutes / 60}h ${state.totalMinutes % 60}m", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(16.dp))
                    CategoryUsageChart(prod = state.productivityMinutes, ent = state.entertainmentMinutes, soc = state.socialMinutes, util = state.utilityMinutes)
                }
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(if (state.mode == DashboardMode.DAILY) "Hourly Activity" else "Weekly Trend", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    if (state.mode == DashboardMode.DAILY) {
                        HourlyUsageChart(state.hourlyMinutes)
                    } else {
                        BarGraph(values = state.weeklyTotals, isDemoMode = state.isDemoMode)
                    }
                }
            }

//            Spacer(modifier = Modifier.height(300.dp))
//
//            Button(
//                onClick = { showDebugMenu = true },
//                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
//                modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp)
//            ) {
//                Text("OPEN TEST ENGINE", color = Color.White.copy(alpha = 0.5f))
//            }
//
//            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // --- POPUP DIALOG ENGINE ---
    if (showDebugMenu) {
        AlertDialog(
            onDismissRequest = { showDebugMenu = false },
            title = { Text("Select Behavioral Scenario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UI States:", fontWeight = FontWeight.Bold)
                    listOf("OPTIMAL", "RECOVERY", "WEEKEND", "FRAGMENTED", "BURNOUT").forEach { scenario ->
                        TextButton(
                            onClick = {
                                MockManager.currentScenario = scenario
                                viewModel.refresh()
                                showDebugMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Load $scenario", modifier = Modifier.fillMaxWidth())
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("System Notifications:", fontWeight = FontWeight.Bold)
                    listOf("WARNING", "CRITICAL", "PRAISE").forEach { scenario ->
                        TextButton(
                            onClick = {
                                triggerMockNotification(context, scenario)
                                showDebugMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fire $scenario Alert", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDebugMenu = false }) { Text("Close") }
            }
        )
    }
}

// --- UTILS & HELPERS ---

fun getCategoryColor(category: String): Color = when (category) {
    "social" -> Color(0xFFE91E63)
    "entertainment" -> Color(0xFF9C27B0)
    "productivity" -> Color(0xFF4CAF50)
    else -> Color(0xFF607D8B)
}

@Composable
fun CategoryUsageChart(prod: Int, ent: Int, soc: Int, util: Int) {
    val total = (prod + ent + soc + util).coerceAtLeast(1)
    val categories = listOf(
        Triple("Productivity", prod, getCategoryColor("productivity")),
        Triple("Entertainment", ent, getCategoryColor("entertainment")),
        Triple("Social", soc, getCategoryColor("social")),
        Triple("Utility", util, getCategoryColor("utility"))
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.forEach { (name, value, color) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.bodySmall)
                    Text("${value}m", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { value.toFloat() / total },
                    color = color,
                    trackColor = color.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun StatusSquareCard(modifier: Modifier, title: String, value: String, subtitle: String, color: Color) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WeekCalendarStrip(selectedDate: LocalDate, onDateClick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val startDate = today.minusDays(6)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val formatter = DateTimeFormatter.ofPattern("MMM dd")
        Text(
            text = "${startDate.format(formatter)} - ${today.format(formatter)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..6) {
                val date = startDate.plusDays(i.toLong())
                val isSelected = date == selectedDate
                val isToday = date == today
                val dayName = date.dayOfWeek.name.take(3)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else if (isToday) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .clickable { onDateClick(date) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(text = dayName, style = MaterialTheme.typography.labelSmall)
                    Text(text = date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LearningView() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Establishing Baseline...", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HourlyUsageChart(data: List<Int>) {
    val max = (data.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 16.dp)) {
        val barWidth = size.width / 24f
        val gap = barWidth * 0.2f
        data.forEachIndexed { index, value ->
            val barHeight = (value.toFloat() / max) * size.height
            drawRect(
                color = if (value > 45) Color(0xFFB00020) else Color(0xFF6750A4),
                topLeft = Offset(index * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - gap, barHeight)
            )
        }
    }
}

@Composable
fun BarGraph(values: List<Int>, isDemoMode: Boolean) {
    val displayValues = values.takeLast(7)
    val max = (displayValues.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        val barWidth = size.width / 7f
        val gap = barWidth * 0.25f
        displayValues.forEachIndexed { index, value ->
            val barHeight = (value.toFloat() / max) * size.height
            drawRect(
                color = if (index == 6) Color(0xFF6750A4) else Color(0xFF6750A4).copy(alpha = 0.5f),
                topLeft = Offset(index * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - gap, barHeight)
            )
        }
    }
}

fun triggerMockNotification(context: android.content.Context, scenario: String) {
    val manager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val channelId = "offline_anomaly_alerts"

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(channelId, "Anomaly Alerts", android.app.NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
    }

    val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setAutoCancel(true)

    when (scenario) {
        "WARNING" -> {
            builder.setContentTitle("High Behavioral Risk Detected")
            builder.setContentText("Your social media usage is 40% higher than your baseline today.")
        }
        "CRITICAL" -> {
            builder.setContentTitle("Extreme Late-Night Anomaly")
            builder.setContentText("Sustained YouTube usage detected past 2:00 AM. Stability critical.")
        }
        "PRAISE" -> {
            builder.setContentTitle("Routine Stabilized")
            builder.setContentText("Great job! Your digital behavior has returned to your optimal baseline.")
            builder.setSmallIcon(android.R.drawable.star_on)
        }
    }
    manager.notify(scenario.hashCode(), builder.build())
}