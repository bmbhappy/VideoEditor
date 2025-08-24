package com.example.videoeditor.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer
import java.io.File

/**
 * 大影片檔案處理器
 * 使用 MediaExtractor + MediaMuxer 串流方式處理大檔案
 * 支援 300MB~500MB 甚至更大的影片檔案
 */
class LargeVideoProcessor {
    companion object {
        private const val TAG = "LargeVideoProcessor"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB 緩衝區
    }

    /**
     * 處理大影片檔案 - 串流複製（不重新編碼）
     * @param inputPath 輸入影片路徑
     * @param outputPath 輸出影片路徑
     * @param progressCallback 進度回調
     * @return 處理結果
     */
    fun processLargeVideo(
        inputPath: String, 
        outputPath: String,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        
        try {
            Log.d(TAG, "開始處理大影片檔案: $inputPath")
            Log.d(TAG, "檔案大小: ${File(inputPath).length() / (1024 * 1024)}MB")
            
            // 初始化 MediaExtractor
            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)
            
            Log.d(TAG, "軌道數量: ${extractor.trackCount}")
            
            // 找出影片和音訊軌道
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                Log.d(TAG, "軌道 $i: $mime")
                
                when {
                    mime?.startsWith("video/") == true -> {
                        videoTrackIndex = i
                        videoFormat = format
                        Log.d(TAG, "找到影片軌道: $i")
                    }
                    mime?.startsWith("audio/") == true -> {
                        audioTrackIndex = i
                        audioFormat = format
                        Log.d(TAG, "找到音訊軌道: $i")
                    }
                }
            }
            
            require(videoTrackIndex >= 0) { "找不到影片軌道" }
            
            // 初始化 MediaMuxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // 添加影片軌道
            val outVideoTrack = muxer.addTrack(videoFormat!!)
            Log.d(TAG, "添加影片軌道到輸出: $outVideoTrack")
            
            // 添加音訊軌道（如果存在）
            var outAudioTrack = -1
            if (audioTrackIndex >= 0) {
                outAudioTrack = muxer.addTrack(audioFormat!!)
                Log.d(TAG, "添加音訊軌道到輸出: $outAudioTrack")
            }
            
            // 開始 muxer
            muxer.start()
            Log.d(TAG, "MediaMuxer 已開始")
            
            // 準備緩衝區
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            
            // 計算總時長用於進度
            val totalDurationUs = videoFormat.getLong(MediaFormat.KEY_DURATION)
            Log.d(TAG, "總時長: ${totalDurationUs / 1000000}秒")
            
            var processedSamples = 0L
            var lastProgressTime = 0L
            
            // 串流處理：逐幀讀取並寫入
            while (true) {
                // 重置緩衝區
                buffer.clear()
                info.offset = 0
                
                // 讀取樣本數據
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    Log.d(TAG, "已讀取完所有樣本")
                    break
                }
                
                // 設置樣本信息
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                
                // 獲取當前軌道索引
                val trackIndex = extractor.sampleTrackIndex
                
                // 寫入對應的軌道
                when (trackIndex) {
                    videoTrackIndex -> {
                        muxer.writeSampleData(outVideoTrack, buffer, info)
                        processedSamples++
                        
                        // 進度回調（每秒更新一次）
                        val currentTime = info.presentationTimeUs
                        if (currentTime - lastProgressTime > 1000000) { // 1秒
                            val progress = (currentTime.toFloat() / totalDurationUs) * 100
                            progressCallback?.invoke(progress.coerceIn(0f, 100f))
                            lastProgressTime = currentTime
                            
                            Log.d(TAG, "處理進度: ${String.format("%.1f", progress)}%")
                        }
                    }
                    audioTrackIndex -> {
                        if (outAudioTrack >= 0) {
                            muxer.writeSampleData(outAudioTrack, buffer, info)
                        }
                    }
                }
                
                // 前進到下一個樣本
                extractor.advance()
            }
            
            // 完成處理
            Log.d(TAG, "處理完成，總共處理 $processedSamples 個樣本")
            progressCallback?.invoke(100f)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "處理大影片檔案時發生錯誤", e)
            return false
            
        } finally {
            // 清理資源
            try {
                muxer?.stop()
                muxer?.release()
                extractor?.release()
                Log.d(TAG, "資源清理完成")
            } catch (e: Exception) {
                Log.e(TAG, "清理資源時發生錯誤", e)
            }
        }
    }
    
    /**
     * 檢查檔案是否適合使用大檔案處理器
     * @param filePath 檔案路徑
     * @return 是否適合
     */
    fun isSuitableForLargeFileProcessing(filePath: String): Boolean {
        val file = File(filePath)
        val fileSizeMB = file.length() / (1024 * 1024)
        
        // 檔案大於 100MB 時使用大檔案處理器
        return fileSizeMB > 100
    }
    
    /**
     * 獲取檔案信息
     * @param filePath 檔案路徑
     * @return 檔案信息
     */
    fun getFileInfo(filePath: String): FileInfo? {
        var extractor: MediaExtractor? = null
        
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)
            
            val file = File(filePath)
            val fileSizeMB = file.length() / (1024 * 1024)
            
            var videoDuration = 0L
            var hasAudio = false
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                
                when {
                    mime?.startsWith("video/") == true -> {
                        videoDuration = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    mime?.startsWith("audio/") == true -> {
                        hasAudio = true
                    }
                }
            }
            
            return FileInfo(
                sizeMB = fileSizeMB,
                durationSeconds = videoDuration / 1000000,
                hasAudio = hasAudio,
                trackCount = extractor.trackCount
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "獲取檔案信息時發生錯誤", e)
            return null
            
        } finally {
            extractor?.release()
        }
    }
    
    /**
     * 檔案信息數據類
     */
    data class FileInfo(
        val sizeMB: Long,
        val durationSeconds: Long,
        val hasAudio: Boolean,
        val trackCount: Int
    )
}
