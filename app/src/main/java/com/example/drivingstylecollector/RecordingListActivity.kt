package com.example.drivingstylecollector

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale

class RecordingListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileListAdapter
    private lateinit var classifier: DrivingClassifier

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_list)

        classifier = DrivingClassifier(this)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Get .csv files from Downloads folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val csvFiles = downloadsDir.listFiles { file -> file.extension == "csv" }?.toList() ?: emptyList()

        adapter = FileListAdapter(csvFiles, this::showPopupMenu)
        recyclerView.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }

    private fun showPopupMenu(view: View, file: File) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.file_options_menu)
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_visualize -> {
                    val intent = Intent(this, VisualizationActivity::class.java)
                    intent.putExtra("filePath", file.absolutePath)
                    startActivity(intent)
                    true
                }
                R.id.action_analyze -> {
                    analyzeFile(file)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun analyzeFile(file: File) {
        try {
            val samples = FeatureExtractor.readCsv(file)
            if (samples.size < 2) {
                Toast.makeText(this, "Recording too short to analyze", Toast.LENGTH_SHORT).show()
                return
            }
            val features = FeatureExtractor.extractFeatures(samples)
            val result = classifier.classify(features)

            val scoresText = result.allScores.entries
                .sortedByDescending { it.value }
                .joinToString("\n") { (label, score) ->
                    String.format(Locale.getDefault(), "%s: %.1f%%", label, score * 100)
                }

            AlertDialog.Builder(this)
                .setTitle("Driving Style: ${result.label}")
                .setMessage(
                    String.format(
                        Locale.getDefault(),
                        "Confidence: %.1f%%\n\n%s",
                        result.confidence * 100,
                        scoresText
                    )
                )
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Analysis failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
