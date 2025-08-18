package com.example.videoeditor.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.videoeditor.databinding.ItemVideoFileBinding
import com.example.videoeditor.models.VideoFile

class FileAdapter(
    private val onFileClick: (VideoFile) -> Unit,
    private val onShareClick: (VideoFile) -> Unit,
    private val onDeleteClick: (VideoFile) -> Unit,
    private val onSaveToGalleryClick: (VideoFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
    
    private var files: List<VideoFile> = emptyList()
    
    inner class FileViewHolder(private val binding: ItemVideoFileBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(videoFile: VideoFile) {
            binding.tvFileName.text = videoFile.name
            binding.tvFileSize.text = videoFile.getFormattedSize()
            binding.tvFileDate.text = videoFile.getFormattedDate()
            
            binding.root.setOnClickListener {
                onFileClick(videoFile)
            }
            
            binding.btnSaveToGallery.setOnClickListener {
                onSaveToGalleryClick(videoFile)
            }
            
            binding.btnShare.setOnClickListener {
                onShareClick(videoFile)
            }
            
            binding.btnDelete.setOnClickListener {
                onDeleteClick(videoFile)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemVideoFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(files[position])
    }
    
    override fun getItemCount(): Int = files.size
    
    fun updateFiles(newFiles: List<VideoFile>) {
        files = newFiles
        notifyDataSetChanged()
    }
}
