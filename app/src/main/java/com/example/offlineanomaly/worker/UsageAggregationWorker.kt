package com.example.offlineanomaly.worker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.offlineanomaly.db.AppDatabase
import com.example.offlineanomaly.db.UsageWindowEntity
import com.example.offlineanomaly.repo.DashboardRepository
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class UsageAggregationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {

            val now = System.currentTimeMillis()

            val hourStart = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault())
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant()
                .toEpochMilli()

            val hourEnd = now

            val usm = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, hourStart, hourEnd)
            val totalForegroundMs = stats.sumOf { it.totalTimeInForeground }

            saveWindowToDb(hourStart, totalForegroundMs)

            val repository = DashboardRepository(applicationContext)
            repository.loadDaily(LocalDate.now())
            val dailyState = repository.loadDaily(LocalDate.now())
            if (dailyState.anomalyPercent > 0.7f) {
                showDriftNotification("Behavior Drift!", dailyState.behaviorReason)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
    private fun showDriftNotification(title: String, message: String) {
        val channelId = "behavior_alerts"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Behavior Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(1, notification)
    }
    private suspend fun saveWindowToDb(timestamp: Long, foregroundMs: Long) {
        val seconds = (foregroundMs / 1000).toInt()
        val minutesOfDay = LocalTime.now().toSecondOfDay() / 60.0
        val angle = 2.0 * PI * (minutesOfDay / 1440.0)

        val entity = UsageWindowEntity(
            timestamp = timestamp,
            foregroundSeconds = seconds,
            hourSin = sin(angle).toFloat(),
            hourCos = cos(angle).toFloat(),
            screenOnSeconds = seconds,
            appSwitchCount = 0,
            unlockCount = 0
        )

        AppDatabase.getInstance(applicationContext).usageWindowDao().insertWindow(entity)
    }
}