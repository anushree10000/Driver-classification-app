package com.example.drivingstylecollector

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads driving_classifier.tflite + feature_scaler.json from assets/ and
 * classifies a session's engineered feature vector into a driving style
 * (Aggressive / Calm / Normal), plus per-class confidence.
 *
 * Model was trained offline in driver-classifier-training/train_and_export.py
 * on engineered features (see FeatureExtractor.kt for feature definitions,
 * which must exactly match what the model was trained on).
 */
class DrivingClassifier(context: Context) {

    data class Result(val label: String, val confidence: Float, val allScores: Map<String, Float>)

    private val interpreter: Interpreter
    private val featureMean: FloatArray
    private val featureScale: FloatArray
    private val labels: List<String>

    init {
        interpreter = Interpreter(loadModelFile(context, "driving_classifier.tflite"))

        val scalerJson = JSONObject(
            context.assets.open("feature_scaler.json").bufferedReader().use { it.readText() }
        )
        val meanArr = scalerJson.getJSONArray("mean")
        val scaleArr = scalerJson.getJSONArray("scale")
        featureMean = FloatArray(meanArr.length()) { meanArr.getDouble(it).toFloat() }
        featureScale = FloatArray(scaleArr.length()) { scaleArr.getDouble(it).toFloat() }

        val labelsArr = scalerJson.getJSONArray("labels")
        labels = (0 until labelsArr.length()).map { labelsArr.getString(it) }
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetName)
        FileInputStream(fd.fileDescriptor).use { inputStream ->
            val channel = inputStream.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    /** rawFeatures must be in FeatureExtractor.FEATURE_ORDER, unscaled. */
    fun classify(rawFeatures: FloatArray): Result {
        require(rawFeatures.size == featureMean.size) {
            "Expected ${featureMean.size} features, got ${rawFeatures.size}"
        }

        // standardize: (x - mean) / scale -- must match sklearn StandardScaler used in training
        val scaled = FloatArray(rawFeatures.size) { i ->
            (rawFeatures[i] - featureMean[i]) / featureScale[i]
        }

        val input = arrayOf(scaled)
        val output = arrayOf(FloatArray(labels.size))
        interpreter.run(input, output)

        val scores = output[0]
        val scoreMap = labels.indices.associate { labels[it] to scores[it] }
        val bestIdx = scores.indices.maxByOrNull { scores[it] } ?: 0

        return Result(
            label = labels[bestIdx],
            confidence = scores[bestIdx],
            allScores = scoreMap
        )
    }

    fun close() {
        interpreter.close()
    }
}
