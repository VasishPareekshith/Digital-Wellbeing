package com.example.offlineanomaly.threshold

import kotlin.math.sqrt

/**
 * Manages the dynamic threshold for anomaly detection using EMA.
 * Threshold = Mean + (Alpha * Standard Deviation)
 */
class AdaptiveThresholdManager(
    private val alpha: Float = 3.0f,
    private val beta: Float = 0.15f
) {

    private var mean: Float = 0f
    private var variance: Float = 0f
    private var sampleCount = 0

    // Exposed for Repository/DB saving
    val meanValue: Float get() = mean
    val varianceValue: Float get() = variance
    val currentSamples: Int get() = sampleCount

    /**
     * Re-inflates the manager from DB state.
     */
    fun restore(mean: Float, variance: Float, samples: Int) {
        this.mean = mean
        this.variance = variance
        this.sampleCount = samples
    }

    /**
     * Minimum 5 samples (days) required before we trust the threshold.
     */
    val isCalibrated: Boolean
        get() = sampleCount >= 5

    /**
     * Calculates the threshold.
     * If not calibrated, we return a high default to prevent false positives during learning.
     */
    val currentThreshold: Float
        get() = if (!isCalibrated) 100f else mean + (alpha * std())

    /**
     * Evaluates a new reconstruction error from TFLite.
     */
    fun evaluate(error: Float): ThresholdResult {

        if (sampleCount == 0) {
            mean = error
            variance = error * 0.1f
            sampleCount = 1
            return ThresholdResult(false, currentThreshold)
        }

        val threshold = currentThreshold
        val isAnomaly = isCalibrated && error > threshold

        if (!isAnomaly) {
            updateStats(error)
        }

        return ThresholdResult(isAnomaly, threshold)
    }

    private fun updateStats(error: Float) {

        val delta = error - mean
        mean += beta * delta

        val newDelta = error - mean
        variance = (1 - beta) * (variance + beta * delta * newDelta)

        if (sampleCount < 100) sampleCount++
    }

    private fun std(): Float = sqrt(variance).coerceAtLeast(0.001f)

    data class ThresholdResult(
        val isAnomaly: Boolean,
        val threshold: Float
    )
}