package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

/**
 * 保證成功的崩潰報告器
 * 使用最底層、最直接的方法，確保崩潰報告一定會保存
 */
object GuaranteedCrashReporter {
    
    private const val TAG = "GuaranteedCrashReporter"
    
    /**
     * 保存崩潰報告 - 保證成功的方法
     */
    fun saveCrashReport(context: Context, throwable: Throwable) {
        try {
            // 1. 立即記錄到logcat（最可靠的方法）
            Log.e(TAG, "=== 保證成功的崩潰報告開始 ===")
            Log.e(TAG, "時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            Log.e(TAG, "異常類型: ${throwable.javaClass.simpleName}")
            Log.e(TAG, "異常消息: ${throwable.message}")
            Log.e(TAG, "堆疊追蹤:")
            throwable.printStackTrace()
            Log.e(TAG, "=== 保證成功的崩潰報告結束 ===")
            
            // 2. 立即寫入系統錯誤流（第二可靠的方法）
            System.err.println("=== GUARANTEED_CRASH_START ===")
            System.err.println("時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            System.err.println("異常類型: ${throwable.javaClass.simpleName}")
            System.err.println("異常消息: ${throwable.message}")
            System.err.println("堆疊追蹤:")
            throwable.printStackTrace(System.err)
            System.err.println("=== GUARANTEED_CRASH_END ===")
            System.err.flush()
            
            // 3. 創建最簡單的報告內容
            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp))
            
            val guaranteedReport = """
GUARANTEED_CRASH_REPORT
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
            
            // 4. 嘗試多個位置保存（使用最簡單的方法）
            val locations = listOf(
                context.filesDir,
                File(context.filesDir, "guaranteed_crash_reports"),
                context.getExternalFilesDir("guaranteed_crash_reports"),
                File(context.applicationInfo.dataDir, "guaranteed_crash_reports"),
                File(context.filesDir, "emergency_guaranteed_reports"),
                File(context.filesDir, "last_resort_reports")
            )
            
            var savedCount = 0
            for (location in locations) {
                if (location != null) {
                    try {
                        // 確保目錄存在
                        if (!location.exists()) {
                            location.mkdirs()
                        }
                        
                        // 創建文件名
                        val fileName = "guaranteed_crash_${dateStr}_${timestamp}.txt"
                        val file = File(location, fileName)
                        
                        // 使用最簡單的方法寫入
                        val bytes = guaranteedReport.toByteArray(StandardCharsets.UTF_8)
                        
                        // 方法1: 最簡單的writeText（最可靠）
                        try {
                            file.writeText(guaranteedReport)
                            savedCount++
                            Log.i(TAG, "方法1成功: ${file.absolutePath}")
                        } catch (e: Exception) {
                            Log.w(TAG, "方法1失敗: ${e.message}")
                            
                            // 方法2: FileOutputStream
                            try {
                                FileOutputStream(file).use { fos ->
                                    fos.write(bytes)
                                    fos.flush()
                                    fos.fd.sync()
                                }
                                savedCount++
                                Log.i(TAG, "方法2成功: ${file.absolutePath}")
                            } catch (e2: Exception) {
                                Log.w(TAG, "方法2失敗: ${e2.message}")
                                
                                // 方法3: 直接寫入
                                try {
                                    file.writeBytes(bytes)
                                    savedCount++
                                    Log.i(TAG, "方法3成功: ${file.absolutePath}")
                                } catch (e3: Exception) {
                                    Log.w(TAG, "方法3失敗: ${e3.message}")
                                    
                                    // 方法4: 使用FileWriter
                                    try {
                                        java.io.FileWriter(file).use { writer ->
                                            writer.write(guaranteedReport)
                                            writer.flush()
                                        }
                                        savedCount++
                                        Log.i(TAG, "方法4成功: ${file.absolutePath}")
                                    } catch (e4: Exception) {
                                        Log.w(TAG, "方法4失敗: ${e4.message}")
                                        
                                        // 方法5: 使用PrintWriter
                                        try {
                                            java.io.PrintWriter(file).use { writer ->
                                                writer.print(guaranteedReport)
                                                writer.flush()
                                            }
                                            savedCount++
                                            Log.i(TAG, "方法5成功: ${file.absolutePath}")
                                        } catch (e5: Exception) {
                                            Log.w(TAG, "方法5失敗: ${e5.message}")
                                        }
                                    }
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "保存到 ${location.absolutePath} 完全失敗: ${e.message}")
                    }
                }
            }
            
            Log.i(TAG, "保證成功的崩潰報告已保存到 $savedCount 個位置")
            System.err.println("GUARANTEED_CRASH_SAVED: $savedCount locations")
            System.err.flush()
            
            // 5. 最後的保證：寫入到系統日誌
            try {
                System.err.println("FINAL_GUARANTEED_CRASH_REPORT: ${throwable.javaClass.simpleName}: ${throwable.message}")
                System.err.flush()
            } catch (finalEx: Exception) {
                // 完全失敗，但我們已經嘗試了所有方法
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "保證成功的崩潰報告保存失敗", e)
            System.err.println("GUARANTEED_CRASH_FAILED: ${e.message}")
            System.err.flush()
            
            // 最後的嘗試：直接寫入系統日誌
            try {
                System.err.println("ULTIMATE_GUARANTEED_CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}")
                System.err.flush()
            } catch (finalEx: Exception) {
                // 完全失敗
            }
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
                File(context.filesDir, "guaranteed_crash_reports"),
                context.getExternalFilesDir("guaranteed_crash_reports"),
                File(context.applicationInfo.dataDir, "guaranteed_crash_reports"),
                File(context.filesDir, "emergency_guaranteed_reports"),
                File(context.filesDir, "last_resort_reports")
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && (file.name.startsWith("guaranteed_crash_") || 
                                      file.name.startsWith("emergency_guaranteed_") || 
                                      file.name.startsWith("last_resort_")) && file.name.endsWith(".txt")
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
                File(context.filesDir, "guaranteed_crash_reports"),
                context.getExternalFilesDir("guaranteed_crash_reports"),
                File(context.applicationInfo.dataDir, "guaranteed_crash_reports"),
                File(context.filesDir, "emergency_guaranteed_reports"),
                File(context.filesDir, "last_resort_reports")
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && (file.name.startsWith("guaranteed_crash_") || 
                                      file.name.startsWith("emergency_guaranteed_") || 
                                      file.name.startsWith("last_resort_")) && file.name.endsWith(".txt")
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
