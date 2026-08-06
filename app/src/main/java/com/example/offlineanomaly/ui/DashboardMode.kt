package com.example.offlineanomaly.ui

import java.time.LocalDate
import com.example.offlineanomaly.repo.AppUsageInfo
enum class DashboardMode {
    DAILY, WEEKLY
}

data class DashboardState(
    val weekLabel: String = "",
    val today: LocalDate = LocalDate.now(),
    val isDemoMode: Boolean = false,
    val totalMinutes: Int = 0,
    val productivityMinutes: Int = 0,
    val entertainmentMinutes: Int = 0,
    val socialMinutes: Int = 0,
    val utilityMinutes: Int = 0,
    val anomalyPercent: Float = 0f,
    val anomalyStatus: String = "NORMAL",
    val stabilityScore: Int = 100,
    val stabilityStatus: String = "Stable",
    val behaviorLabel: String = "Balanced",
    val behaviorReason: String = "",
    val routineShift: Boolean = false,
    val routineMessage: String = "",
    val driftEntertainment: Float = 0f,
    val driftSocial: Float = 0f,
    val driftProductivity: Float = 0f,
    val driftNight: Float = 0f,
    val hourlyMinutes: List<Int> = List(24) { 0 },
    val weeklyTotals: List<Int> = List(7) { 0 },
    val selectedDate: LocalDate = LocalDate.now(),
    val mode: DashboardMode = DashboardMode.DAILY,
    val learning: Boolean = false,
    val isLoading: Boolean = false,
    val topApps: List<AppUsageInfo> = emptyList(),
    val debugRawError: Float = 0f,
    val isDeveloperMode: Boolean = true,
    val rawSystemData: List<RawAppEntry> = emptyList(),
    val totalRawMs: Long = 0L
)
data class RawAppEntry(
    val packageName: String,
    val timeMs: Long,
    val category: String
)