package com.example.drivingstylecollector

import android.content.ContentValues
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var accFiltered: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var gyroFiltered: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var isLogging = false
    private var lastLogTime: Long = 0
    private val logIntervalMillis = 100L
    private var outputStream: OutputStream? = null

    private var latestAccData: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var latestGyroData: FloatArray = floatArrayOf(0f, 0f, 0f)
    private var accDataReady = false
    private var gyroDataReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val toggleButton = findViewById<Button>(R.id.toggleLogging)
        toggleButton.setOnClickListener {
            if (!isLogging) {
                startLogging()
                toggleButton.text = "Stop Logging"
                Toast.makeText(this, "Logging started", Toast.LENGTH_SHORT).show()
            } else {
                stopLogging()
                toggleButton.text = "Start Logging"
                Toast.makeText(this, "Logging stopped", Toast.LENGTH_SHORT).show()
            }
        }

        val goToRecordingsButton = findViewById<ImageButton>(R.id.goToRecordingsButton)
        goToRecordingsButton.setOnClickListener {
            val intent = Intent(this, RecordingListActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && isLogging && outputStream != null) {
            val now = System.currentTimeMillis()

            val filtered = lowPass(
                event.values.clone(),
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) accFiltered else gyroFiltered
            )

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    accFiltered = filtered
                    latestAccData = filtered.clone()
                    accDataReady = true
                }
                Sensor.TYPE_GYROSCOPE -> {
                    gyroFiltered = filtered
                    latestGyroData = filtered.clone()
                    gyroDataReady = true
                }
            }

            if (now - lastLogTime >= logIntervalMillis && accDataReady && gyroDataReady) {
                lastLogTime = now
                val data = "$now,${latestAccData[0]},${latestAccData[1]},${latestAccData[2]},${latestGyroData[0]},${latestGyroData[1]},${latestGyroData[2]}"
                Log.d("SensorData", data)
                writeToCSV(data)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startLogging() {
        isLogging = true
        val fileName = "sensor_data_${getTimeStamp()}.csv"

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = uri?.let { contentResolver.openOutputStream(it, "w") }

            if (outputStream == null) throw Exception("OutputStream is null")

            val header = "timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z\n"
            outputStream?.write(header.toByteArray())
            outputStream?.flush()

            accDataReady = false
            gyroDataReady = false

        } catch (e: Exception) {
            Log.e("FileWrite", "Failed to create CSV file: ${e.message}", e)
            Toast.makeText(this, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
            isLogging = false
        }
    }

    private fun stopLogging() {
        isLogging = false
        outputStream?.close()
        outputStream = null
        accDataReady = false
        gyroDataReady = false
    }

    private fun writeToCSV(data: String) {
        try {
            outputStream?.write((data + "\n").toByteArray())
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e("FileWrite", "Failed to write to CSV: ${e.message}", e)
            Toast.makeText(this, "Write failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray): FloatArray {
        val alpha = 0.25f
        for (i in input.indices) {
            output[i] = output[i] + alpha * (input[i] - output[i])
        }
        return output
    }

    private fun getTimeStamp(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return sdf.format(Date())
    }
}
