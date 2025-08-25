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
 * 超簡單的崩潰報告器
 * 使用最底層的方法保存崩潰報告
 */
object UltraSimpleCrashReporter {
    
    private const val TAG = "UltraSimpleCrashReporter"
    
    /**
     * 保存崩潰報告 - 最簡單的方法
     */
    fun saveCrashReport(context: Context, throwable: Throwable) {
        try {
            // 1. 立即記錄到logcat
            Log.e(TAG, "=== 崩潰開始 ===")
            Log.e(TAG, "異常類型: ${throwable.javaClass.simpleName}")
            Log.e(TAG, "異常消息: ${throwable.message}")
            Log.e(TAG, "堆疊追蹤:")
            throwable.printStackTrace()
            Log.e(TAG, "=== 崩潰結束 ===")
            
            // 2. 立即寫入系統錯誤流
            System.err.println("=== ULTRA_CRASH_START ===")
            System.err.println("時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            System.err.println("異常類型: ${throwable.javaClass.simpleName}")
            System.err.println("異常消息: ${throwable.message}")
            System.err.println("堆疊追蹤:")
            throwable.printStackTrace(System.err)
            System.err.println("=== ULTRA_CRASH_END ===")
            System.err.flush()
            
            // 3. 創建最簡單的報告內容
            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp))
            
            val simpleReport = """
CRASH_REPORT
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
            
            // 4. 嘗試多個位置保存
            val locations = listOf(
                context.filesDir,
                File(context.filesDir, "ultra_crash_reports"),
                context.getExternalFilesDir("ultra_crash_reports"),
                File(context.applicationInfo.dataDir, "ultra_crash_reports"),
                File(context.filesDir, "emergency_crash_reports")
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
                        val fileName = "ultra_crash_${dateStr}_${timestamp}.txt"
                        val file = File(location, fileName)
                        
                        // 使用最底層的方法寫入
                        val bytes = simpleReport.toByteArray(StandardCharsets.UTF_8)
                        
                        // 方法1: FileOutputStream
                        try {
                            FileOutputStream(file).use { fos ->
                                fos.write(bytes)
                                fos.flush()
                                fos.fd.sync()
                            }
                            savedCount++
                            Log.i(TAG, "方法1成功: ${file.absolutePath}")
                        } catch (e: Exception) {
                            Log.w(TAG, "方法1失敗: ${e.message}")
                            
                            // 方法2: 直接寫入
                            try {
                                file.writeBytes(bytes)
                                savedCount++
                                Log.i(TAG, "方法2成功: ${file.absolutePath}")
                            } catch (e2: Exception) {
                                Log.w(TAG, "方法2失敗: ${e2.message}")
                                
                                // 方法3: 使用writeText
                                try {
                                    file.writeText(simpleReport)
                                    savedCount++
                                    Log.i(TAG, "方法3成功: ${file.absolutePath}")
                                } catch (e3: Exception) {
                                    Log.w(TAG, "方法3失敗: ${e3.message}")
                                }
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "保存到 ${location.absolutePath} 完全失敗: ${e.message}")
                    }
                }
            }
            
            Log.i(TAG, "超簡單崩潰報告已保存到 $savedCount 個位置")
            System.err.println("ULTRA_CRASH_SAVED: $savedCount locations")
            System.err.flush()
            
        } catch (e: Exception) {
            Log.e(TAG, "超簡單崩潰報告保存失敗", e)
            System.err.println("ULTRA_CRASH_FAILED: ${e.message}")
            System.err.flush()
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
                File(context.filesDir, "ultra_crash_reports"),
                context.getExternalFilesDir("ultra_crash_reports"),
                File(context.applicationInfo.dataDir, "ultra_crash_reports"),
                File(context.filesDir, "emergency_crash_reports")
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && (file.name.startsWith("ultra_crash_") || file.name.startsWith("emergency_crash_")) && file.name.endsWith(".txt")
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
                File(context.filesDir, "ultra_crash_reports"),
                context.getExternalFilesDir("ultra_crash_reports"),
                File(context.applicationInfo.dataDir, "ultra_crash_reports"),
                File(context.filesDir, "emergency_crash_reports")
            )
            
            for (location in locations) {
                if (location != null && location.exists()) {
                    val files = location.listFiles { file ->
                        file.isFile && (file.name.startsWith("ultra_crash_") || file.name.startsWith("emergency_crash_")) && file.name.endsWith(".txt")
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
