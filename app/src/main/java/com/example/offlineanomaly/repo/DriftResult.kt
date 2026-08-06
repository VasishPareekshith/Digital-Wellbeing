package com.example.offlineanomaly.repo

data class DriftResult(
    val totalUsageDelta: Float,
    val entertainmentDelta: Float,
    val socialDelta: Float,
    val productivityDelta: Float,
    val nightDelta: Float
)