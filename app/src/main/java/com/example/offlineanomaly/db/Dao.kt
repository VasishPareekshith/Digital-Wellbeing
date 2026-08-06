package com.example.offlineanomaly.db

import androidx.room.*
@Dao
interface UsageEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<UsageEventEntity>)

    @Query("SELECT * FROM usage_event WHERE timestamp >= :ts ORDER BY timestamp ASC")
    suspend fun since(ts: Long): List<UsageEventEntity>
}

@Dao
interface FeatureVectorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FeatureVectorEntity>)

    @Query("SELECT * FROM feature_vector WHERE windowEnd >= :sinceTs ORDER BY windowEnd ASC")
    suspend fun pending(sinceTs: Long): List<FeatureVectorEntity>
}

@Dao
interface AnomalyRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AnomalyRecordEntity)

    @Query("SELECT * FROM anomaly_record ORDER BY timestamp DESC")
    fun getAllAnomalies(): kotlinx.coroutines.flow.Flow<List<AnomalyRecordEntity>>

    @Query("SELECT * FROM anomaly_record WHERE date(timestamp/1000, 'unixepoch') = date('now') ORDER BY timestamp ASC")
    suspend fun getTodayRecords(): List<AnomalyRecordEntity>

    @Query("SELECT * FROM anomaly_record ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): AnomalyRecordEntity?
}

@Dao
interface ThresholdStateDao {
    @Query("SELECT * FROM threshold_state WHERE id = 0")
    suspend fun get(): ThresholdStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ThresholdStateEntity)
}

@Dao
interface UsageWindowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWindow(window: UsageWindowEntity)

    @Query("SELECT * FROM usage_window ORDER BY timestamp DESC LIMIT :n")
    suspend fun getLastNWindows(n: Int): List<UsageWindowEntity>

    @Query("SELECT score FROM anomaly_record ORDER BY timestamp ASC")
    suspend fun getAllErrors(): List<Float>

    @Query("INSERT INTO anomaly_record(timestamp, score, isAnomaly, modelVersion) VALUES(:timestamp, :error, 0, 'local')")
    suspend fun insertReconstructionError(timestamp: Long, error: Float)

    @Query("SELECT score FROM anomaly_record ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentErrors(limit: Int): List<Float>

    @Query("""
    SELECT SUM(screenOnSeconds) 
    FROM usage_window 
    WHERE timestamp BETWEEN :start AND :end
    """)
    suspend fun getTotalScreenTime(start: Long, end: Long): Int?

    @Query("""
    SELECT 
        strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') as hour,
        SUM(screenOnSeconds) as total
    FROM usage_window
    WHERE timestamp BETWEEN :start AND :end
    GROUP BY hour
    ORDER BY hour
""")
    suspend fun getHourlyScreenTime(start: Long, end: Long): List<HourlyUsage>

    @Query("""
    SELECT date(timestamp/1000, 'unixepoch') as day,
           SUM(screenOnSeconds) as total
    FROM usage_window
    WHERE timestamp >= :start
    GROUP BY day
    ORDER BY day
    """)
    suspend fun getWeeklyTotals(start: Long): List<DailyUsage>
}
