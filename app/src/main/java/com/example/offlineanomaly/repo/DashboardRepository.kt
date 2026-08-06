package com.example.offlineanomaly.repo

import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.delay
import com.example.offlineanomaly.db.AppDatabase
import com.example.offlineanomaly.db.ThresholdStateEntity
import com.example.offlineanomaly.detector.TimeLossDetector
import com.example.offlineanomaly.feature.UsageFeatureBuilder
import com.example.offlineanomaly.threshold.AdaptiveThresholdManager
import com.example.offlineanomaly.tflite.TFLiteAnomalyDetector
import com.example.offlineanomaly.ui.DashboardMode
import com.example.offlineanomaly.ui.DashboardState
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

data class AppUsageInfo(val name: String, val minutes: Int, val category: String)
data class RawAppEntry(val packageName: String, val timeMs: Long, val category: String)
object MockManager {
    var currentScenario = "OPTIMAL"
}
class DashboardRepository(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val db = AppDatabase.getInstance(context)
    private val thresholdDao = db.thresholdStateDao()
    private val thresholdManager = AdaptiveThresholdManager()
    private val tflite = TFLiteAnomalyDetector(context)
    private val featureBuilder = UsageFeatureBuilder(context)
    private val detector = TimeLossDetector(thresholdManager, db.anomalyRecordDao())

    suspend fun loadDaily(date: LocalDate): DashboardState {
        delay(100)

        val savedThreshold = thresholdDao.get()
        savedThreshold?.let {
            thresholdManager.restore(it.emaMean ?: 0f, it.emaVar ?: 0f, if (it.warmupDone) 10 else 0)
        }

        val indiaZone = ZoneId.of("Asia/Kolkata")
        val selectedDayMs = date.atStartOfDay(indiaZone).toInstant().toEpochMilli()
        val dailyStats = getAggregatedStats(date)

        val lstmPrediction = runInference(date)

        val actualUsage = dailyStats.total.toFloat()
        val userDailyAvg = thresholdManager.meanValue

        val status = detector.evaluate(
            timestamp = selectedDayMs,
            actualUsage = actualUsage,
            lstmPrediction = lstmPrediction,
            userDailyAvg = userDailyAvg
        )
        val input = featureBuilder.get7DayFeaturesForDate(date)

        saveThresholdState()

        val drift = computeDrift(input)

        val stabilityScore = calculateStability(drift, dailyStats, status).coerceIn(0, 100)
        val finalRisk = (100f - stabilityScore) / 100f

        // 4. Labels
        val (label, reason) = classifyBehaviorFromDrift( dailyStats, status, stabilityScore)

        val syncedStatus = when {
            status == TimeLossDetector.Status.ANOMALY -> "CRITICAL"
            status == TimeLossDetector.Status.RECOVERY -> "RECOVERY"

            // 2. The Granular Risk Scale
            finalRisk >= 0.75f -> "CRITICAL"    // 75% - 100% Risk
            finalRisk >= 0.50f -> "WARNING"     // 50% - 74% Risk
            finalRisk >= 0.25f -> "ELEVATED"    // 25% - 49% Risk
            else -> "OPTIMAL"                   // 0% - 24% Risk (Perfect behavior)
        }

        // Hourly Logic (Unchanged)
        val startOfDay = selectedDayMs
        val endOfDay = startOfDay + 86400000L
        val dbHourly = db.usageWindowDao().getHourlyScreenTime(startOfDay, endOfDay)
        val hourlyMinutes = MutableList(24) { 0 }
        dbHourly.forEach {
            val hourInt = it.hour.toIntOrNull() ?: 0
            if (hourInt in 0..23) hourlyMinutes[hourInt] = it.total / 60
        }

        return DashboardState(
            behaviorLabel = label,
            behaviorReason = reason,
            anomalyPercent = finalRisk,
            anomalyStatus = syncedStatus,
            stabilityScore = stabilityScore,
            stabilityStatus = getStabilityStatus(stabilityScore),
            totalMinutes = dailyStats.total,
            productivityMinutes = dailyStats.prod,
            socialMinutes = dailyStats.soc,
            entertainmentMinutes = dailyStats.ent,
            utilityMinutes = dailyStats.util,
            topApps = dailyStats.topApps,
            selectedDate = date,
            hourlyMinutes = hourlyMinutes,
            isLoading = false
        )
    }
    fun getAggregatedStats(date: LocalDate, isWeekly: Boolean = false): AggregatedStats {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()

        val duration = if (isWeekly) 86400000L * 7 else 86400000L
        val endLimit = startOfDay + duration - 1L

        val currentMs = System.currentTimeMillis()
        val endOfDay = if (endLimit > currentMs) currentMs else endLimit

        val pm = context.packageManager

        val interval =
            if (isWeekly) UsageStatsManager.INTERVAL_WEEKLY else UsageStatsManager.INTERVAL_DAILY
        val statsList = usm.queryUsageStats(interval, startOfDay, endOfDay)

        val mergedMap = mutableMapOf<String, Long>()

        for (usageStats in statsList) {
            if (!isWeekly && usageStats.firstTimeStamp < startOfDay) {
                continue
            }

            val totalTime =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    usageStats.totalTimeVisible
                } else {
                    usageStats.totalTimeInForeground
                }

            if (totalTime > 0) {
                val pkg = usageStats.packageName
                mergedMap[pkg] = mergedMap.getOrDefault(pkg, 0L) + totalTime
            }
        }

        val appList = mutableListOf<AppUsageInfo>()
        var t = 0L;
        var p = 0L;
        var e = 0L;
        var s = 0L;
        var u = 0L

        mergedMap.forEach { (pkg, totalTime) ->
            if (totalTime < 1000) return@forEach

            if (pkg == "android" || pkg.contains("squarehome") || pkg.contains("offlineanomaly") ||
                pkg.contains("googlequicksearch") || pkg.contains("community")
            )
                return@forEach

            val hasIcon = pm.getLaunchIntentForPackage(pkg) != null
            if (!hasIcon && totalTime < 5000) return@forEach

            val category = categorize(pkg)
            if (category == "ignore") return@forEach

            val label = try {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (e: Exception) {
                pkg.split(".").last().replaceFirstChar { it.uppercase() }
            }

            val displayMins = (totalTime / 60000).toInt()

            if (displayMins > 0) {
                appList.add(AppUsageInfo(label, displayMins, category))
                t += totalTime
                when (category) {
                    "productivity" -> p += totalTime
                    "entertainment" -> e += totalTime
                    "social" -> s += totalTime
                    else -> u += totalTime
                }
            }

        }
//        val stats = getMockStatsForScreenshot(MockManager.currentScenario)
//        return stats
        return AggregatedStats(
            total = (t / 60000).toInt(),
            prod = (p / 60000).toInt(),
            ent = (e / 60000).toInt(),
            soc = (s / 60000).toInt(),
            util = (u / 60000).toInt(),
            topApps = appList.sortedByDescending { it.minutes }.take(5),
            rawSystemData = emptyList()
        )
    }
    private fun categorize(pkg: String): String {
        val p = pkg.lowercase()

        val systemNoise = listOf(
            "com.google.android.gms", "com.android.systemui",
            "launcher", "overlay", "offlineanomaly", "trebuchet", "com.ss.squarehome2", "googlequicksearchbox"
        )
        if (systemNoise.any { p.contains(it) }) return "ignore"

        // 2. CATEGORY ALLOCATION
        return when {
            p.contains("instagram") || p.contains("facebook") || p.contains("whatsapp") || p.contains("social") || p.contains("snapchat") || p.contains("telegram") -> "social"

            p.contains("youtube") || p.contains("video") || p.contains("netflix") || p.contains("entertainment") || p.contains("spotify") || p.contains("game") -> "entertainment"

            p.contains("chrome") || p.contains("browser") || p.contains("gmail") || p.contains(".gm") || p.contains("mail") || p.contains("productivity") || p.contains("docs") -> "productivity"

            else -> "utility"
        }
    }

    private fun calculateStability(drift: DriftResult, stats: AggregatedStats, status: TimeLossDetector.Status): Int {
        var score = 100f - (abs(drift.totalUsageDelta) * 0.4f)

        when(status) {
            TimeLossDetector.Status.ANOMALY -> score -= 30f
            TimeLossDetector.Status.RECOVERY -> score += 15f
            else -> {}
        }

        if (stats.soc > 240) score -= 45f // > 4 hours Social = Massive Penalty
        else if (stats.soc > 120) score -= 20f // > 2 hours Social

        if (stats.ent > 240) score -= 30f // > 4 hours Entertainment

        if (stats.total > 480) score -= 30f // > 8 hours Total Screen Time

        return score.toInt().coerceIn(0, 100)
    }

    private fun classifyBehaviorFromDrift(
        stats: AggregatedStats,
        status: TimeLossDetector.Status,
        stabilityScore: Int // WE MUST USE THE SCORE HERE
    ): Pair<String, String> {

        val topApp = stats.topApps.firstOrNull()?.name ?: "Apps"

        if (stats.soc > 240) {
            return "Severe Dopamine Loop" to "Warning: You have spent over 4 hours on Social Media today. $topApp is severely impacting your cognitive baseline."
        }
        if (stats.total > 600) {
            return "Extreme Screen Fatigue" to "Your total screen time has exceeded 10 hours. This temporal anomaly requires immediate offline recovery."
        }

        return when {
            stabilityScore < 40 -> "Critical Drift" to "Major temporal drift detected. Unhealthy interaction with $topApp is dominating your day."

            stabilityScore in 40..69 -> "Focus Saturation" to "Your usage patterns are shifting towards distraction. Moderate drift detected."

            status == TimeLossDetector.Status.RECOVERY -> "Behavioral Recovery" to "Positive Anomaly detected. Your stability is rebounding."

            status == TimeLossDetector.Status.ANOMALY -> "Circadian Shift" to "Temporal anomaly detected. Usage has deviated from your healthy baseline."

            // Only if none of the bad things above are true:
            else -> "Predictable Focus" to "High stability found. Your interaction with $topApp remains within personalized boundaries."
        }
    }

    fun runInference(endDate: LocalDate): Float {
        val indiaZone = ZoneId.of("Asia/Kolkata")
        val endMs = endDate.atTime(java.time.LocalTime.MAX).atZone(indiaZone).toInstant().toEpochMilli()
        val startMs = endMs - (7 * 24 * 60 * 60 * 1000L)
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
        return tflite.run(featureBuilder.buildVectorFromStats(stats))
    }
    fun getMockState(date: LocalDate): DashboardState {
        val dayIndex = date.dayOfWeek.value - 1 // 0 = Mon, 6 = Sun
        val weeklyPattern = listOf(210, 245, 190, 220, 380, 410, 315)
        val currentTotal = weeklyPattern[dayIndex]

        val hourlyData = MutableList(24) { 0 }
        val peakHour = if (dayIndex >= 4) 22 else 14
        for (hour in 0..23) {
            val dist = abs(hour - peakHour)
            val value = if (dist < 5) (currentTotal / 8) - (dist * 5) else (0..3).random()
            hourlyData[hour] = value.coerceAtLeast(0)
        }

        val currentRisk = if (dayIndex >= 4) 0.82f else 0.15f
        val currentStability = (100 - (currentRisk * 100)).toInt()

        val mockTopApps = if (dayIndex >= 4) {
            // THE ANOMALY SCENARIO
            listOf(
                AppUsageInfo("Instagram", (currentTotal * 0.5).toInt(), "social"),
                AppUsageInfo("YouTube", (currentTotal * 0.3).toInt(), "entertainment"),
                AppUsageInfo("WhatsApp", (currentTotal * 0.1).toInt(), "social")
            )
        } else {
            // THE STEADY SCENARIO
            listOf(
                AppUsageInfo("Chrome", (currentTotal * 0.4).toInt(), "productivity"),
                AppUsageInfo("Gmail", (currentTotal * 0.2).toInt(), "productivity"),
                AppUsageInfo("WhatsApp", (currentTotal * 0.2).toInt(), "social")
            )
        }

        return DashboardState(
            isDemoMode = true,
            selectedDate = date,
            totalMinutes = currentTotal,
            hourlyMinutes = hourlyData,
            productivityMinutes = (currentTotal * (if (dayIndex >= 4) 0.1 else 0.6)).toInt(),
            socialMinutes = (currentTotal * (if (dayIndex >= 4) 0.6 else 0.2)).toInt(),
            entertainmentMinutes = (currentTotal * (if (dayIndex >= 4) 0.3 else 0.1)).toInt(),

            anomalyPercent = currentRisk,
            stabilityScore = currentStability,
            behaviorLabel = if (currentRisk > 0.6f) "Distraction Trend" else "Steady Routine",
            behaviorReason = if (currentRisk > 0.6f)
                "Significant drift in temporal patterns: High social media usage during typical rest hours."
            else "Usage is within your 7-day learned baseline.",

            topApps = mockTopApps,
            weeklyTotals = weeklyPattern,
            isDeveloperMode = true
        )
    }

    suspend fun loadWeekly(): DashboardState {
        val today = LocalDate.now()
        val indiaZone = ZoneId.of("Asia/Kolkata")
        val todayMs = today.atStartOfDay(indiaZone).toInstant().toEpochMilli()

        val stats = getAggregatedStats(today, isWeekly = true)
        val lstmPrediction = runInference(today)
        val userDailyAvg = thresholdManager.meanValue

        val status = detector.evaluate(
            timestamp = todayMs,
            actualUsage = stats.total.toFloat(),
            lstmPrediction = lstmPrediction,
            userDailyAvg = userDailyAvg
        )

        val input = featureBuilder.get7DayFeaturesForDate(today)
        val drift = computeDrift(input)

        val weeklyStability = calculateStability(drift, stats, status).coerceIn(0, 100)

        val weeklyRisk = (100f - weeklyStability) / 100f

        // In loadWeekly
        val syncedStatus = when {
            status == TimeLossDetector.Status.ANOMALY -> "CRITICAL"
            status == TimeLossDetector.Status.RECOVERY -> "RECOVERY"

            weeklyRisk >= 0.75f -> "CRITICAL"    // 75% - 100% Risk
            weeklyRisk >= 0.50f -> "WARNING"     // 50% - 74% Risk
            weeklyRisk >= 0.25f -> "ELEVATED"    // 25% - 49% Risk
            else -> "OPTIMAL"                   // 0% - 24% Risk (Perfect behavior)
        }

        val (label, reason) = classifyBehaviorFromDrift(stats, status, weeklyStability)
        val weeklyTotals = mutableListOf<Int>()
        for (i in 0 until 7) {
            val date = today.minusDays(i.toLong())
            val dayStats = getAggregatedStats(date)
            weeklyTotals.add(dayStats.total)
        }

        return DashboardState(
            mode = DashboardMode.WEEKLY,
            behaviorLabel = label,
            behaviorReason = "Weekly Trend: $reason",
            anomalyPercent = weeklyRisk,
            anomalyStatus = syncedStatus,
            stabilityScore = weeklyStability,
            stabilityStatus = getStabilityStatus(weeklyStability),
            totalMinutes = stats.total,
            productivityMinutes = stats.prod,
            entertainmentMinutes = stats.ent,
            socialMinutes = stats.soc,
            utilityMinutes = stats.util,
            weeklyTotals = weeklyTotals.reversed(),
            isLoading = false,
            selectedDate = today,
            topApps = stats.topApps
        )
    }

    private fun computeDrift(features: FloatArray): DriftResult {
        val daySize = 10
        val todayIdx = 6 * daySize
        val today = features.sliceArray(todayIdx until todayIdx + daySize)
        val baseline = FloatArray(daySize)
        for (i in 0 until 6) {
            for (j in 0 until daySize) {
                baseline[j] += features[i * daySize + j]
            }
        }
        baseline.indices.forEach { baseline[it] /= 6f }

        if (today[0] < 0.05f && baseline[0] > 0.1f) return DriftResult(0f, 0f, 0f, 0f, 0f)

        fun delta(now: Float, base: Float) = if (base <= 0.01f) 0f
        else (((now - base) / base) * 100f).coerceIn(-100f, 100f)

        return DriftResult(
            totalUsageDelta = delta(today[0], baseline[0]),
            nightDelta = delta(today[1], baseline[1]),
            productivityDelta = delta(today[6], baseline[6]),
            entertainmentDelta = delta(today[7], baseline[7]),
            socialDelta = delta(today[8], baseline[8])
        )
    }


    private suspend fun saveThresholdState() {
        thresholdDao.upsert(ThresholdStateEntity(
            id = 0, userId = "local",
            warmupDone = thresholdManager.isCalibrated,
            baselineP95 = thresholdManager.currentThreshold,
            emaMean = thresholdManager.meanValue,
            emaVar = thresholdManager.varianceValue,
            lastUpdated = System.currentTimeMillis()
        ))
    }

    private fun getStabilityStatus(score: Int) = when {
        score >= 85 -> "Stable"; score >= 70 -> "Slight Drift"
        score >= 50 -> "Moderate Drift"; else -> "Major Drift"
    }


    fun getMockStatsForScreenshot(scenario: String): AggregatedStats {
        val appList = mutableListOf<AppUsageInfo>()

        when (scenario) {
            "OPTIMAL" -> {
                appList.add(AppUsageInfo("Android Studio", 240, "productivity"))
                appList.add(AppUsageInfo("YouTube", 45, "entertainment"))
                appList.add(AppUsageInfo("Messages", 15, "social"))
                return AggregatedStats(
                    total = 300, prod = 240, ent = 45, soc = 15, util = 0,
                    topApps = appList.sortedByDescending { it.minutes }, rawSystemData = emptyList()
                )
            }
            "RECOVERY" -> {
                appList.add(AppUsageInfo("Android Studio", 180, "productivity"))
                appList.add(AppUsageInfo("YouTube", 60, "entertainment"))
                appList.add(AppUsageInfo("Instagram", 45, "social"))
                return AggregatedStats(
                    total = 285, prod = 180, ent = 60, soc = 45, util = 0,
                    topApps = appList.sortedByDescending { it.minutes }, rawSystemData = emptyList()
                )
            }
            "WEEKEND" -> {
                appList.add(AppUsageInfo("Netflix", 210, "entertainment"))
                appList.add(AppUsageInfo("YouTube", 120, "entertainment"))
                appList.add(AppUsageInfo("WhatsApp", 60, "social"))
                return AggregatedStats(
                    total = 390, prod = 0, ent = 330, soc = 60, util = 0,
                    topApps = appList.sortedByDescending { it.minutes }, rawSystemData = emptyList()
                )
            }
            "FRAGMENTED" -> {
                appList.add(AppUsageInfo("Whatsapp", 25, "social"))
                appList.add(AppUsageInfo("Instagram", 20, "social"))
                appList.add(AppUsageInfo("Twitter", 18, "social"))
                appList.add(AppUsageInfo("Reddit", 15, "social"))
                appList.add(AppUsageInfo("Chrome", 12, "util"))
                return AggregatedStats(
                    total = 90, prod = 0, ent = 0, soc = 78, util = 12,
                    topApps = appList.sortedByDescending { it.minutes }, rawSystemData = emptyList()
                )
            }
            "BURNOUT" -> {
                appList.add(AppUsageInfo("YouTube", 320, "entertainment"))
                appList.add(AppUsageInfo("Instagram", 180, "social"))
                appList.add(AppUsageInfo("Android Studio", 120, "productivity"))
                return AggregatedStats(
                    total = 620, prod = 120, ent = 320, soc = 180, util = 0,
                    topApps = appList.sortedByDescending { it.minutes }, rawSystemData = emptyList()
                )
            }
            else -> return AggregatedStats(0,0,0,0,0, emptyList(), emptyList())
        }
    }

    data class MockHistoryItem(val date: String, val riskScore: Int, val status: String)

    fun getMockHistoryList(): List<MockHistoryItem> {
        return listOf(
            MockHistoryItem("Today, April 6", 85, "Critical Anomaly"),
            MockHistoryItem("Yesterday, April 5", 40, "Minor Deviation"),
            MockHistoryItem("Saturday, April 4", 5, "Optimal"),
            MockHistoryItem("Friday, April 3", 8, "Optimal"),
            MockHistoryItem("Thursday, April 2", 12, "Predictable Focus"),
            MockHistoryItem("Wednesday, April 1", 60, "Routine Shift"),
            MockHistoryItem("Tuesday, March 31", 10, "Predictable Focus")
        )
    }

    data class AggregatedStats(
        val total: Int, val prod: Int, val ent: Int, val soc: Int, val util: Int,
        val topApps: List<AppUsageInfo>,
        val rawSystemData: List<RawAppEntry>
    )
    data class DriftResult(val totalUsageDelta: Float, val nightDelta: Float, val productivityDelta: Float, val entertainmentDelta: Float, val socialDelta: Float)
}



