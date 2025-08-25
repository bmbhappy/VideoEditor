package com.example.videoeditor.engine

import android.content.Context
import android.media.*
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * 大檔案背景音樂混音器
 * 使用 MediaExtractor + MediaMuxer 串流方式處理大檔案
 * 支援 300MB~500MB 甚至更大的影片檔案
 */
class LargeBgmMixer {
    companion object {
        private const val TAG = "LargeBgmMixer"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB 緩衝區
        private const val LARGE_FILE_THRESHOLD_MB = 100 // 100MB 以上使用大檔案處理器
    }

    /**
     * 將影片和背景音樂合併（大檔案版本）
     */
    fun mixVideoWithBgm(
        context: Context,
        inputVideoPath: String,
        inputBgmPath: String,
        outputPath: String,
        config: BgmMixConfig,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean {
        
        try {
            Log.d(TAG, "=== 開始大檔案背景音樂混音 ===")
            Log.d(TAG, "影片路徑: $inputVideoPath")
            Log.d(TAG, "BGM路徑: $inputBgmPath")
            Log.d(TAG, "輸出路徑: $outputPath")
            
            // 檢查檔案大小
            val videoFile = File(inputVideoPath)
            val bgmFile = File(inputBgmPath)
            val videoSizeMB = videoFile.length() / (1024 * 1024)
            val bgmSizeMB = bgmFile.length() / (1024 * 1024)
            
            Log.d(TAG, "影片大小: ${videoSizeMB}MB")
            Log.d(TAG, "BGM大小: ${bgmSizeMB}MB")
            
            // 檢查檔案是否存在
            require(videoFile.exists()) { "影片檔案不存在: $inputVideoPath" }
            require(bgmFile.exists()) { "BGM檔案不存在: $inputBgmPath" }
            
            // 獲取影片時長
            val videoDurationUs = getVideoDuration(inputVideoPath)
            Log.d(TAG, "影片時長: ${videoDurationUs / 1000000}秒")
            
            // 處理 BGM 檔案
            val processedBgmPath = preprocessBgmForLargeFile(
                context, 
                inputBgmPath, 
                config, 
                videoDurationUs
            )
            
            if (processedBgmPath == null) {
                Log.e(TAG, "無法處理 BGM 檔案")
                return false
            }
            
            // 執行串流混音
            return performStreamingMix(
                inputVideoPath,
                processedBgmPath,
                outputPath,
                videoDurationUs,
                progressCallback
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "大檔案背景音樂混音失敗", e)
            return false
        }
    }
    
    /**
     * 獲取影片時長
     */
    private fun getVideoDuration(videoPath: String): Long {
        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(videoPath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    return format.getLong(MediaFormat.KEY_DURATION)
                }
            }
            return 0L
        } finally {
            extractor?.release()
        }
    }
    
    /**
     * 預處理 BGM 檔案（適用於大檔案）
     */
    private fun preprocessBgmForLargeFile(
        context: Context,
        bgmPath: String,
        config: BgmMixConfig,
        videoDurationUs: Long
    ): String? {
        
        try {
            Log.d(TAG, "預處理 BGM 檔案")
            
            // 檢查是否需要轉碼
            val needsTranscoding = needsAudioTranscoding(bgmPath)
            
            if (needsTranscoding) {
                Log.d(TAG, "BGM 需要轉碼為 AAC 格式")
                return convertToAac(context, bgmPath, config)
            } else {
                Log.d(TAG, "BGM 格式已支援，無需轉碼")
                return bgmPath
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "預處理 BGM 檔案失敗", e)
            return null
        }
    }
    
    /**
     * 檢查是否需要音訊轉碼
     */
    private fun needsAudioTranscoding(audioPath: String): Boolean {
        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(audioPath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    // 檢查是否為 AAC 格式
                    return mime != "audio/mp4a-latm" && mime != "audio/aac"
                }
            }
            return true
        } finally {
            extractor?.release()
        }
    }
    
    /**
     * 轉換為 AAC 格式（使用逐塊解碼 + 即時編碼，避免記憶體溢出）
     */
    private fun convertToAac(
        context: Context,
        inputPath: String,
        config: BgmMixConfig
    ): String? {
        
        val outputFile = File(
            context.getExternalFilesDir(null),
            "bgm_converted_${System.currentTimeMillis()}.aac"
        )
        
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var audioTrackIndex = -1
        
        try {
            Log.d(TAG, "開始逐塊轉碼 BGM: $inputPath")
            
            // 初始化 extractor
            extractor = MediaExtractor().apply { setDataSource(inputPath) }
            var audioTrack = -1
            var format: MediaFormat? = null
            
            // 找到音訊軌道
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrack = i
                    format = fmt
                    break
                }
            }
            
            if (audioTrack < 0 || format == null) {
                Log.e(TAG, "找不到音訊軌道")
                return null
            }
            
            extractor.selectTrack(audioTrack)
            Log.d(TAG, "找到音訊軌道: $audioTrack")
            
            // 創建解碼器
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime).apply { 
                configure(format, null, null, 0)
                start() 
            }
            Log.d(TAG, "解碼器初始化完成: $mime")
            
            // 建立 AAC 編碼器
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val encodeFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount)
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, 2) // AAC-LC
            
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm").apply {
                configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            Log.d(TAG, "編碼器初始化完成: AAC-LC, ${sampleRate}Hz, ${channelCount}ch")
            
            // 建立 muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val bufferInfo = MediaCodec.BufferInfo()
            var muxerStarted = false
            var processedSamples = 0L
            
            Log.d(TAG, "開始逐塊解碼 → 編碼 → 寫出")
            
            // === 解碼 → 編碼 → 寫出（逐塊處理，避免記憶體溢出）===
            loop@ while (true) {
                // 解碼輸入
                val inIndex = decoder!!.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = decoder!!.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buf, 0)
                    
                    if (size < 0) {
                        // 輸入結束
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        Log.d(TAG, "解碼輸入結束")
                    } else {
                        // 輸入數據
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                
                // 解碼輸出 → 送進編碼器
                var outIndex = decoder!!.dequeueOutputBuffer(bufferInfo, 10_000)
                while (outIndex >= 0) {
                    val decodedBuf = decoder!!.getOutputBuffer(outIndex)!!
                    
                    if (bufferInfo.size > 0) {
                        // 將解碼後的數據送進編碼器
                        val inEncIndex = encoder!!.dequeueInputBuffer(10_000)
                        if (inEncIndex >= 0) {
                            val encInBuf = encoder.getInputBuffer(inEncIndex)!!
                            encInBuf.clear()
                            encInBuf.put(decodedBuf)
                            encoder.queueInputBuffer(
                                inEncIndex, 0, bufferInfo.size, 
                                bufferInfo.presentationTimeUs, bufferInfo.flags
                            )
                        }
                    }
                    
                    decoder!!.releaseOutputBuffer(outIndex, false)
                    outIndex = decoder!!.dequeueOutputBuffer(bufferInfo, 0)
                }
                
                // 編碼輸出 → 寫進 muxer
                var encOutIndex = encoder!!.dequeueOutputBuffer(bufferInfo, 10_000)
                while (encOutIndex >= 0) {
                    val encodedBuf = encoder!!.getOutputBuffer(encOutIndex)!!
                    
                    if (!muxerStarted) {
                        // 啟動 muxer
                        val newFormat = encoder.outputFormat
                        audioTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                        Log.d(TAG, "Muxer 啟動完成")
                    }
                    
                    if (bufferInfo.size > 0) {
                        // 寫入編碼後的數據
                        encodedBuf.position(bufferInfo.offset)
                        encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, encodedBuf, bufferInfo)
                        processedSamples++
                        
                                        if (processedSamples % 1000L == 0L) {
                    Log.d(TAG, "已處理 $processedSamples 個音訊樣本")
                }
                    }
                    
                    encoder.releaseOutputBuffer(encOutIndex, false)
                    encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }
                
                // 檢查是否完成
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "轉碼完成，總共處理 $processedSamples 個樣本")
                    break@loop
                }
            }
            
            Log.d(TAG, "BGM 轉碼完成: ${outputFile.absolutePath}")
            return outputFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "BGM 轉碼失敗", e)
            return null
        } finally {
            // 清理資源（確保所有資源都被釋放）
            try { extractor?.release() } catch (_: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
            Log.d(TAG, "轉碼資源清理完成")
        }
    }
    
    /**
     * 執行串流混音（優化記憶體使用）
     */
    private fun performStreamingMix(
        videoPath: String,
        bgmPath: String,
        outputPath: String,
        videoDurationUs: Long,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean {
        
        var videoExtractor: MediaExtractor? = null
        var bgmExtractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        
        try {
            Log.d(TAG, "開始串流混音（優化記憶體使用）")
            
            // 初始化 extractors
            videoExtractor = MediaExtractor().apply { setDataSource(videoPath) }
            bgmExtractor = MediaExtractor().apply { setDataSource(bgmPath) }
            
            // 創建 muxer
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // 找到軌道
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var bgmTrackIndex = -1
            var bgmFormat: MediaFormat? = null
            
            // 影片軌道
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = format
                    break
                }
            }
            
            // BGM 軌道
            for (i in 0 until bgmExtractor.trackCount) {
                val format = bgmExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    bgmTrackIndex = i
                    bgmFormat = format
                    break
                }
            }
            
            require(videoTrackIndex >= 0) { "找不到影片軌道" }
            require(bgmTrackIndex >= 0) { "找不到 BGM 軌道" }
            
            Log.d(TAG, "找到影片軌道: $videoTrackIndex, BGM軌道: $bgmTrackIndex")
            
            // 添加軌道到 muxer
            val outVideoTrack = muxer.addTrack(videoFormat!!)
            val outBgmTrack = muxer.addTrack(bgmFormat!!)
            
            // 選擇軌道
            videoExtractor.selectTrack(videoTrackIndex)
            bgmExtractor.selectTrack(bgmTrackIndex)
            
            // 開始 muxer
            muxer.start()
            
            // 準備緩衝區（使用較小的緩衝區以節省記憶體）
            val buffer = ByteBuffer.allocate(512 * 1024) // 512KB 緩衝區
            val info = MediaCodec.BufferInfo()
            
            var processedSamples = 0L
            var lastProgressTime = 0L
            
            // 串流處理：先處理影片軌道
            Log.d(TAG, "開始處理影片軌道")
            while (true) {
                buffer.clear()
                info.offset = 0
                
                val size = videoExtractor.readSampleData(buffer, 0)
                if (size < 0) {
                    Log.d(TAG, "影片軌道讀取完成")
                    break
                }
                
                info.size = size
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = videoExtractor.sampleFlags
                
                muxer.writeSampleData(outVideoTrack, buffer, info)
                videoExtractor.advance()
                processedSamples++
                
                // 進度回調（減少回調頻率以節省資源）
                val currentTime = info.presentationTimeUs
                if (currentTime - lastProgressTime > 2000000) { // 2秒
                    val progress = (currentTime.toFloat() / videoDurationUs) * 50 // 影片處理佔 50%
                    progressCallback?.invoke(progress)
                    lastProgressTime = currentTime
                    
                    if (processedSamples % 1000L == 0L) {
                        Log.d(TAG, "已處理 $processedSamples 個影片樣本")
                    }
                }
            }
            
            Log.d(TAG, "影片軌道處理完成，開始處理 BGM 軌道")
            
            // 處理 BGM 軌道（優化循環邏輯）
            var bgmCurrentTime = 0L
            var loopCount = 0
            val bgmDurationUs = bgmExtractor.getTrackFormat(bgmTrackIndex).getLong(MediaFormat.KEY_DURATION)
            
            Log.d(TAG, "BGM 時長: ${bgmDurationUs / 1000000}秒")
            
            while (bgmCurrentTime < videoDurationUs) {
                // 重置 BGM 到開始位置
                bgmExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                loopCount++
                
                Log.d(TAG, "BGM 循環 $loopCount，當前時間: ${bgmCurrentTime / 1000000}s")
                
                var loopStartPts = bgmCurrentTime
                var loopBasePts = 0L
                var isFirstFrame = true
                var samplesInLoop = 0
                
                while (true) {
                    buffer.clear()
                    info.offset = 0
                    
                    val size = bgmExtractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        Log.d(TAG, "BGM 檔案讀取完成")
                        break
                    }
                    
                    val samplePts = bgmExtractor.sampleTime
                    
                    if (isFirstFrame) {
                        loopBasePts = samplePts
                        isFirstFrame = false
                    }
                    
                    val outputPts = loopStartPts + (samplePts - loopBasePts)
                    
                    if (outputPts >= videoDurationUs) {
                        Log.d(TAG, "已達到影片長度，停止 BGM 處理")
                        break
                    }
                    
                    info.size = size
                    info.presentationTimeUs = outputPts
                    info.flags = bgmExtractor.sampleFlags
                    
                    muxer.writeSampleData(outBgmTrack, buffer, info)
                    bgmExtractor.advance()
                    processedSamples++
                    samplesInLoop++
                    
                    // 進度回調
                    val progress = 50f + (outputPts.toFloat() / videoDurationUs) * 50 // BGM 處理佔 50%
                    progressCallback?.invoke(progress.coerceIn(0f, 100f))
                    
                    if (samplesInLoop % 1000L == 0L) {
                        Log.d(TAG, "BGM 循環 $loopCount 已處理 $samplesInLoop 個樣本")
                    }
                }
                
                bgmCurrentTime += bgmDurationUs
            }
            
            Log.d(TAG, "串流混音完成，總共處理 $processedSamples 個樣本")
            progressCallback?.invoke(100f)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "串流混音失敗", e)
            return false
        } finally {
            // 清理資源（確保所有資源都被釋放）
            try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
            try { bgmExtractor?.release() } catch (_: Exception) {}
            try { videoExtractor?.release() } catch (_: Exception) {}
            Log.d(TAG, "串流混音資源清理完成")
        }
    }
    
    /**
     * 檢查是否適合使用大檔案處理器
     */
    fun isSuitableForLargeFileProcessing(videoPath: String): Boolean {
        val file = File(videoPath)
        val fileSizeMB = file.length() / (1024 * 1024)
        return fileSizeMB > LARGE_FILE_THRESHOLD_MB
    }
}
