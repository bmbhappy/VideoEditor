package com.example.videoeditor.engine

import android.content.Context
import android.media.*
import android.media.MediaCodec.*
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class VideoProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoProcessor"
        private const val TIMEOUT_US = 10000L
        private const val LARGE_FILE_THRESHOLD_MB = 100 // 100MB 以上使用大檔案處理器
    }
    
    // 大檔案處理器實例
    private val largeVideoProcessor = LargeVideoProcessor()
    
    interface ProcessingCallback {
        fun onProgress(progress: Float)
        fun onSuccess(outputPath: String)
        fun onError(error: String)
    }
    
    suspend fun trimVideo(
        inputUri: Uri,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "=== 開始裁剪影片 ===")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "輸入 URI: $inputUri")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "裁剪時間範圍: ${startTimeMs}ms - ${endTimeMs}ms")
            
            // 檢查是否為大檔案
            val inputPath = getFilePathFromUri(inputUri)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "解析的檔案路徑: $inputPath")
            
            if (inputPath != null) {
                val file = File(inputPath)
                val fileSizeMB = file.length() / (1024 * 1024)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案大小: ${fileSizeMB}MB")
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案存在: ${file.exists()}")
                
                if (largeVideoProcessor.isSuitableForLargeFileProcessing(inputPath)) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檢測到大檔案，使用大檔案處理器")
                    processLargeVideoTrim(inputPath, startTimeMs, endTimeMs, callback)
                    return@withContext
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案大小不足100MB，使用普通處理器")
                }
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "無法獲取檔案路徑，使用普通處理器")
            }
            
            // 使用原有的處理方式
            processNormalVideoTrim(inputUri, startTimeMs, endTimeMs, callback)
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "裁剪影片時發生錯誤: ${e.message}")
            callback.onError("裁剪影片時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 處理大檔案裁剪
     */
    private suspend fun processLargeVideoTrim(
        inputPath: String,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "=== 使用大檔案處理器裁剪 ===")
            
            // 獲取檔案信息
            val fileInfo = largeVideoProcessor.getFileInfo(inputPath)
            if (fileInfo != null) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案信息: ${fileInfo.sizeMB}MB, ${fileInfo.durationSeconds}秒")
            }
            
            // 創建輸出檔案
            val outputFile = File(
                context.getExternalFilesDir(null),
                "trimmed_large_${System.currentTimeMillis()}.mp4"
            )
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "輸出檔案: ${outputFile.absolutePath}")
            
            // 使用大檔案處理器進行裁剪
            val success = largeVideoProcessor.trimLargeVideo(
                inputPath = inputPath,
                outputPath = outputFile.absolutePath,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                progressCallback = { progress ->
                    callback.onProgress(progress)
                }
            )
            
            if (success) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "大檔案裁剪完成")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "大檔案裁剪失敗")
                callback.onError("大檔案裁剪失敗")
            }
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "大檔案裁剪時發生錯誤: ${e.message}")
            callback.onError("大檔案裁剪時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 處理普通檔案裁剪（原有邏輯）
     */
    private suspend fun processNormalVideoTrim(
        inputUri: Uri,
        startTimeMs: Long,
        endTimeMs: Long,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        
        try {
            
            extractor = MediaExtractor()
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 1: 創建 MediaExtractor")
            
            extractor.setDataSource(context, inputUri, null)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 2: 設定資料來源")
            
            // 檢查影片長度：以實際可用(非 meta)軌道為準，取最大值
            var duration: Long = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                val hasDuration = format.containsKey(MediaFormat.KEY_DURATION)
                if (hasDuration && (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true)) {
                    val d = format.getLong(MediaFormat.KEY_DURATION) / 1000
                    if (d > duration) duration = d
                }
            }
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 3: 檢查影片總長度: ${duration}ms")
            
            if (endTimeMs > duration) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤: 結束時間超過影片長度")
                callback.onError("結束時間超過影片長度")
                return@withContext
            }
            
            // 使用外部檔案目錄，確保檔案可以被其他應用訪問
            val outputFile = File(
                context.getExternalFilesDir(null),
                "trimmed_${System.currentTimeMillis()}.mp4"
            )
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 4: 設定輸出檔案路徑: ${outputFile.absolutePath}")
            
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 5: 創建 MediaMuxer")
            
            val trackCount = extractor.trackCount
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 6: 檢測到 $trackCount 個軌道")
            
            val trackIndexMap = mutableMapOf<Int, Int>()
            
            // 設定軌道
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 7: 開始設定軌道映射")
            var hasVideoTrack = false
            var hasAudioTrack = false
            
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "軌道 $i: MIME類型 = $mimeType")
                
                if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) {
                    val outputTrackIndex = muxer.addTrack(format)
                    trackIndexMap[i] = outputTrackIndex
                    extractor.selectTrack(i)
                    
                    if (mimeType.startsWith("video/")) {
                        hasVideoTrack = true
                    } else if (mimeType.startsWith("audio/")) {
                        hasAudioTrack = true
                    }
                    
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 7.$i: 添加軌道 $i -> 輸出軌道 $outputTrackIndex (MIME: $mimeType)")
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 7.$i: 選取輸入軌道 $i 以供讀取")
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "步驟 7.$i: 跳過不支援的軌道 $i (MIME: $mimeType)")
                }
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "軌道映射表: $trackIndexMap")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檢測到影片軌道: $hasVideoTrack, 音訊軌道: $hasAudioTrack")
            
            if (!hasVideoTrack) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤: 沒有找到影片軌道")
                callback.onError("沒有找到影片軌道")
                return@withContext
            }
            
            // 開始處理
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 8: 啟動 MediaMuxer")
            muxer.start()
            
            val buffer = ByteBuffer.allocate(1024 * 1024) // 1MB buffer
            val bufferInfo = MediaCodec.BufferInfo()
            
            val totalDuration = endTimeMs - startTimeMs
            var processedDuration = 0L
            
            // 定位到開始時間（在選擇軌道之後執行）
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 9: 定位到開始時間 ${startTimeMs}ms")
            extractor.seekTo(startTimeMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 10: 開始處理樣本")
            
            var sampleCount = 0
            var videoSampleCount = 0
            var audioSampleCount = 0
            var maxSamples = 10000 // 防止無限循環
            var lastLogTime = System.currentTimeMillis()
            
            while (sampleCount < maxSamples) {
                val trackIndex = extractor.getSampleTrackIndex()
                if (trackIndex < 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 10.$sampleCount: 沒有更多樣本，結束處理")
                    break
                }
                
                val sampleTime = extractor.sampleTime
                if (sampleTime >= endTimeMs * 1000) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 10.$sampleCount: 樣本時間 ${sampleTime}us 超過結束時間 ${endTimeMs * 1000}us，結束處理")
                    break
                }
                
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 10.$sampleCount: 樣本大小為負數，結束處理")
                    break
                }
                
                // 檢查超時
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLogTime > 5000) { // 5秒超時
                    com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "步驟 10.$sampleCount: 處理超時，強制結束")
                    break
                }
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                val ptsUs = sampleTime - startTimeMs * 1000
                bufferInfo.presentationTimeUs = if (ptsUs < 0) 0 else ptsUs
                bufferInfo.flags = extractor.sampleFlags
                
                val outputTrackIndex = trackIndexMap[trackIndex]
                if (outputTrackIndex != null) {
                    try {
                        muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                        sampleCount++
                        
                        // 統計不同軌道的樣本數量
                        val format = extractor.getTrackFormat(trackIndex)
                        val mimeType = format.getString(MediaFormat.KEY_MIME)
                        if (mimeType?.startsWith("video/") == true) {
                            videoSampleCount++
                        } else if (mimeType?.startsWith("audio/") == true) {
                            audioSampleCount++
                        }
                        
                        if (sampleCount % 100 == 0) {
                            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 10.$sampleCount: 已處理 $sampleCount 個樣本 (影片: $videoSampleCount, 音訊: $audioSampleCount)")
                            lastLogTime = currentTime
                        }
                    } catch (e: IllegalArgumentException) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "步驟 10.$sampleCount: trackIndex is invalid 錯誤!")
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤詳情: trackIndex=$trackIndex, outputTrackIndex=$outputTrackIndex")
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "軌道映射表: $trackIndexMap")
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "樣本信息: size=$sampleSize, time=${sampleTime}us, flags=${extractor.sampleFlags}")
                        throw e
                    } catch (e: Exception) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "步驟 10.$sampleCount: 寫入樣本時發生錯誤: ${e.message}")
                        throw e
                    }
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "步驟 10.$sampleCount: 找不到軌道 $trackIndex 的輸出映射，跳過樣本")
                }
                
                extractor.advance()
                processedDuration = sampleTime - startTimeMs * 1000
                
                val progress = (processedDuration.toFloat() / (totalDuration * 1000)) * 100
                callback.onProgress(progress.coerceIn(0f, 100f))
            }
            
            if (sampleCount >= maxSamples) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "達到最大樣本數量限制，強制結束處理")
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 11: 處理完成統計")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "總共處理了 $sampleCount 個樣本")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "影片樣本: $videoSampleCount 個")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "音訊樣本: $audioSampleCount 個")
            
            // 停止 MediaMuxer 以完成檔案寫入
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 12: 停止 MediaMuxer")
            muxer.stop()
            
            // 確保檔案存在且可讀
            if (outputFile.exists() && outputFile.length() > 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 13: 裁剪成功")
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "輸出檔案: ${outputFile.absolutePath}")
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案大小: ${outputFile.length()} bytes")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "步驟 13: 輸出檔案不存在或為空")
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "檔案路徑: ${outputFile.absolutePath}")
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "檔案存在: ${outputFile.exists()}")
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "檔案大小: ${outputFile.length()} bytes")
                callback.onError("輸出檔案生成失敗")
            }
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "=== 裁剪影片失敗 ===")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤類型: ${e.javaClass.simpleName}")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤訊息: ${e.message}")
            e.printStackTrace()
            callback.onError("裁剪失敗: ${e.message}")
        } finally {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 14: 清理資源")
            try {
                extractor?.release()
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "MediaExtractor 已釋放")
                // 注意：muxer.stop() 已經在成功路徑中調用，這裡只需要釋放
                muxer?.release()
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "MediaMuxer 已釋放")
            } catch (e: Exception) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "釋放資源失敗: ${e.message}")
            }
        }
    }
    
    /**
     * 從 URI 獲取檔案路徑
     */
    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "處理 file URI: ${uri.path}")
                    uri.path
                }
                "content" -> {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "處理 content URI: $uri")
                    // 對於 content URI，嘗試獲取實際路徑
                    val cursor = context.contentResolver.query(uri, arrayOf("_data"), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val path = it.getString(0)
                            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "從 content URI 獲取路徑: $path")
                            path
                        } else {
                            com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "content URI 查詢無結果")
                            null
                        }
                    } ?: run {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "content URI 查詢失敗")
                        null
                    }
                }
                else -> {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "不支援的 URI scheme: ${uri.scheme}")
                    null
                }
            }
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "獲取檔案路徑失敗: ${e.message}")
            null
        }
    }
    
    suspend fun changeSpeed(
        inputUri: Uri,
        speed: Float,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "=== 開始變速處理 ===")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "輸入 URI: $inputUri")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "變速倍數: $speed")
            
            // 檢查是否為大檔案
            val inputPath = getFilePathFromUri(inputUri)
            if (inputPath != null && largeVideoProcessor.isSuitableForLargeFileProcessing(inputPath)) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檢測到大檔案，使用大檔案處理器")
                processLargeVideoSpeed(inputPath, speed, callback)
                return@withContext
            }
            
            // 使用原有的處理方式
            processNormalVideoSpeed(inputUri, speed, callback)
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "變速處理時發生錯誤: ${e.message}")
            callback.onError("變速處理時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 處理大檔案變速
     */
    private suspend fun processLargeVideoSpeed(
        inputPath: String,
        speed: Float,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "=== 使用大檔案處理器變速 ===")
            
            // 獲取檔案信息
            val fileInfo = largeVideoProcessor.getFileInfo(inputPath)
            if (fileInfo != null) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案信息: ${fileInfo.sizeMB}MB, ${fileInfo.durationSeconds}秒")
            }
            
            // 創建輸出檔案
            val outputFile = File(
                context.getExternalFilesDir(null),
                "speed_large_${System.currentTimeMillis()}.mp4"
            )
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "輸出檔案: ${outputFile.absolutePath}")
            
            // 使用大檔案處理器進行變速
            val success = largeVideoProcessor.changeLargeVideoSpeed(
                inputPath = inputPath,
                outputPath = outputFile.absolutePath,
                speedFactor = speed,
                progressCallback = { progress ->
                    callback.onProgress(progress)
                }
            )
            
            if (success) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "大檔案變速完成")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "大檔案變速失敗")
                callback.onError("大檔案變速失敗")
            }
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "大檔案變速處理時發生錯誤: ${e.message}")
            callback.onError("大檔案變速處理時發生錯誤: ${e.message}")
        }
    }
    
    /**
     * 處理普通檔案變速
     */
    private suspend fun processNormalVideoSpeed(
        inputUri: Uri,
        speed: Float,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var muxerStopped = false
        
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "=== 使用普通處理器變速 ===")
            
            extractor = MediaExtractor()
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 1: 創建 MediaExtractor")
            
            extractor.setDataSource(context, inputUri, null)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 2: 設定資料來源")
            
            // 使用外部檔案目錄
            val outputFile = File(
                context.getExternalFilesDir(null),
                "speed_${System.currentTimeMillis()}.mp4"
            )
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 3: 設定輸出檔案路徑: ${outputFile.absolutePath}")
            
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 4: 創建 MediaMuxer")
            
            val trackCount = extractor.trackCount
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 5: 檢測到 $trackCount 個軌道")
            
            val trackIndexMap = mutableMapOf<Int, Int>()
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            
            // 分析軌道格式
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 6: 分析軌道格式")
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "軌道 $i: MIME類型 = $mimeType")
                
                if (mimeType?.startsWith("video/") == true) {
                    videoFormat = format
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "影片軌道格式: $format")
                } else if (mimeType?.startsWith("audio/") == true) {
                    audioFormat = format
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "音訊軌道格式: $format")
                }
            }
            
            // 設定軌道 - 使用原始格式（不改變音訊採樣率；改由 PTS 縮放達成變速）
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 7: 設定軌道映射")
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                
                if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) {
                    // 影片與音訊軌道皆使用原始格式；避免容器層參數與實際編碼不一致
                    val outputTrackIndex = muxer.addTrack(format)
                    trackIndexMap[i] = outputTrackIndex
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 7.$i: 添加軌道 $i -> 輸出軌道 $outputTrackIndex (MIME=$mimeType)")

                    extractor.selectTrack(i)
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "步驟 7.$i: 跳過不支援的軌道 $i (MIME: $mimeType)")
                }
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "軌道映射表: $trackIndexMap")
            
            // 開始處理
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 8: 啟動 MediaMuxer")
            muxer.start()
            
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            // 以各軌道首個時間戳為基準，依 speed 等比縮放 PTS
            val firstPtsPerTrack = mutableMapOf<Int, Long>()
            
            var sampleCount = 0
            var videoSampleCount = 0
            var audioSampleCount = 0
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 9: 開始處理樣本")
            while (true) {
                val trackIndex = extractor.getSampleTrackIndex()
                if (trackIndex < 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 9.$sampleCount: 沒有更多樣本，結束處理")
                    break
                }
                
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 9.$sampleCount: 樣本大小為負數，結束處理")
                    break
                }
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                
                // 保持原始時間戳，讓播放器處理變速
                val originalTimeUs = extractor.sampleTime
                val trackFormat = extractor.getTrackFormat(trackIndex)
                val trackMimeType = trackFormat.getString(MediaFormat.KEY_MIME)
                
                // 以該軌道首個 PTS 為 0，依 speed 進行等比縮放
                val base = firstPtsPerTrack.getOrPut(trackIndex) { originalTimeUs }
                val deltaUs = if (originalTimeUs >= base) originalTimeUs - base else 0L
                val scaledUs = (deltaUs / speed).toLong().coerceAtLeast(0L)
                bufferInfo.presentationTimeUs = scaledUs
                
                if (trackMimeType?.startsWith("video/") == true) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "影片處理: 原始=${originalTimeUs}us, 縮放=${scaledUs}us, 速度=$speed")
                } else if (trackMimeType?.startsWith("audio/") == true) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "音訊處理: 原始=${originalTimeUs}us, 縮放=${scaledUs}us, 速度=$speed")
                }
                
                bufferInfo.flags = extractor.sampleFlags
                
                val outputTrackIndex = trackIndexMap[trackIndex]
                if (outputTrackIndex != null) {
                    try {
                        muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                        sampleCount++
                        
                        // 統計不同軌道的樣本數量
                        val format = extractor.getTrackFormat(trackIndex)
                        val mimeType = format.getString(MediaFormat.KEY_MIME)
                        if (mimeType?.startsWith("video/") == true) {
                            videoSampleCount++
                        } else if (mimeType?.startsWith("audio/") == true) {
                            audioSampleCount++
                        }
                        
                        if (sampleCount % 100 == 0) {
                            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 9.$sampleCount: 已處理 $sampleCount 個樣本 (影片: $videoSampleCount, 音訊: $audioSampleCount)")
                        }
                    } catch (e: IllegalArgumentException) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "步驟 9.$sampleCount: trackIndex is invalid 錯誤!")
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤詳情: trackIndex=$trackIndex, outputTrackIndex=$outputTrackIndex")
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "軌道映射表: $trackIndexMap")
                        throw e
                    } catch (e: Exception) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "步驟 9.$sampleCount: 寫入樣本時發生錯誤: ${e.message}")
                        throw e
                    }
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "步驟 9.$sampleCount: 找不到軌道 $trackIndex 的輸出映射，跳過樣本")
                }
                
                extractor.advance()
                
                val progress = (sampleCount.toFloat() / 100) * 100 // 臨時進度計算
                callback.onProgress(progress.coerceIn(0f, 100f))
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 10: 處理完成統計")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "總共處理了 $sampleCount 個樣本")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "影片樣本: $videoSampleCount 個")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "音訊樣本: $audioSampleCount 個")
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 11: 處理完成，準備停止 MediaMuxer")
            
            // 確保檔案存在且可讀
            if (outputFile.exists() && outputFile.length() > 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 12: 變速處理成功")
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "輸出檔案: ${outputFile.absolutePath}")
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案大小: ${outputFile.length()} bytes")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "步驟 12: 輸出檔案不存在或為空")
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "檔案路徑: ${outputFile.absolutePath}")
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "檔案存在: ${outputFile.exists()}")
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "檔案大小: ${outputFile.length()} bytes")
                callback.onError("輸出檔案生成失敗")
            }
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "=== 變速處理失敗 ===")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤類型: ${e.javaClass.simpleName}")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "錯誤訊息: ${e.message}")
            e.printStackTrace()
            callback.onError("變速處理失敗: ${e.message}")
        } finally {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "步驟 13: 清理資源")
            try {
                extractor?.release()
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "MediaExtractor 已釋放")
                
                // 停止 MediaMuxer
                if (muxer != null) {
                    try {
                        if (!muxerStopped) {
                            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "停止 MediaMuxer")
                            muxer.stop()
                            muxerStopped = true
                        }
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "MediaMuxer 已停止")
                    } catch (e: Exception) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "MediaMuxer 停止失敗: ${e.message}")
                    }
                    
                    // 釋放 MediaMuxer
                    try {
                        muxer.release()
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "MediaMuxer 已釋放")
                    } catch (e: Exception) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("W", "VideoProcessor", "MediaMuxer 釋放失敗: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "VideoProcessor", "釋放資源失敗: ${e.message}")
            }
        }
    }
    
    suspend fun removeAudio(
        inputUri: Uri,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "=== 開始移除音訊 ===")
            extractor = MediaExtractor()
            extractor!!.setDataSource(context, inputUri, null)

            val outputFile = File(
                context.getExternalFilesDir(null),
                "no_audio_${System.currentTimeMillis()}.mp4"
            )
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "輸出檔案路徑: ${outputFile.absolutePath}")

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackCount = extractor!!.trackCount
            val trackIndexMap = mutableMapOf<Int, Int>()

            for (i in 0 until trackCount) {
                val format = extractor!!.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                if (mimeType?.startsWith("video/") == true) {
                    val outIdx = muxer!!.addTrack(format)
                    trackIndexMap[i] = outIdx
                    extractor!!.selectTrack(i)
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "選取影片軌道 $i -> 輸出軌道 $outIdx")
                }
            }

            muxer!!.start()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val inTrack = extractor!!.getSampleTrackIndex()
                if (inTrack < 0) break
                val size = extractor!!.readSampleData(buffer, 0)
                if (size < 0) break
                val outIdx = trackIndexMap[inTrack]
                if (outIdx != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = size
                    bufferInfo.presentationTimeUs = extractor!!.sampleTime
                    bufferInfo.flags = extractor!!.sampleFlags
                    muxer!!.writeSampleData(outIdx, buffer, bufferInfo)
                }
                extractor!!.advance()
            }

            if (outputFile.exists() && outputFile.length() > 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "移除音訊完成，檔案大小: ${outputFile.length()} bytes")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "輸出檔案不存在或為空")
                callback.onError("輸出檔案生成失敗")
            }
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "移除音訊失敗: ${e.message}")
            callback.onError("移除音訊失敗: ${e.message}")
        } finally {
            try {
                extractor?.release()
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
        }
    }
    
    suspend fun addBackgroundMusic(
        inputUri: Uri,
        musicUri: Uri,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "=== 開始添加背景音樂（新混音器） ===")
            
            val inputPath = com.example.videoeditor.utils.VideoUtils.resolveToLocalFilePath(
                context = context,
                uri = inputUri,
                defaultNamePrefix = "video"
            ) ?: throw IllegalArgumentException("無法獲取輸入影片路徑")

            val musicPath = com.example.videoeditor.utils.VideoUtils.resolveToLocalFilePath(
                context = context,
                uri = musicUri,
                defaultNamePrefix = "music",
                fallbackExt = "mp3"
            ) ?: throw IllegalArgumentException("無法獲取音樂檔案路徑")

            val outputFile = File(
                context.getExternalFilesDir(null),
                "with_music_${System.currentTimeMillis()}.mp4"
            )

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "輸入影片路徑: $inputPath")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "音樂檔案路徑: $musicPath")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "輸出檔案路徑: ${outputFile.absolutePath}")

            // 使用專業的背景音樂混音器
            val config = BgmMixConfig(
                mainVolume = 0.85f,          // 原影片音量比例 0.0~1.0
                bgmVolume = 0.35f,           // BGM 音量比例 0.0~1.0
                bgmStartOffsetUs = 0L,       // BGM 從 0 開始；可設 >0 延後入場
                loopBgm = true,              // BGM 不足時自動循環
                enableDucking = false,       // 是否開啟 Ducking
                duckingThreshold = 0.08f,    // 主聲道瞬時能量門檻（RMS）
                duckingAttenuation = 0.5f,   // Ducking 時 BGM 衰減倍率（0.0~1.0）
                outSampleRate = 48000,
                outChannelCount = 2,
                outAacBitrate = 128_000
            )

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "混音配置: $config")

            SimpleBgmMixer.mixVideoWithBgm(
                context = context,
                inputVideoPath = inputPath,
                inputBgmPath = musicPath,
                outputPath = outputFile.absolutePath,
                config = config
            )

            if (outputFile.exists() && outputFile.length() > 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "背景音樂混音完成，檔案大小: ${outputFile.length()} bytes")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "輸出檔案不存在或為空")
                callback.onError("輸出檔案生成失敗")
            }
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "=== 背景音樂混音失敗 ===")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "錯誤類型: ${e.javaClass.simpleName}")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "錯誤訊息: ${e.message}")
            e.printStackTrace()
            callback.onError("背景音樂混音失敗: ${e.message}")
        }
    }
    
    private fun getVideoDuration(extractor: MediaExtractor): Long {
        var duration = 0L
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                duration = format.getLong(MediaFormat.KEY_DURATION)
                break
            }
        }
        return duration
    }
    
    private fun getMusicDuration(extractor: MediaExtractor): Long {
        var duration = 0L
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                duration = format.getLong(MediaFormat.KEY_DURATION)
                break
            }
        }
        return duration
    }
    
    suspend fun applyFilter(
        inputUri: Uri,
        filterType: String,
        callback: ProcessingCallback
    ) = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        
        try {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "=== 開始應用濾鏡: $filterType ===")
            
            extractor = MediaExtractor()
            extractor!!.setDataSource(context, inputUri, null)
            
            // 使用外部檔案目錄，確保檔案可以被其他應用訪問
            val outputFile = File(
                context.getExternalFilesDir(null),
                "filtered_${filterType}_${System.currentTimeMillis()}.mp4"
            )
            
            Log.d(TAG, "輸出檔案路徑: ${outputFile.absolutePath}")
            
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val trackCount = extractor!!.trackCount
            val trackIndexMap = mutableMapOf<Int, Int>()
            
            // 設定軌道
            for (i in 0 until trackCount) {
                val format = extractor!!.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                
                if (mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true) {
                    val outputTrackIndex = muxer!!.addTrack(format)
                    trackIndexMap[i] = outputTrackIndex
                    extractor!!.selectTrack(i)
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "選取輸入軌道 $i ($mimeType) -> 輸出 $outputTrackIndex")
                }
            }
            
            muxer!!.start()
            
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            
            var sampleCount = 0
            while (true) {
                val trackIndex = extractor!!.getSampleTrackIndex()
                if (trackIndex < 0) break
                
                val sampleSize = extractor!!.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor!!.sampleTime
                bufferInfo.flags = extractor!!.sampleFlags
                
                val outputTrackIndex = trackIndexMap[trackIndex]
                if (outputTrackIndex != null) {
                    // 這裡可以添加實際的濾鏡處理邏輯
                    // 目前只是簡單複製，但保留了擴展的空間
                    muxer!!.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                    sampleCount++
                    
                    if (sampleCount % 100 == 0) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "濾鏡處理已處理 $sampleCount 個樣本")
                    }
                }
                
                extractor!!.advance()
                
                val progress = (sampleCount.toFloat() / 100) * 100
                callback.onProgress(progress.coerceIn(0f, 100f))
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "濾鏡處理總共處理了 $sampleCount 個樣本")
            
            muxer.stop()
            
            // 確保檔案存在且可讀
            if (outputFile.exists() && outputFile.length() > 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "濾鏡應用完成: ${outputFile.absolutePath}, 檔案大小: ${outputFile.length()} bytes")
                callback.onSuccess(outputFile.absolutePath)
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "輸出檔案不存在或為空: ${outputFile.absolutePath}")
                callback.onError("輸出檔案生成失敗")
            }
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "濾鏡應用失敗: ${e.message}")
            callback.onError("濾鏡應用失敗: ${e.message}")
        } finally {
            try {
                extractor?.release()
                muxer?.release()
            } catch (e: Exception) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "釋放資源失敗: ${e.message}")
            }
        }
    }
}
