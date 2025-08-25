package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 崩潰報告分析器
 * 幫助分析崩潰原因和提供調試建議
 */
object CrashReportAnalyzer {
    
    private const val TAG = "CrashReportAnalyzer"
    
    /**
     * 分析所有崩潰報告
     */
    fun analyzeAllCrashReports(context: Context): String {
        val reports = GuaranteedCrashReporter.getAllCrashReports(context)
        if (reports.isEmpty()) {
            return "沒有找到崩潰報告"
        }
        
        val analysis = StringBuilder()
        analysis.append("=== 崩潰報告分析 ===\n")
        analysis.append("總共找到 ${reports.size} 個崩潰報告\n\n")
        
        for ((index, file) in reports.withIndex()) {
            analysis.append("報告 ${index + 1}:\n")
            analysis.append(analyzeSingleReport(file))
            analysis.append("\n" + "=".repeat(50) + "\n\n")
        }
        
        return analysis.toString()
    }
    
    /**
     * 分析單個崩潰報告
     */
    fun analyzeSingleReport(file: File): String {
        try {
            val content = file.readText()
            val analysis = StringBuilder()
            
            // 基本信息
            analysis.append("文件名: ${file.name}\n")
            analysis.append("文件大小: ${file.length()} bytes\n")
            analysis.append("修改時間: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}\n")
            analysis.append("文件路徑: ${file.absolutePath}\n\n")
            
            // 解析報告內容
            val lines = content.lines()
            var exceptionType = "未知"
            var exceptionMessage = "無"
            var timestamp = ""
            var stackTrace = StringBuilder()
            var inStackTrace = false
            
            for (line in lines) {
                when {
                    line.contains("時間:") -> {
                        timestamp = line.substringAfter("時間:").trim()
                        analysis.append("崩潰時間: $timestamp\n")
                    }
                    line.contains("異常類型:") -> {
                        exceptionType = line.substringAfter("異常類型:").trim()
                        analysis.append("異常類型: $exceptionType\n")
                    }
                    line.contains("異常消息:") -> {
                        exceptionMessage = line.substringAfter("異常消息:").trim()
                        if (exceptionMessage != "無") {
                            analysis.append("異常消息: $exceptionMessage\n")
                        }
                    }
                    line.contains("堆疊追蹤:") -> {
                        inStackTrace = true
                        analysis.append("\n堆疊追蹤:\n")
                    }
                    inStackTrace && line.isNotEmpty() -> {
                        stackTrace.append(line).append("\n")
                    }
                }
            }
            
            // 添加堆疊追蹤
            if (stackTrace.isNotEmpty()) {
                analysis.append(stackTrace.toString())
            }
            
            // 分析崩潰原因
            analysis.append("\n=== 崩潰原因分析 ===\n")
            analysis.append(analyzeCrashCause(exceptionType, exceptionMessage, stackTrace.toString()))
            
            // 提供解決建議
            analysis.append("\n=== 解決建議 ===\n")
            analysis.append(provideSolutions(exceptionType, exceptionMessage))
            
            return analysis.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "分析崩潰報告失敗: ${file.absolutePath}", e)
            return "分析失敗: ${e.message}"
        }
    }
    
    /**
     * 分析崩潰原因
     */
    private fun analyzeCrashCause(exceptionType: String, exceptionMessage: String, stackTrace: String): String {
        val analysis = StringBuilder()
        
        when (exceptionType) {
            "OutOfMemoryError" -> {
                analysis.append("🔴 記憶體不足錯誤\n")
                analysis.append("原因: 應用程式嘗試分配超過可用記憶體的空間\n")
                if (exceptionMessage.contains("bitmap")) {
                    analysis.append("具體原因: 可能是載入過大的圖片或影片縮圖\n")
                } else if (exceptionMessage.contains("allocation")) {
                    analysis.append("具體原因: 可能是處理大檔案時記憶體不足\n")
                }
            }
            "NullPointerException" -> {
                analysis.append("🔴 空指針異常\n")
                analysis.append("原因: 嘗試訪問null對象的方法或屬性\n")
                analysis.append("建議: 檢查相關對象是否為null\n")
            }
            "IllegalArgumentException" -> {
                analysis.append("🔴 非法參數異常\n")
                analysis.append("原因: 傳遞了無效的參數值\n")
                analysis.append("建議: 檢查參數的有效性\n")
            }
            "RuntimeException" -> {
                analysis.append("🔴 運行時異常\n")
                analysis.append("原因: 程式運行時發生的錯誤\n")
                analysis.append("建議: 檢查異常消息和堆疊追蹤\n")
            }
            "SecurityException" -> {
                analysis.append("🔴 安全異常\n")
                analysis.append("原因: 權限不足或安全限制\n")
                analysis.append("建議: 檢查應用權限設置\n")
            }
            else -> {
                analysis.append("🔴 其他異常: $exceptionType\n")
                analysis.append("原因: 需要進一步分析\n")
            }
        }
        
        // 分析堆疊追蹤
        if (stackTrace.contains("MediaCodec") || stackTrace.contains("MediaExtractor")) {
            analysis.append("📹 涉及媒體處理: 可能是影片/音訊處理問題\n")
        }
        if (stackTrace.contains("Bitmap") || stackTrace.contains("Canvas")) {
            analysis.append("🖼️ 涉及圖像處理: 可能是圖片載入或繪製問題\n")
        }
        if (stackTrace.contains("File") || stackTrace.contains("IOException")) {
            analysis.append("📁 涉及檔案操作: 可能是檔案讀寫問題\n")
        }
        
        return analysis.toString()
    }
    
    /**
     * 提供解決建議
     */
    private fun provideSolutions(exceptionType: String, exceptionMessage: String): String {
        val solutions = StringBuilder()
        
        when (exceptionType) {
            "OutOfMemoryError" -> {
                solutions.append("💡 解決方案:\n")
                solutions.append("1. 減少同時載入的圖片/影片數量\n")
                solutions.append("2. 使用較小的圖片解析度\n")
                solutions.append("3. 及時釋放不需要的資源\n")
                solutions.append("4. 考慮使用圖片壓縮\n")
                solutions.append("5. 檢查是否有記憶體洩漏\n")
            }
            "NullPointerException" -> {
                solutions.append("💡 解決方案:\n")
                solutions.append("1. 添加null檢查\n")
                solutions.append("2. 使用安全調用操作符 (?.)\n")
                solutions.append("3. 提供默認值\n")
                solutions.append("4. 檢查對象初始化\n")
            }
            "IllegalArgumentException" -> {
                solutions.append("💡 解決方案:\n")
                solutions.append("1. 驗證輸入參數\n")
                solutions.append("2. 提供參數範圍檢查\n")
                solutions.append("3. 使用默認值處理無效輸入\n")
            }
            "RuntimeException" -> {
                solutions.append("💡 解決方案:\n")
                solutions.append("1. 查看具體的異常消息\n")
                solutions.append("2. 檢查相關代碼邏輯\n")
                solutions.append("3. 添加適當的錯誤處理\n")
            }
            else -> {
                solutions.append("💡 一般解決方案:\n")
                solutions.append("1. 查看完整的堆疊追蹤\n")
                solutions.append("2. 檢查相關代碼\n")
                solutions.append("3. 添加日誌記錄\n")
                solutions.append("4. 使用調試器追蹤問題\n")
            }
        }
        
        solutions.append("\n🔧 調試建議:\n")
        solutions.append("1. 在Android Studio中使用Logcat查看詳細日誌\n")
        solutions.append("2. 使用Memory Profiler監控記憶體使用\n")
        solutions.append("3. 設置斷點進行調試\n")
        solutions.append("4. 檢查設備的可用記憶體\n")
        
        return solutions.toString()
    }
    
    /**
     * 獲取崩潰統計信息
     */
    fun getCrashStatistics(context: Context): String {
        val reports = GuaranteedCrashReporter.getAllCrashReports(context)
        if (reports.isEmpty()) {
            return "沒有崩潰報告"
        }
        
        val stats = StringBuilder()
        stats.append("=== 崩潰統計 ===\n")
        stats.append("總崩潰次數: ${reports.size}\n")
        
        val exceptionTypes = mutableMapOf<String, Int>()
        val timeDistribution = mutableMapOf<String, Int>()
        
        for (file in reports) {
            try {
                val content = file.readText()
                val lines = content.lines()
                
                // 統計異常類型
                for (line in lines) {
                    if (line.contains("異常類型:")) {
                        val type = line.substringAfter("異常類型:").trim()
                        exceptionTypes[type] = exceptionTypes.getOrDefault(type, 0) + 1
                        break
                    }
                }
                
                // 統計時間分布
                val hour = SimpleDateFormat("HH", Locale.getDefault()).format(Date(file.lastModified()))
                timeDistribution[hour] = timeDistribution.getOrDefault(hour, 0) + 1
                
            } catch (e: Exception) {
                Log.w(TAG, "統計崩潰報告失敗: ${file.absolutePath}", e)
            }
        }
        
        stats.append("\n異常類型分布:\n")
        exceptionTypes.entries.sortedByDescending { it.value }.forEach { (type, count) ->
            stats.append("  $type: $count 次\n")
        }
        
        stats.append("\n時間分布:\n")
        timeDistribution.entries.sortedBy { it.key }.forEach { (hour, count) ->
            stats.append("  ${hour}:00-${hour}:59: $count 次\n")
        }
        
        return stats.toString()
    }
}
