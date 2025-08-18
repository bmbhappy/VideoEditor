package com.example.videoeditor.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object LogDisplayManager {
    
    private val logBuffer = mutableListOf<LogEntry>()
    private val listeners = mutableListOf<LogUpdateListener>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    interface LogUpdateListener {
        fun onLogUpdated(logs: List<LogEntry>)
    }
    
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String,
        val color: Int
    )
    
    fun addListener(listener: LogUpdateListener) {
        listeners.add(listener)
        // 立即發送當前日誌
        listener.onLogUpdated(logBuffer.toList())
    }
    
    fun removeListener(listener: LogUpdateListener) {
        listeners.remove(listener)
    }
    
    fun addLog(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val color = when (level) {
            "D" -> 0xFF4CAF50.toInt() // 綠色
            "I" -> 0xFF2196F3.toInt() // 藍色
            "W" -> 0xFFFF9800.toInt() // 橙色
            "E" -> 0xFFF44336.toInt() // 紅色
            else -> 0xFF9E9E9E.toInt() // 灰色
        }
        
        val entry = LogEntry(timestamp, level, tag, message, color)
        logBuffer.add(entry)
        
        // 限制日誌數量，避免記憶體溢出
        if (logBuffer.size > 1000) {
            logBuffer.removeAt(0)
        }
        
        // 通知所有監聽器
        listeners.forEach { it.onLogUpdated(logBuffer.toList()) }
        
        // 同時輸出到系統日誌
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message)
            "E" -> Log.e(tag, message)
        }
    }
    
    fun clearLogs() {
        logBuffer.clear()
        listeners.forEach { it.onLogUpdated(emptyList()) }
    }
    
    fun getLogs(): List<LogEntry> = logBuffer.toList()
}
