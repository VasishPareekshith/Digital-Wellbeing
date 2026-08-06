package com.example.offlineanomaly.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        UsageEventEntity::class,
        FeatureVectorEntity::class,
        AnomalyRecordEntity::class,
        ThresholdStateEntity::class,
        UsageWindowEntity::class,
        ReconstructionErrorEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageEventDao(): UsageEventDao
    abstract fun featureVectorDao(): FeatureVectorDao
    abstract fun anomalyRecordDao(): AnomalyRecordDao
    abstract fun thresholdStateDao(): ThresholdStateDao
    abstract fun usageWindowDao(): UsageWindowDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline_anomaly.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
