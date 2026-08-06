package com.example.offlineanomaly.db

data class HourlyUsage(
    val hour: String,
    val total: Int
)

data class DailyUsage(
    val day: String,
    val total: Int
)