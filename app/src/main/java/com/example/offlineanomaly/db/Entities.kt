package com.example.offlineanomaly.db

import androidx.room.*

@Entity(tableName = "usage_event")
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val packageName: String,
    val eventType: Int,
    val screenOn: Boolean,
    val sessionDurationMs: Long?,
    val notifications: Int?
)

@Entity(tableName = "feature_vector", indices = [Index("windowStart"), Index("windowEnd")])
data class FeatureVectorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowStart: Long,
    val windowEnd: Long,
    val features: ByteArray,
    val seqLen: Int
)

@Entity(tableName = "anomaly_record", indices = [Index("timestamp")])
data class AnomalyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val score: Float,
    val isAnomaly: Boolean,
    val modelVersion: String,
    val primaryApp: String = "Unknown",
    val duration: Int = 0
)


@Entity(tableName = "threshold_state")
data class ThresholdStateEntity(
    @PrimaryKey val id: Long = 0,
    val userId: String,
    val warmupDone: Boolean,
    val baselineP95: Float?,
    val emaMean: Float?,
    val emaVar: Float?,
    val lastUpdated: Long
)

@Entity(tableName = "usage_window", indices = [Index("timestamp")])
data class UsageWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val screenOnSeconds: Int,
    val foregroundSeconds: Int,
    val appSwitchCount: Int,
    val unlockCount: Int,
    val hourSin: Float,
    val hourCos: Float
)

@Entity(tableName = "reconstruction_error", indices = [Index("timestamp")])
data class ReconstructionErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val error: Float
)
