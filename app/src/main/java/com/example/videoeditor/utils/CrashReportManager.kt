package com.example.videoeditor.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 崩潰報告管理器
 * 負責保存、讀取和管理崩潰報告
 */
object CrashReportManager {
    
    private const val TAG = "CrashReportManager"
    private const val CRASH_REPORTS_DIR = "crash_reports"
    private const val MAX_REPORTS = 50 // 最多保存50個報告
    
    /**
     * 保存崩潰報告
     */
    fun saveCrashReport(context: Context, title: String, throwable: Throwable) {
        try {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp))
            
            val reportContent = generateCrashReport(title, throwable, timestamp)
            
            // 嘗試多個位置保存，優先級從高到低
            val savedLocations = mutableListOf<String>()
            
            // 1. 應用內部存儲（最可靠）
            val internalFile = File(context.filesDir, "$CRASH_REPORTS_DIR/crash_${dateStr}_${timestamp}.txt")
            if (saveToFileSync(internalFile, reportContent)) {
                savedLocations.add("內部存儲: ${internalFile.absolutePath}")
                Log.i(TAG, "崩潰報告已保存到內部存儲: ${internalFile.absolutePath}")
            }
            
            // 2. 應用數據目錄（備用）
            val dataDir = File(context.applicationInfo.dataDir, CRASH_REPORTS_DIR)
            if (dataDir.exists() || dataDir.mkdirs()) {
                val dataFile = File(dataDir, "crash_${dateStr}_${timestamp}.txt")
                if (saveToFileSync(dataFile, reportContent)) {
                    savedLocations.add("數據目錄: ${dataFile.absolutePath}")
                    Log.i(TAG, "崩潰報告已保存到數據目錄: ${dataFile.absolutePath}")
                }
            }
            
            // 3. 外部存儲（如果可用）
            try {
                val externalDir = context.getExternalFilesDir(CRASH_REPORTS_DIR)
                if (externalDir != null) {
                    val externalFile = File(externalDir, "crash_${dateStr}_${timestamp}.txt")
                    if (saveToFileSync(externalFile, reportContent)) {
                        savedLocations.add("外部存儲: ${externalFile.absolutePath}")
                        Log.i(TAG, "崩潰報告已保存到外部存儲: ${externalFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "外部存儲保存失敗", e)
            }
            
            // 4. 下載目錄（最後嘗試）
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir.exists() && downloadDir.canWrite()) {
                    val downloadFile = File(downloadDir, "VideoEditor_Crash_${dateStr}_${timestamp}.txt")
                    if (saveToFileSync(downloadFile, reportContent)) {
                        savedLocations.add("下載目錄: ${downloadFile.absolutePath}")
                        Log.i(TAG, "崩潰報告已保存到下載目錄: ${downloadFile.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "下載目錄保存失敗", e)
            }
            
            // 記錄保存結果
            if (savedLocations.isNotEmpty()) {
                Log.i(TAG, "崩潰報告已成功保存到 ${savedLocations.size} 個位置")
                // 強制同步到磁盤
                System.out.flush()
                System.err.flush()
            } else {
                Log.e(TAG, "無法保存崩潰報告到任何位置")
            }
            
            // 清理舊報告（異步執行，不阻塞崩潰處理）
            try {
                cleanupOldReports(context)
            } catch (e: Exception) {
                Log.w(TAG, "清理舊報告失敗", e)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "保存崩潰報告時發生錯誤", e)
            // 嘗試最基本的保存方式
            try {
                val emergencyFile = File(context.filesDir, "emergency_crash_${System.currentTimeMillis()}.txt")
                val emergencyContent = "緊急崩潰報告\n時間: ${Date()}\n異常: ${throwable.javaClass.simpleName}\n消息: ${throwable.message}\n堆疊: ${getStackTrace(throwable)}"
                saveToFileSync(emergencyFile, emergencyContent)
                Log.i(TAG, "緊急崩潰報告已保存: ${emergencyFile.absolutePath}")
            } catch (ex: Exception) {
                Log.e(TAG, "緊急保存也失敗", ex)
            }
        }
    }
    
    /**
     * 生成崩潰報告內容
     */
    private fun generateCrashReport(title: String, throwable: Throwable, timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date(timestamp))
        
        return """
            ========================================
            影片編輯器崩潰報告
            ========================================
            
            標題: $title
            時間: $dateStr
            時間戳: $timestamp
            
            ========================================
            異常信息
            ========================================
            類型: ${throwable.javaClass.simpleName}
            消息: ${throwable.message ?: "無"}
            
            ========================================
            堆疊追蹤
            ========================================
            ${getStackTrace(throwable)}
            
            ========================================
            系統信息
            ========================================
            Android版本: ${android.os.Build.VERSION.RELEASE}
            API級別: ${android.os.Build.VERSION.SDK_INT}
            設備: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            架構: ${android.os.Build.CPU_ABI}
            
            ========================================
            記憶體信息
            ========================================
            ${getMemoryInfo()}
            
            ========================================
            報告結束
            ========================================
        """.trimIndent()
    }
    
    /**
     * 獲取堆疊追蹤
     */
    private fun getStackTrace(throwable: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        return stringWriter.toString()
    }
    
    /**
     * 獲取記憶體信息
     */
    private fun getMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        return """
            最大記憶體: ${formatBytes(maxMemory)}
            總記憶體: ${formatBytes(totalMemory)}
            已用記憶體: ${formatBytes(usedMemory)}
            可用記憶體: ${formatBytes(freeMemory)}
            記憶體使用率: ${String.format("%.1f", (usedMemory.toDouble() / maxMemory * 100))}%
        """.trimIndent()
    }
    
    /**
     * 格式化字節數
     */
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }
    
    /**
     * 保存到文件（同步版本，確保數據寫入磁盤）
     */
    private fun saveToFileSync(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.fd.sync() // 強制同步到磁盤
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "同步保存到文件失敗: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * 保存到文件（異步版本，用於非崩潰情況）
     */
    private fun saveToFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer ->
                writer.write(content)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存到文件失敗: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * 清理舊報告
     */
    private fun cleanupOldReports(context: Context) {
        try {
            val internalDir = File(context.filesDir, CRASH_REPORTS_DIR)
            cleanupDirectory(internalDir)
            
            val externalDir = context.getExternalFilesDir(CRASH_REPORTS_DIR)
            if (externalDir != null) {
                cleanupDirectory(externalDir)
            }
            
            val dataDir = File(context.applicationInfo.dataDir, CRASH_REPORTS_DIR)
            cleanupDirectory(dataDir)
            
        } catch (e: Exception) {
            Log.e(TAG, "清理舊報告時發生錯誤", e)
        }
    }
    
    /**
     * 清理目錄中的舊文件
     */
    private fun cleanupDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        
        val files = directory.listFiles { file ->
            file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".txt")
        } ?: return
        
        if (files.size > MAX_REPORTS) {
            // 按修改時間排序，刪除最舊的文件
            files.sortBy { it.lastModified() }
            val filesToDelete = files.size - MAX_REPORTS
            
            for (i in 0 until filesToDelete) {
                if (files[i].delete()) {
                    Log.d(TAG, "已刪除舊報告: ${files[i].name}")
                }
            }
        }
    }
    
    /**
     * 獲取所有崩潰報告
     */
    fun getAllCrashReports(context: Context): List<CrashReport> {
        val reports = mutableListOf<CrashReport>()
        
        try {
            // 從多個位置讀取報告
            val locations = listOf(
                File(context.filesDir, CRASH_REPORTS_DIR),
                context.getExternalFilesDir(CRASH_REPORTS_DIR),
                File(context.applicationInfo.dataDir, CRASH_REPORTS_DIR),
                context.filesDir // 檢查根目錄的緊急報告
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && (
                            file.name.startsWith("crash_") || 
                            file.name.startsWith("emergency_crash_") ||
                            file.name.startsWith("last_resort_crash_") ||
                            file.name.startsWith("suspicious_exit_")
                        ) && file.name.endsWith(".txt")
                    } ?: continue
                    
                    for (file in files) {
                        try {
                            val content = file.readText()
                            val report = parseCrashReport(file, content)
                            if (report != null) {
                                reports.add(report)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "讀取報告失敗: ${file.absolutePath}", e)
                        }
                    }
                }
            }
            
            // 按時間戳排序，最新的在前
            reports.sortByDescending { it.timestamp }
            
        } catch (e: Exception) {
            Log.e(TAG, "獲取崩潰報告時發生錯誤", e)
        }
        
        return reports
    }
    
    /**
     * 解析崩潰報告
     */
    private fun parseCrashReport(file: File, content: String): CrashReport? {
        return try {
            val lines = content.lines()
            var title = ""
            var timestamp = 0L
            var exceptionType = ""
            var exceptionMessage = ""
            
            for (line in lines) {
                when {
                    line.startsWith("標題: ") -> title = line.substringAfter("標題: ")
                    line.startsWith("時間戳: ") -> timestamp = line.substringAfter("時間戳: ").toLongOrNull() ?: 0L
                    line.startsWith("類型: ") -> exceptionType = line.substringAfter("類型: ")
                    line.startsWith("消息: ") -> exceptionMessage = line.substringAfter("消息: ")
                }
            }
            
            // 如果解析失敗，使用文件名作為標題
            if (title.isEmpty()) {
                title = when {
                    file.name.startsWith("emergency_crash_") -> "緊急崩潰報告"
                    file.name.startsWith("last_resort_crash_") -> "最後嘗試崩潰報告"
                    file.name.startsWith("suspicious_exit_") -> "可疑退出報告"
                    file.name.startsWith("crash_") -> "崩潰報告"
                    else -> "未知崩潰報告"
                }
            }
            
            // 如果時間戳為0，使用文件修改時間
            if (timestamp == 0L) {
                timestamp = file.lastModified()
            }
            
            CrashReport(
                file = file,
                title = title,
                timestamp = timestamp,
                exceptionType = exceptionType,
                exceptionMessage = exceptionMessage,
                content = content
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析崩潰報告失敗", e)
            null
        }
    }
    
    /**
     * 刪除崩潰報告
     */
    fun deleteCrashReport(report: CrashReport): Boolean {
        return try {
            val deleted = report.file.delete()
            if (deleted) {
                Log.d(TAG, "已刪除崩潰報告: ${report.file.name}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "刪除崩潰報告失敗", e)
            false
        }
    }
    
    /**
     * 清空所有崩潰報告
     */
    fun clearAllCrashReports(context: Context): Int {
        var deletedCount = 0
        
        try {
            val locations = listOf(
                File(context.filesDir, CRASH_REPORTS_DIR),
                context.getExternalFilesDir(CRASH_REPORTS_DIR),
                File(context.applicationInfo.dataDir, CRASH_REPORTS_DIR)
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".txt")
                    } ?: continue
                    
                    for (file in files) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            }
            
            Log.d(TAG, "已清空 $deletedCount 個崩潰報告")
            
        } catch (e: Exception) {
            Log.e(TAG, "清空崩潰報告時發生錯誤", e)
        }
        
        return deletedCount
    }
    
    /**
     * 崩潰報告數據類
     */
    data class CrashReport(
        val file: File,
        val title: String,
        val timestamp: Long,
        val exceptionType: String,
        val exceptionMessage: String,
        val content: String
    ) {
        val formattedDate: String
            get() {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                return dateFormat.format(Date(timestamp))
            }
        
        val shortTitle: String
            get() = if (title.length > 30) "${title.take(30)}..." else title
    }
}
