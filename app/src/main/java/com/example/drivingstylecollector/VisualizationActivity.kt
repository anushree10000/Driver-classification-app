package com.example.drivingstylecollector

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class VisualizationActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visualization)

        lineChart = findViewById(R.id.lineChart)

        val filePath = intent.getStringExtra("filePath")

        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                plotCsvData(file)
            } else {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun plotCsvData(file: File) {
        val accX = ArrayList<Entry>()
        val accY = ArrayList<Entry>()
        val accZ = ArrayList<Entry>()

        val gyroX = ArrayList<Entry>()
        val gyroY = ArrayList<Entry>()
        val gyroZ = ArrayList<Entry>()

        var lineNumber = 0

        try {
            val reader = BufferedReader(FileReader(file))
            var line: String?

            // Skip header
            reader.readLine()

            while (reader.readLine().also { line = it } != null) {
                val values = line!!.split(",")
                if (values.size < 7) continue

                val timestamp = lineNumber.toFloat() // X-axis
                accX.add(Entry(timestamp, values[1].toFloat()))
                accY.add(Entry(timestamp, values[2].toFloat()))
                accZ.add(Entry(timestamp, values[3].toFloat()))
                gyroX.add(Entry(timestamp, values[4].toFloat()))
                gyroY.add(Entry(timestamp, values[5].toFloat()))
                gyroZ.add(Entry(timestamp, values[6].toFloat()))

                lineNumber++
            }

            val accXSet = LineDataSet(accX, "Acc X").apply { color = Color.RED }
            val accYSet = LineDataSet(accY, "Acc Y").apply { color = Color.GREEN }
            val accZSet = LineDataSet(accZ, "Acc Z").apply { color = Color.BLUE }

            val gyroXSet = LineDataSet(gyroX, "Gyro X").apply { color = Color.MAGENTA }
            val gyroYSet = LineDataSet(gyroY, "Gyro Y").apply { color = Color.CYAN }
            val gyroZSet = LineDataSet(gyroZ, "Gyro Z").apply { color = Color.YELLOW }

            val lineData = LineData(accXSet, accYSet, accZSet, gyroXSet, gyroYSet, gyroZSet)

            lineChart.data = lineData
            lineChart.description.isEnabled = false
            lineChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            lineChart.invalidate() // refresh chart

        } catch (e: Exception) {
            Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
