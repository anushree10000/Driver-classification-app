package com.example.drivingstylecollector

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileListAdapter(
    private val files: List<File>,
    private val onMenuClick: (View, File) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val filenameTextView: TextView = view.findViewById(R.id.filenameTextView)
        val menuButton: ImageButton = view.findViewById(R.id.menuButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.filenameTextView.text = file.name
        holder.menuButton.setOnClickListener {
            onMenuClick(it, file)
        }
    }

    override fun getItemCount(): Int = files.size
}
