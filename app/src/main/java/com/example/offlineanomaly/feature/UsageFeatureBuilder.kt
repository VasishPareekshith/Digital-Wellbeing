package com.example.offlineanomaly.feature

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.*

class UsageFeatureBuilder(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    // --- 1. COLD START CONSTANTS ---
    // A realistic "average" 10-feature vector for a baseline day
    private val BASELINE_DAY = floatArrayOf(
        4.5f,   // Total Hours
        0.15f,  // Night Ratio (15%)
        1.0f,   // Productivity Hours
        1.5f,   // Entertainment Hours
        1.2f,   // Social Hours
        0.8f,   // Utility Hours
        0.22f,  // Productivity Ratio
        0.33f,  // Entertainment Ratio
        0.26f,  // Social Ratio
        0.19f   // Utility Ratio
    )

    // In UsageFeatureBuilder.kt

    /**
     * Builds the 7x10 (70) feature vector.
     * Uses BASELINE_DAY only for days before the app was installed to "scaffold" the model.
     */
    // 1. ADD THIS: This allows the AI to look at 7 days relative to any date you pick
    fun get7DayFeaturesForDate(date: java.time.LocalDate): FloatArray {
        val result = FloatArray(70)
        val installTime = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: Exception) { System.currentTimeMillis() }

        val selectedDayEnd = date.atTime(java.time.LocalTime.MAX)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        for (i in 0 until 7) {
            val daysBeforeSelected = 6 - i
            val dayEnd = selectedDayEnd - (daysBeforeSelected * 24 * 60 * 60 * 1000L)
            val dayStart = dayEnd - (24 * 60 * 60 * 1000L)

            // USE THE BASELINE if the date is before the app was installed
            val dailyFeatures = if (dayEnd < installTime) {
                BASELINE_DAY
            } else {
                computeDailyFeatures(dayStart, dayEnd)
            }

            System.arraycopy(dailyFeatures, 0, result, i * 10, 10)
        }
        return result
    }

    // 2. ADD THIS: This is the bridge for the raw stats query in your repository
    fun buildVectorFromStats(stats: List<android.app.usage.UsageStats>?): FloatArray {
        // If your TFLite model expects 70 inputs, we must return 70 floats
        val vector = FloatArray(70)
        if (stats.isNullOrEmpty()) return vector

        // Group the raw stats by day and take the last 7 days
        val last7Days = stats.sortedByDescending { it.firstTimeStamp }.take(7).reversed()

        last7Days.forEachIndexed { index, usageStats ->
            if (index < 7) {
                // We reuse your existing logic to turn one day of stats into 10 features
                val dayFeatures = computeDailyFeatures(usageStats.firstTimeStamp, usageStats.lastTimeStamp)
                System.arraycopy(dayFeatures, 0, vector, index * 10, 10)
            }
        }
        return vector
    }

    private fun computeDailyFeatures(start: Long, end: Long): FloatArray {
        // Using queryAndAggregateUsageStats for better performance on large datasets
        val stats = usageStatsManager.queryAndAggregateUsageStats(start, end)

        var totalMillis = 0L
        var nightMillis = 0L
        var prod = 0L; var ent = 0L; var soc = 0L; var util = 0L

        stats.forEach { (pkg, usage) ->
            val time = usage.totalTimeInForeground
            if (time <= 0) return@forEach

            totalMillis += time

            // Night detection: Check if the last used time falls in night hours
            val calendar = Calendar.getInstance().apply { timeInMillis = usage.lastTimeUsed }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 5) nightMillis += time

            when (categorize(pkg)) {
                "productivity" -> prod += time
                "entertainment" -> ent += time
                "social"  -> soc += time
                else -> util += time
            }
        }

        val totalH = totalMillis / 3600000f
        return floatArrayOf(
            totalH,
            if (totalMillis > 0) nightMillis.toFloat() / totalMillis else 0f,
            prod / 3600000f, ent / 3600000f, soc / 3600000f, util / 3600000f,
            if (totalMillis > 0) prod.toFloat() / totalMillis else 0f,
            if (totalMillis > 0) ent.toFloat() / totalMillis else 0f,
            if (totalMillis > 0) soc.toFloat() / totalMillis else 0f,
            if (totalMillis > 0) util.toFloat() / totalMillis else 0f
        )
    }

    // --- 2. ROBUST HYBRID CLASSIFICATION ---
    // In UsageFeatureBuilder.kt (and Repository.kt if they must be separate)
    private fun categorize(pkg: String): String {
        val p = pkg.lowercase()

        // 1. SYSTEM NOISE (Strict exclusion)
        val systemNoise = listOf(
            "com.google.android.gms", "com.android.systemui", "android",
            "launcher", "overlay", "offlineanomaly", "trebuchet"
        )
        if (systemNoise.any { p == it || p.contains(it) }) return "ignore"

        // 2. BROAD MANUAL MATCHING (Fixed the Instagram leak)
        // We check if the package name CONTAINS the keyword before exact mapping
        return when {
            p.contains("instagram") || p.contains("facebook") || p.contains("whatsapp") ||
                    p.contains("messenger") || p.contains("social") || p.contains("tiktok") -> "social"

            p.contains("youtube") || p.contains("video") || p.contains("netflix") ||
                    p.contains("spotify") || p.contains("game") -> "entertainment"

            p.contains("chrome") || p.contains("browser") || p.contains("gmail") ||
                    p.contains("docs") || p.contains("drive") -> "productivity"

            // 3. AUTO-CATEGORIZE (System Metadata Fallback)
            else -> try {
                val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME,
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_AUDIO -> "entertainment"
                    ApplicationInfo.CATEGORY_SOCIAL -> "social"
                    ApplicationInfo.CATEGORY_PRODUCTIVITY,
                    ApplicationInfo.CATEGORY_MAPS -> "productivity"
                    else -> "utility" // Ensure it goes to Utility instead of being ignored
                }
            } catch (e: Exception) {
                "utility" // Last resort: everything else is Utility
            }
        }
    }
}