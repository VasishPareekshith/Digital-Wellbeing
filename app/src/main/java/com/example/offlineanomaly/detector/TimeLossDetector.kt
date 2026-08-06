package com.example.offlineanomaly.detector

import com.example.offlineanomaly.db.AnomalyRecordDao
import com.example.offlineanomaly.db.AnomalyRecordEntity
import com.example.offlineanomaly.threshold.AdaptiveThresholdManager
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.PI

class TimeLossDetector(
    private val thresholdManager: AdaptiveThresholdManager,
    private val anomalyDao: AnomalyRecordDao,
    private val modelVersion: String = "local"
) {
    enum class Status { NORMAL, ANOMALY, RECOVERY }

    fun getSanitizedTarget(lstmPrediction: Float, hour: Int, userDailyAvg: Float): Float {
        val hourlyAvg = userDailyAvg / 24f
        val healthMultiplier = when (hour) {
            in 0..5 -> 0.2f
            in 9..17 -> 0.8f
            else -> 1.0f
        }
        val dynamicLimit = hourlyAvg * healthMultiplier
        return minOf(lstmPrediction, dynamicLimit)
    }

    fun getCircadianWeight(hour: Int): Float {
        val peakHour = 2.0
        val radians = (hour - peakHour) * (2 * PI / 24.0)
        val offset = 1.75f
        val amplitude = 0.75f
        return offset + amplitude * cos(radians).toFloat()
    }

    suspend fun evaluate(
        timestamp: Long,
        actualUsage: Float,
        lstmPrediction: Float,
        userDailyAvg: Float
    ): Status {

        val latest = anomalyDao.latest()
        if (latest != null && (timestamp - latest.timestamp) < 60_000) {
            return if (latest.isAnomaly) Status.ANOMALY else Status.NORMAL
        }

        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val target = getSanitizedTarget(lstmPrediction, hour, userDailyAvg)

        val weight = getCircadianWeight(hour)

        val customError = (actualUsage - target) * weight

        val isRecovery = (actualUsage < (lstmPrediction * 0.3f) && lstmPrediction > 30f)

        val result = thresholdManager.evaluate(customError)

        // ✅ Store the data
        anomalyDao.insert(
            AnomalyRecordEntity(
                timestamp = timestamp,
                score = customError,
                isAnomaly = result.isAnomaly && !isRecovery,
                modelVersion = modelVersion
            )
        )

        return when {
            isRecovery -> Status.RECOVERY
            result.isAnomaly -> Status.ANOMALY
            else -> Status.NORMAL
        }
    }
}