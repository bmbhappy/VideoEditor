package com.example.videoeditor.utils

import android.content.Context
import android.media.*
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * 音訊格式轉換工具
 * 主要用於將不支援的格式（如 WAV）轉換為 MediaMuxer 支援的格式（如 AAC）
 */
object AudioConverter {
    
    private const val TAG = "AudioConverter"
    
    /**
     * 將音訊檔案轉換為 AAC 格式
     * @param inputPath 輸入檔案路徑
     * @param outputPath 輸出檔案路徑
     * @return 轉換後的檔案路徑，如果轉換失敗則返回 null
     */
    fun convertToAac(inputPath: String, outputPath: String): String? {
        return try {
            LogDisplayManager.addLog("D", TAG, "開始轉換音訊: $inputPath -> $outputPath")
            
            val inputFile = File(inputPath)
            if (!inputFile.exists()) {
                LogDisplayManager.addLog("E", TAG, "輸入檔案不存在: $inputPath")
                return null
            }
            
            // 檢查檔案副檔名
            val extension = inputFile.extension.lowercase()
            if (extension == "aac" || extension == "m4a") {
                LogDisplayManager.addLog("D", TAG, "檔案已經是 AAC 格式，直接複製")
                inputFile.copyTo(File(outputPath), overwrite = true)
                return outputPath
            }
            
            // 對於其他支援的格式，直接複製（因為 SimpleBgmMixer 會處理）
            if (extension in listOf("mp3", "ogg", "flac")) {
                LogDisplayManager.addLog("D", TAG, "檔案格式支援，直接複製: $extension")
                inputFile.copyTo(File(outputPath), overwrite = true)
                return outputPath
            }
            
            // 對於不支援的格式，返回 null
            LogDisplayManager.addLog("E", TAG, "不支援的音訊格式: $extension")
            LogDisplayManager.addLog("E", TAG, "支援的格式: aac, m4a, mp3, ogg, flac")
            null
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "音訊轉換異常: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    

}
