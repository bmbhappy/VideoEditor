package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * 簡單的崩潰報告器
 * 使用最直接的方式保存崩潰報告
 */
object SimpleCrashReporter {
    
    private const val TAG = "SimpleCrashReporter"
    
    /**
     * 保存崩潰報告
     */
    fun saveCrashReport(context: Context, throwable: Throwable) {
        try {
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp))
            
            val reportContent = """
                崩潰報告
                時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))}
                時間戳: $timestamp
                異常類型: ${throwable.javaClass.simpleName}
                異常消息: ${throwable.message ?: "無"}
                
                堆疊追蹤:
                ${getStackTrace(throwable)}
                
                系統信息:
                Android版本: ${android.os.Build.VERSION.RELEASE}
                API級別: ${android.os.Build.VERSION.SDK_INT}
                設備: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
            """.trimIndent()
            
            // 嘗試多個位置保存
            val locations = listOf(
                context.filesDir,
                File(context.filesDir, "crash_reports"),
                context.getExternalFilesDir("crash_reports"),
                File(context.applicationInfo.dataDir, "crash_reports")
            )
            
            var savedCount = 0
            for (location in locations) {
                if (location != null) {
                    try {
                        if (!location.exists()) {
                            location.mkdirs()
                        }
                        
                        val fileName = "crash_${dateStr}_${timestamp}.txt"
                        val file = File(location, fileName)
                        
                        // 使用FileOutputStream直接寫入
                        FileOutputStream(file).use { fos ->
                            fos.write(reportContent.toByteArray())
                            fos.flush()
                            fos.fd.sync() // 強制同步到磁盤
                        }
                        
                        savedCount++
                        Log.i(TAG, "崩潰報告已保存: ${file.absolutePath}")
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "保存到 ${location.absolutePath} 失敗", e)
                    }
                }
            }
            
            Log.i(TAG, "崩潰報告已保存到 $savedCount 個位置")
            
        } catch (e: Exception) {
            Log.e(TAG, "保存崩潰報告失敗", e)
        }
    }
    
    /**
     * 獲取堆疊追蹤
     */
    private fun getStackTrace(throwable: Throwable): String {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
    
    /**
     * 檢查是否有崩潰報告
     */
    fun hasCrashReports(context: Context): Boolean {
        try {
            val locations = listOf(
                context.filesDir,
                File(context.filesDir, "crash_reports"),
                context.getExternalFilesDir("crash_reports"),
                File(context.applicationInfo.dataDir, "crash_reports")
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".txt")
                    }
                    if (files != null && files.isNotEmpty()) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "檢查崩潰報告失敗", e)
        }
        return false
    }
    
    /**
     * 獲取所有崩潰報告
     */
    fun getAllCrashReports(context: Context): List<File> {
        val reports = mutableListOf<File>()
        
        try {
            val locations = listOf(
                context.filesDir,
                File(context.filesDir, "crash_reports"),
                context.getExternalFilesDir("crash_reports"),
                File(context.applicationInfo.dataDir, "crash_reports")
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".txt")
                    }
                    if (files != null) {
                        reports.addAll(files.toList())
                    }
                }
            }
            
            // 按修改時間排序，最新的在前
            reports.sortByDescending { it.lastModified() }
            
        } catch (e: Exception) {
            Log.e(TAG, "獲取崩潰報告失敗", e)
        }
        
        return reports
    }
    
    /**
     * 清除所有崩潰報告
     */
    fun clearAllCrashReports(context: Context) {
        try {
            val reports = getAllCrashReports(context)
            for (report in reports) {
                try {
                    report.delete()
                    Log.i(TAG, "已刪除崩潰報告: ${report.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "刪除崩潰報告失敗: ${report.absolutePath}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除崩潰報告失敗", e)
        }
    }
}
