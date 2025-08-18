package com.example.videoeditor.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.videoeditor.databinding.ItemLogBinding
import com.example.videoeditor.utils.LogDisplayManager

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {
    
    private var logs: List<LogDisplayManager.LogEntry> = emptyList()
    
    inner class LogViewHolder(private val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(logEntry: LogDisplayManager.LogEntry) {
            binding.tvTimestamp.text = logEntry.timestamp
            binding.tvLevel.text = logEntry.level
            binding.tvTag.text = logEntry.tag
            binding.tvMessage.text = logEntry.message
            
            // 設置顏色
            binding.tvLevel.setTextColor(logEntry.color)
            binding.tvTag.setTextColor(logEntry.color)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }
    
    override fun getItemCount(): Int = logs.size
    
    fun updateLogs(newLogs: List<LogDisplayManager.LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
