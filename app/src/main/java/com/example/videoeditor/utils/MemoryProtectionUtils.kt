package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File

object MemoryProtectionUtils {
    
    private const val TAG = "MemoryProtectionUtils"
    
    // 檔案大小限制
    private const val MAX_VIDEO_SIZE = 300L * 1024 * 1024 // 300MB
    private const val MAX_AUDIO_SIZE = 50L * 1024 * 1024  // 50MB
    private const val MAX_IMAGE_SIZE = 10L * 1024 * 1024  // 10MB
    
    // 時長限制
    private const val MAX_VIDEO_DURATION_MS = 15L * 60 * 1000 // 15分鐘
    private const val MAX_AUDIO_DURATION_MS = 5L * 60 * 1000  // 5分鐘
    
    // 記憶體使用率限制
    private const val MAX_MEMORY_USAGE = 0.8 // 80%
    private const val CRITICAL_MEMORY_USAGE = 0.9 // 90%
    
    /**
     * 檢查檔案是否適合處理
     */
    fun checkFileSuitability(
        context: Context,
        filePath: String,
        fileType: FileType
    ): Boolean {
        return try {
            val file = File(filePath)
            
            // 基本檢查
            if (!file.exists()) {
                showError(context, "檔案不存在")
                return false
            }
            
            if (!file.canRead()) {
                showError(context, "檔案無法讀取，請檢查權限")
                return false
            }
            
            // 檔案大小檢查
            val maxSize = when (fileType) {
                FileType.VIDEO -> MAX_VIDEO_SIZE
                FileType.AUDIO -> MAX_AUDIO_SIZE
                FileType.IMAGE -> MAX_IMAGE_SIZE
            }
            
            if (file.length() > maxSize) {
                val maxSizeStr = formatFileSize(maxSize)
                showError(context, "${fileType.displayName}檔案過大，最大支援 $maxSizeStr")
                return false
            }
            
            // 磁碟空間檢查
            val requiredSpace = file.length() * 3
            val availableSpace = file.parentFile?.freeSpace ?: 0L
            
            if (availableSpace < requiredSpace) {
                val requiredStr = formatFileSize(requiredSpace)
                showError(context, "磁碟空間不足，需要至少 $requiredStr")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "檔案檢查失敗: ${e.message}")
            showError(context, "檔案檢查失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 檢查多個檔案的適用性
     */
    fun checkMultipleFilesSuitability(
        context: Context,
        files: List<Pair<String, FileType>>
    ): Boolean {
        return try {
            var totalSize = 0L
            var requiredSpace = 0L
            
            for ((filePath, fileType) in files) {
                val file = File(filePath)
                
                if (!file.exists() || !file.canRead()) {
                    showError(context, "檔案 ${file.name} 不存在或無法讀取")
                    return false
                }
                
                totalSize += file.length()
                requiredSpace += file.length() * 3
            }
            
            // 檢查總大小限制
            val maxTotalSize = MAX_VIDEO_SIZE + MAX_AUDIO_SIZE
            if (totalSize > maxTotalSize) {
                showError(context, "檔案總大小過大，請選擇較小的檔案")
                return false
            }
            
            // 檢查磁碟空間
            val firstFile = File(files.first().first)
            val availableSpace = firstFile.parentFile?.freeSpace ?: 0L
            
            if (availableSpace < requiredSpace) {
                val requiredStr = formatFileSize(requiredSpace)
                showError(context, "磁碟空間不足，需要至少 $requiredStr")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "多檔案檢查失敗: ${e.message}")
            showError(context, "檔案檢查失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 檢查時長限制
     */
    fun checkDurationLimit(
        context: Context,
        durationMs: Long,
        mediaType: MediaType
    ): Boolean {
        val maxDuration = when (mediaType) {
            MediaType.VIDEO -> MAX_VIDEO_DURATION_MS
            MediaType.AUDIO -> MAX_AUDIO_DURATION_MS
        }
        
        if (durationMs > maxDuration) {
            val maxDurationStr = formatDuration(maxDuration)
            showError(context, "${mediaType.displayName}過長，最大支援 $maxDurationStr")
            return false
        }
        
        return true
    }
    
    /**
     * 檢查記憶體使用情況
     */
    fun checkMemoryUsage(context: Context): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
        
        Log.d(TAG, "記憶體使用率: ${(memoryUsage * 100).toInt()}%, 可用記憶體: ${formatFileSize(maxMemory - usedMemory)}")
        
        if (memoryUsage > MAX_MEMORY_USAGE) {
            showError(context, "系統記憶體不足，請關閉其他應用程式後重試")
            return false
        }
        
        return true
    }
    
    /**
     * 強制垃圾回收
     */
    fun forceGarbageCollection() {
        try {
            System.gc()
            Log.d(TAG, "強制垃圾回收完成")
        } catch (e: Exception) {
            Log.e(TAG, "垃圾回收失敗: ${e.message}")
        }
    }
    
    /**
     * 獲取記憶體使用信息
     */
    fun getMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
        
        return "記憶體使用率: ${(memoryUsage * 100).toInt()}%, " +
               "已使用: ${formatFileSize(usedMemory)}, " +
               "可用: ${formatFileSize(maxMemory - usedMemory)}"
    }
    
    /**
     * 格式化檔案大小
     */
    fun formatFileSize(sizeInBytes: Long): String {
        return when {
            sizeInBytes < 1024 -> "$sizeInBytes B"
            sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
            sizeInBytes < 1024 * 1024 * 1024 -> "${sizeInBytes / (1024 * 1024)} MB"
            else -> "${sizeInBytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 格式化時長
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d分%d秒", minutes, remainingSeconds)
    }
    
    private fun showError(context: Context, message: String) {
        Log.w(TAG, message)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    enum class FileType(val displayName: String) {
        VIDEO("影片"),
        AUDIO("音訊"),
        IMAGE("圖片")
    }
    
    enum class MediaType(val displayName: String) {
        VIDEO("影片"),
        AUDIO("音訊")
    }
}
