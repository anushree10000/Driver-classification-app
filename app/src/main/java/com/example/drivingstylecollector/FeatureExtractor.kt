package com.example.drivingstylecollector

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computes the same 9 engineered features from a recorded CSV session that
 * driver-classifier-training/train_and_export.py computes during training.
 * If you ever change the feature set, update BOTH this file and
 * generate_data.py / train_and_export.py, then retrain and swap the .tflite
 * model in assets/ -- feature order/definitions must match exactly.
 */
object FeatureExtractor {

    private const val GRAVITY = 9.81f

    // Must match FEATURE_COLS order in train_and_export.py
    val FEATURE_ORDER = listOf(
        "acc_mag_mean", "acc_mag_std", "acc_mag_max",
        "gyro_mag_mean", "gyro_mag_std", "gyro_mag_max",
        "jerk_std", "jerk_max_abs", "harsh_event_rate"
    )

    data class SessionSample(
        val accX: Float, val accY: Float, val accZ: Float,
        val gyroX: Float, val gyroY: Float, val gyroZ: Float
    )

    fun readCsv(file: File): List<SessionSample> {
        val samples = mutableListOf<SessionSample>()
        BufferedReader(FileReader(file)).use { reader ->
            reader.readLine() // skip header
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val values = line!!.split(",")
                if (values.size < 7) continue
                samples.add(
                    SessionSample(
                        accX = values[1].toFloat(), accY = values[2].toFloat(), accZ = values[3].toFloat(),
                        gyroX = values[4].toFloat(), gyroY = values[5].toFloat(), gyroZ = values[6].toFloat()
                    )
                )
            }
        }
        return samples
    }

    /** Returns a FloatArray in FEATURE_ORDER, ready for the classifier. */
    fun extractFeatures(samples: List<SessionSample>): FloatArray {
        require(samples.size >= 2) { "Need at least 2 samples to compute jerk" }

        val accMag = samples.map {
            val dz = it.accZ - GRAVITY
            sqrt(it.accX * it.accX + it.accY * it.accY + dz * dz)
        }
        val gyroMag = samples.map {
            sqrt(it.gyroX * it.gyroX + it.gyroY * it.gyroY + it.gyroZ * it.gyroZ)
        }
        val jerk = (1 until accMag.size).map { accMag[it] - accMag[it - 1] }

        val accMagMean = accMag.average().toFloat()
        val accMagStd = stdDev(accMag, accMagMean)
        val accMagMax = accMag.max()

        val gyroMagMean = gyroMag.average().toFloat()
        val gyroMagStd = stdDev(gyroMag, gyroMagMean)
        val gyroMagMax = gyroMag.max()

        val jerkStd = stdDev(jerk, jerk.average().toFloat())
        val jerkMaxAbs = jerk.maxOf { abs(it) }

        val harshThreshold = accMagMean + 2 * accMagStd
        val harshEventRate = accMag.count { it > harshThreshold }.toFloat() / accMag.size

        return floatArrayOf(
            accMagMean, accMagStd, accMagMax,
            gyroMagMean, gyroMagStd, gyroMagMax,
            jerkStd, jerkMaxAbs, harshEventRate
        )
    }

    private fun stdDev(values: List<Float>, mean: Float): Float {
        val variance = values.sumOf { ((it - mean) * (it - mean)).toDouble() } / values.size
        return sqrt(variance).toFloat()
    }
}
