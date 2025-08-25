package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

/**
 * 超強保證崩潰報告器
 * 專門處理 Finalizer 異常和其他極端崩潰情況
 */
object UltraGuaranteedCrashReporter {
    
    private const val TAG = "UltraGuaranteedCrashReporter"
    
    /**
     * 保存崩潰報告 - 使用最激進的方法確保成功
     */
    fun saveCrashReport(context: Context, throwable: Throwable) {
        try {
            // 1. 立即記錄到系統日誌
            Log.e(TAG, "=== 超強保證崩潰報告開始 ===")
            Log.e(TAG, "異常類型: ${throwable.javaClass.simpleName}")
            Log.e(TAG, "異常消息: ${throwable.message}")
            Log.e(TAG, "堆疊追蹤: ${Log.getStackTraceString(throwable)}")
            
            // 2. 立即輸出到 System.err
            System.err.println("=== ULTRA GUARANTEED CRASH REPORT START ===")
            System.err.println("Exception Type: ${throwable.javaClass.simpleName}")
            System.err.println("Exception Message: ${throwable.message}")
            throwable.printStackTrace(System.err)
            System.err.println("=== ULTRA GUARANTEED CRASH REPORT END ===")
            
            // 3. 生成簡單的崩潰報告內容
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val reportContent = """
=== 超強保證崩潰報告 ===
時間: $timestamp
異常類型: ${throwable.javaClass.simpleName}
異常消息: ${throwable.message ?: "無"}
堆疊追蹤:
${Log.getStackTraceString(throwable)}

系統信息:
可用記憶體: ${Runtime.getRuntime().freeMemory()} bytes
總記憶體: ${Runtime.getRuntime().totalMemory()} bytes
最大記憶體: ${Runtime.getRuntime().maxMemory()} bytes
線程名稱: ${Thread.currentThread().name}
線程ID: ${Thread.currentThread().id}
是否為Finalizer線程: ${Thread.currentThread().name.contains("Finalizer")}
            """.trimIndent()
            
            // 4. 嘗試保存到多個位置，使用多種方法
            val locations = listOf(
                File(context.filesDir, "ultra_guaranteed_crash_reports"),
                File(context.getExternalFilesDir("ultra_guaranteed_crash_reports") ?: context.filesDir, "reports"),
                File(context.applicationInfo.dataDir, "ultra_guaranteed_crash_reports"),
                File(context.filesDir, "emergency_ultra_reports"),
                File(context.filesDir, "last_resort_ultra_reports"),
                File(context.filesDir, "final_ultra_reports"),
                File(context.getExternalFilesDir(null), "ultra_crash_reports")
            )
            
            val fileName = "ultra_crash_${System.currentTimeMillis()}.txt"
            
            for (location in locations) {
                try {
                    // 確保目錄存在
                    if (!location.exists()) {
                        location.mkdirs()
                    }
                    
                    val file = File(location, fileName)
                    
                    // 方法1: 使用 FileOutputStream 和 fd.sync()
                    try {
                        FileOutputStream(file).use { fos ->
                            fos.write(reportContent.toByteArray(StandardCharsets.UTF_8))
                            fos.fd.sync()
                        }
                        Log.d(TAG, "成功保存到: ${file.absolutePath} (方法1)")
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "方法1失敗: ${e.message}")
                    }
                    
                    // 方法2: 使用 file.writeText()
                    try {
                        file.writeText(reportContent, StandardCharsets.UTF_8)
                        Log.d(TAG, "成功保存到: ${file.absolutePath} (方法2)")
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "方法2失敗: ${e.message}")
                    }
                    
                    // 方法3: 使用 file.writeBytes()
                    try {
                        file.writeBytes(reportContent.toByteArray(StandardCharsets.UTF_8))
                        Log.d(TAG, "成功保存到: ${file.absolutePath} (方法3)")
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "方法3失敗: ${e.message}")
                    }
                    
                    // 方法4: 使用 FileWriter
                    try {
                        FileWriter(file).use { writer ->
                            writer.write(reportContent)
                            writer.flush()
                        }
                        Log.d(TAG, "成功保存到: ${file.absolutePath} (方法4)")
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "方法4失敗: ${e.message}")
                    }
                    
                    // 方法5: 使用 PrintWriter
                    try {
                        PrintWriter(file).use { writer ->
                            writer.print(reportContent)
                            writer.flush()
                        }
                        Log.d(TAG, "成功保存到: ${file.absolutePath} (方法5)")
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "方法5失敗: ${e.message}")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "保存到 ${location.absolutePath} 失敗: ${e.message}")
                }
            }
            
            Log.e(TAG, "=== 超強保證崩潰報告完成 ===")
            
        } catch (e: Exception) {
            // 最後的緊急措施
            try {
                System.err.println("ULTRA CRASH REPORTER FAILED: ${e.message}")
                e.printStackTrace(System.err)
            } catch (_: Exception) {
                // 完全失敗，無法記錄
            }
        }
    }
    
    /**
     * 檢查是否有崩潰報告
     */
    fun hasCrashReports(context: Context): Boolean {
        val locations = listOf(
            File(context.filesDir, "ultra_guaranteed_crash_reports"),
            File(context.getExternalFilesDir("ultra_guaranteed_crash_reports") ?: context.filesDir, "reports"),
            File(context.applicationInfo.dataDir, "ultra_guaranteed_crash_reports"),
            File(context.filesDir, "emergency_ultra_reports"),
            File(context.filesDir, "last_resort_ultra_reports"),
            File(context.filesDir, "final_ultra_reports"),
            File(context.getExternalFilesDir(null), "ultra_crash_reports")
        )
        
        for (location in locations) {
            if (location.exists() && location.isDirectory) {
                val files = location.listFiles { file -> 
                    file.name.startsWith("ultra_crash_") && file.name.endsWith(".txt") 
                }
                if (files != null && files.isNotEmpty()) {
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * 獲取所有崩潰報告
     */
    fun getAllCrashReports(context: Context): List<File> {
        val reports = mutableListOf<File>()
        val locations = listOf(
            File(context.filesDir, "ultra_guaranteed_crash_reports"),
            File(context.getExternalFilesDir("ultra_guaranteed_crash_reports") ?: context.filesDir, "reports"),
            File(context.applicationInfo.dataDir, "ultra_guaranteed_crash_reports"),
            File(context.filesDir, "emergency_ultra_reports"),
            File(context.filesDir, "last_resort_ultra_reports"),
            File(context.filesDir, "final_ultra_reports"),
            File(context.getExternalFilesDir(null), "ultra_crash_reports")
        )
        
        for (location in locations) {
            if (location.exists() && location.isDirectory) {
                val files = location.listFiles { file -> 
                    file.name.startsWith("ultra_crash_") && file.name.endsWith(".txt") 
                }
                if (files != null) {
                    reports.addAll(files.toList())
                }
            }
        }
        
        return reports.sortedByDescending { it.lastModified() }
    }
    
    /**
     * 清除所有崩潰報告
     */
    fun clearAllCrashReports(context: Context) {
        val locations = listOf(
            File(context.filesDir, "ultra_guaranteed_crash_reports"),
            File(context.getExternalFilesDir("ultra_guaranteed_crash_reports") ?: context.filesDir, "reports"),
            File(context.applicationInfo.dataDir, "ultra_guaranteed_crash_reports"),
            File(context.filesDir, "emergency_ultra_reports"),
            File(context.filesDir, "last_resort_ultra_reports"),
            File(context.filesDir, "final_ultra_reports"),
            File(context.getExternalFilesDir(null), "ultra_crash_reports")
        )
        
        for (location in locations) {
            if (location.exists() && location.isDirectory) {
                val files = location.listFiles { file -> 
                    file.name.startsWith("ultra_crash_") && file.name.endsWith(".txt") 
                }
                if (files != null) {
                    for (file in files) {
                        try {
                            file.delete()
                        } catch (e: Exception) {
                            Log.w(TAG, "刪除檔案失敗: ${file.absolutePath}, ${e.message}")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 獲取堆疊追蹤字符串
     */
    private fun getStackTrace(throwable: Throwable): String {
        return try {
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            printWriter.close()
            stringWriter.toString()
        } catch (e: Exception) {
            "無法獲取堆疊追蹤: ${e.message}"
        }
    }
}
