package com.example.videoeditor.models

data class VideoFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long
) {
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    fun getFormattedDate(): String {
        val date = java.util.Date(lastModified)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return formatter.format(date)
    }
}
