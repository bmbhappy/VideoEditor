package com.example.videoeditor.engine

import android.content.Context
import android.media.*
import java.io.File
import java.nio.ByteBuffer

/**
 * 簡化版背景音樂混音器
 * 支援原影片無損拷貝和背景音樂添加
 */
object SimpleBgmMixer {
    
    // 大檔案處理器實例
    private val largeBgmMixer = LargeBgmMixer()

    /**
     * 將影片和背景音樂合併
     */
    fun mixVideoWithBgm(
        context: Context,
        inputVideoPath: String,
        inputBgmPath: String,
        outputPath: String,
        config: BgmMixConfig
    ) {
        
        // 檢查是否為大檔案
        if (largeBgmMixer.isSuitableForLargeFileProcessing(inputVideoPath)) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "檢測到大檔案，使用大檔案處理器")
            val success = largeBgmMixer.mixVideoWithBgm(
                context = context,
                inputVideoPath = inputVideoPath,
                inputBgmPath = inputBgmPath,
                outputPath = outputPath,
                config = config
            )
            
            if (!success) {
                throw RuntimeException("大檔案背景音樂混音失敗")
            }
            return
        }
        
        // 使用原有的處理方式
        processNormalBgmMix(context, inputVideoPath, inputBgmPath, outputPath, config)
    }
    
    /**
     * 處理普通檔案的背景音樂混音（原有邏輯）
     */
    private fun processNormalBgmMix(
        context: Context,
        inputVideoPath: String,
        inputBgmPath: String,
        outputPath: String,
        config: BgmMixConfig
    ) {
        // 檢查 BGM 配置
        val bgmVolume = config.bgmVolume.coerceIn(0.0f, 2.0f)
        val loopBgm = config.loopBgm
        val bgmStartOffsetUs = config.bgmStartOffsetUs
        val bgmEndOffsetUs = config.bgmEndOffsetUs
        val lengthAdjustMode = config.lengthAdjustMode
        
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM 配置: 音量=$bgmVolume, 循環=$loopBgm, 開始偏移=${bgmStartOffsetUs}us, 結束偏移=${bgmEndOffsetUs}us, 模式=$lengthAdjustMode")
        require(File(inputVideoPath).exists()) { "Video not found: $inputVideoPath" }
        require(File(inputBgmPath).exists()) { "BGM not found: $inputBgmPath" }

        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "=== 開始背景音樂混音 ===")
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "影片路徑: $inputVideoPath")
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM路徑: $inputBgmPath")
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "輸出路徑: $outputPath")

        // 先獲取影片時長
        val tempVideoExtractor = MediaExtractor()
        var videoDurationUs = 0L
        try {
            tempVideoExtractor.setDataSource(inputVideoPath)
            for (i in 0 until tempVideoExtractor.trackCount) {
                val fmt = tempVideoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    videoDurationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                    break
                }
            }
        } finally {
            tempVideoExtractor.release()
        }

        // 檢查並轉換 BGM 格式
        val processedBgmPath = preprocessBgmFile(context, inputBgmPath, bgmVolume, loopBgm, videoDurationUs, bgmStartOffsetUs, bgmEndOffsetUs, lengthAdjustMode)
        if (processedBgmPath == null) {
            throw IllegalArgumentException("無法處理 BGM 檔案格式")
        }

        val videoExtractor = MediaExtractor()
        val bgmExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            // 準備 Extractors（添加錯誤處理）
            try {
                videoExtractor.setDataSource(inputVideoPath)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "影片 MediaExtractor 初始化成功")
            } catch (e: Exception) {
                throw IllegalArgumentException("無法初始化影片 MediaExtractor: ${e.message}", e)
            }
            
            try {
                bgmExtractor.setDataSource(processedBgmPath)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM MediaExtractor 初始化成功")
            } catch (e: Exception) {
                videoExtractor.release() // 釋放已初始化的 extractor
                throw IllegalArgumentException("無法初始化 BGM MediaExtractor: ${e.message}", e)
            }

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "步驟 1: 準備 MediaExtractor")

            // 找 video/audio tracks
            var videoTrackIdx = -1
            var videoFormat: MediaFormat? = null
            var bgmTrackIdx = -1
            var bgmFormat: MediaFormat? = null

            // 找影片軌道
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "影片軌道 $i: $mime")
                if (mime?.startsWith("video/") == true) {
                    videoTrackIdx = i
                    videoFormat = fmt
                    break
                }
            }

            // 找BGM軌道
            for (i in 0 until bgmExtractor.trackCount) {
                val fmt = bgmExtractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM軌道 $i: $mime")
                // 檢查是否為支援的音訊格式
                if (mime?.startsWith("audio/") == true) {
                    if (isSupportedAudioFormat(mime)) {
                        bgmTrackIdx = i
                        bgmFormat = fmt
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "找到支援的音訊軌道: $mime")
                        break
                    } else {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "轉換後的 BGM 仍然是不支援的格式: $mime")
                    }
                }
            }

            require(videoTrackIdx >= 0) { "No video track in input." }
            require(bgmTrackIdx >= 0) { "No supported audio track in BGM file. Only AAC format is supported for direct MP4 muxing" }

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "影片軌道: $videoTrackIdx")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM軌道: $bgmTrackIdx")

            // 選取軌道
            videoExtractor.selectTrack(videoTrackIdx)
            bgmExtractor.selectTrack(bgmTrackIdx)

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "步驟 2: 選取軌道完成")

            // 創建 Muxer
            safeDelete(outputPath)
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 添加軌道
            val outVideoTrack = muxer.addTrack(videoFormat!!)
            val outAudioTrack = muxer.addTrack(bgmFormat!!)

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "添加影片軌道: $outVideoTrack")
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "添加音訊軌道: $outAudioTrack")

            // 啟動 Muxer
            muxer.start()

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "步驟 3: 啟動 MediaMuxer")

            // 複製影片軌道
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "步驟 4: 開始複製影片軌道")
            copyVideoTrack(videoExtractor, muxer, outVideoTrack)

            // 複製音訊軌道並循環
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "步驟 5: 開始複製音訊軌道")
            copyAudioTrackWithLoop(
                bgmExtractor, 
                muxer, 
                outAudioTrack, 
                videoFormat.getLong(MediaFormat.KEY_DURATION),
                bgmVolume,
                loopBgm
            )

            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "=== 背景音樂混音完成 ===")

        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "背景音樂混音失敗: ${e.message}")
            throw e
        } finally {
            try { videoExtractor.release() } catch (_: Throwable) {}
            try { bgmExtractor.release() } catch (_: Throwable) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Throwable) {}
            
            // 清理臨時轉換的檔案
            if (processedBgmPath != inputBgmPath) {
                try { File(processedBgmPath).delete() } catch (_: Throwable) {}
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "資源釋放完成")
        }
    }

    private fun copyVideoTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outTrackIndex: Int
    ) {
        val bufferSize = 1 shl 20 // 1MB
        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()
        var sampleCount = 0
        
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(outTrackIndex, buffer, info)
            extractor.advance()
            sampleCount++
            
            if (sampleCount % 100 == 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "已複製 $sampleCount 個影片樣本")
            }
        }
        
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "影片軌道複製完成，總共 $sampleCount 個樣本")
    }

    private fun copyAudioTrackWithLoop(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outTrackIndex: Int,
        videoDurationUs: Long,
        bgmVolume: Float,
        loopBgm: Boolean
    ) {
        val bufferSize = 1 shl 20 // 1MB
        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()
        var sampleCount = 0
        var currentTimeUs = 0L
        var loopCount = 0

        // 獲取音訊軌道時長
        val audioDurationUs = extractor.getTrackFormat(extractor.sampleTrackIndex).getLong(MediaFormat.KEY_DURATION)
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "影片時長: ${videoDurationUs}us (${videoDurationUs / 1000000}s)")
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "音訊時長: ${audioDurationUs}us (${audioDurationUs / 1000000}s)")

        // 決定是否需要循環
        val needLoop = loopBgm && audioDurationUs < videoDurationUs
        
        if (needLoop) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "音訊較短且啟用循環，將循環播放")
        } else if (audioDurationUs < videoDurationUs) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "音訊較短但未啟用循環，將播放一次後靜音")
        } else {
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "音訊較長，將在影片結束時停止")
        }

        while (currentTimeUs < videoDurationUs) {
            // 重置到開始位置（每次循環）
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            loopCount++
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "開始音訊循環 $loopCount，當前時間: ${currentTimeUs / 1000000}s")

            var loopStartPtsUs = currentTimeUs
            var loopBasePtsUs = 0L // 該循環第一個 frame 的 PTS
            var samplesInThisLoop = 0
            var isFirstFrameInLoop = true

            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "音訊檔案讀取完畢")
                    break
                }

                val samplePtsUs = extractor.sampleTime
                
                // 記錄該循環第一個 frame 的 PTS
                if (isFirstFrameInLoop) {
                    loopBasePtsUs = samplePtsUs
                    isFirstFrameInLoop = false
                }
                
                // 修正的 PTS 計算：在每個循環中，使用相對時間
                val outputPtsUs = loopStartPtsUs + (samplePtsUs - loopBasePtsUs)
                
                // 檢查是否超過影片長度
                if (outputPtsUs >= videoDurationUs) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "已達到影片長度，停止音訊處理")
                    break
                }

                // 注意：這裡不再對壓縮音訊做音量處理，因為會破壞位元流
                // 音量處理應該在轉碼路徑中進行
                
                info.offset = 0
                info.size = size
                info.presentationTimeUs = outputPtsUs
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(outTrackIndex, buffer, info)
                extractor.advance()
                sampleCount++
                samplesInThisLoop++

                if (sampleCount % 1000 == 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "已處理 $sampleCount 個音訊樣本，當前時間: ${outputPtsUs / 1000000}s")
                }
            }

            // 更新循環開始時間
            currentTimeUs = loopStartPtsUs + audioDurationUs

            // 如果不需要循環或已達到影片長度，退出
            if (!needLoop || currentTimeUs >= videoDurationUs) {
                break
            }
        }

        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "音訊軌道複製完成，總共 $sampleCount 個樣本，循環 $loopCount 次")
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "最終音訊時長: ${currentTimeUs / 1000000}s")
    }

    /**
     * 應用音訊效果：音量控制
     */
    private fun applyAudioEffects(
        buffer: ByteBuffer,
        size: Int,
        currentTimeUs: Long,
        totalDurationUs: Long,
        volume: Float
    ): ByteBuffer {
        if (volume == 1.0f) {
            // 沒有音量變化，直接返回原緩衝區
            buffer.limit(buffer.position() + size)
            return buffer
        }

        // 創建新的緩衝區來存儲處理後的數據
        val processedBuffer = ByteBuffer.allocate(size)
        val originalPosition = buffer.position()
        
        // 將 ByteBuffer 轉換為 ShortArray 進行處理
        val shorts = ShortArray(size / 2)
        buffer.asShortBuffer().get(shorts, 0, size / 2)
        
        // 應用音量
        for (i in shorts.indices) {
            shorts[i] = (shorts[i] * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        // 將處理後的數據寫回緩衝區
        processedBuffer.asShortBuffer().put(shorts)
        processedBuffer.rewind()
        
        // 恢復原始緩衝區位置
        buffer.position(originalPosition)
        
        return processedBuffer
    }

    /**
     * 預處理 BGM 檔案，檢查格式並在需要時轉換為支援的格式
     */
    private fun preprocessBgmFile(
        context: Context, 
        bgmPath: String, 
        bgmVolume: Float, 
        loopBgm: Boolean, 
        videoDurationUs: Long,
        bgmStartOffsetUs: Long,
        bgmEndOffsetUs: Long,
        lengthAdjustMode: String
    ): String? {
        return try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(bgmPath)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM 預處理 MediaExtractor 初始化成功")
            } catch (e: Exception) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "BGM 預處理 MediaExtractor 初始化失敗: ${e.message}")
                extractor.release()
                return null
            }
            
            var hasSupportedAudio = false
            var hasUnsupportedAudio = false
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM 軌道 $i MIME: $mime")
                if (mime?.startsWith("audio/") == true) {
                    if (isSupportedAudioFormat(mime)) {
                        hasSupportedAudio = true
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "支援的音訊格式: $mime")
                    } else {
                        hasUnsupportedAudio = true
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "不支援的音訊格式: $mime")
                    }
                }
            }
            extractor.release()
            
            if (hasSupportedAudio) {
                // 如果已有支援的音訊軌道，檢查是否需要轉碼
                if (needsTranscoding(bgmVolume, loopBgm)) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "需要音量處理或循環，進行轉碼: $bgmPath")
                    val convertedPath = convertUsingAudioMixUtils(context, bgmPath, bgmVolume, loopBgm, videoDurationUs, bgmStartOffsetUs, bgmEndOffsetUs, lengthAdjustMode)
                    if (convertedPath != null) {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM 已轉碼處理: $convertedPath")
                        return convertedPath
                    } else {
                        com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "BGM 轉碼處理失敗")
                        return null
                    }
                } else {
                    // 不需要轉碼，直接使用原檔案
                    return bgmPath
                }
            }
            
            if (hasUnsupportedAudio) {
                // 如果有不支援的音訊軌道，使用專業的轉換工具
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM 格式需要轉換為 AAC: $bgmPath")
                val convertedPath = convertUsingAudioMixUtils(context, bgmPath, bgmVolume, loopBgm, videoDurationUs, bgmStartOffsetUs, bgmEndOffsetUs, lengthAdjustMode)
                if (convertedPath != null) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "BGM 已轉換為 AAC: $convertedPath")
                    return convertedPath
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "BGM 轉換失敗")
                    return null
                }
            }
            
            // 沒有音訊軌道
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "BGM 檔案沒有音訊軌道")
            null
            
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "預處理 BGM 檔案異常: ${e.message}")
            null
        }
    }

    /**
     * 檢查音訊格式是否被 MediaMuxer 支援
     * 注意：只有 AAC 格式能被 MediaMuxer 加入 MP4 容器
     */
    private fun isSupportedAudioFormat(mimeType: String): Boolean {
        return when (mimeType.lowercase()) {
            "audio/aac",
            "audio/mp4a-latm" -> true
            else -> false
        }
    }

    /**
     * 檢查是否需要轉碼處理
     * 當音量 != 1.0 或需要循環時，必須轉碼
     */
    private fun needsTranscoding(bgmVolume: Float, loopBgm: Boolean): Boolean {
        return bgmVolume != 1.0f || loopBgm
    }

    /**
     * 獲取音訊檔案格式信息
     */
    private fun getAudioFormatInfo(audioPath: String): AudioFormatInfo? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioPath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    extractor.release()
                    return AudioFormatInfo(sampleRate, channelCount)
                }
            }
            extractor.release()
            null
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "獲取音訊格式信息失敗: ${e.message}")
            null
        }
    }

    /**
     * 音訊格式信息數據類
     */
    private data class AudioFormatInfo(
        val sampleRate: Int,
        val channelCount: Int
    )

    /**
     * 使用 AudioMixUtils 將音訊檔案轉換為 AAC 格式
     */
    private fun convertUsingAudioMixUtils(
        context: Context, 
        inputPath: String, 
        bgmVolume: Float, 
        loopBgm: Boolean, 
        videoDurationUs: Long,
        bgmStartOffsetUs: Long,
        bgmEndOffsetUs: Long,
        lengthAdjustMode: String
    ): String? {
        return try {
            val outputDir = com.example.videoeditor.utils.VideoUtils.getAppFilesDirectory(context)
            val outputPath = File(outputDir, "converted_bgm_${System.currentTimeMillis()}.m4a")
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "開始使用 AudioMixUtils 轉換: $inputPath -> ${outputPath.absolutePath}")
            
            // 臨時調用 AudioMixUtils 的自我測試功能
            com.example.videoeditor.utils.AudioMixUtils.testDecodeMp3ToPcmFunction(inputPath)
            
            // 先獲取原始音訊格式信息
            val originalFormat = getAudioFormatInfo(inputPath)
            if (originalFormat == null) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "無法獲取原始音訊格式信息")
                return null
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "原始音訊格式: 採樣率=${originalFormat.sampleRate}, 聲道數=${originalFormat.channelCount}")
            
            // 收集所有 PCM 數據
            val allPcmData = mutableListOf<ShortArray>()
            val sampleRate = originalFormat.sampleRate
            val channelCount = originalFormat.channelCount
            
            // 解碼 MP3 到 PCM
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "準備調用 decodeMp3ToPcm")
            try {
                com.example.videoeditor.utils.AudioMixUtils.decodeMp3ToPcm(inputPath) { pcm, ptsUs ->
                    allPcmData.add(pcm)
                }
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "decodeMp3ToPcm 調用完成")
            } catch (e: Exception) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "decodeMp3ToPcm 調用失敗: ${e.message}")
                e.printStackTrace()
                throw e
            }
            
            if (allPcmData.isEmpty()) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "沒有解碼到 PCM 數據")
                return null
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "解碼到 ${allPcmData.size} 個 PCM 塊")
            
            // 合併所有 PCM 數據
            val totalSize = allPcmData.sumOf { it.size }
            val mergedPcm = ShortArray(totalSize)
            var offset = 0
            for (pcm in allPcmData) {
                pcm.copyInto(mergedPcm, offset)
                offset += pcm.size
            }
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "PCM 數據合併完成，總樣本數: $totalSize")
            
            // 編碼 PCM 為 AAC（支援音量處理、循環和時間控制）
            val success = com.example.videoeditor.utils.AudioMixUtils.encodePcmToAac(
                pcmData = mergedPcm,
                sampleRate = sampleRate,
                channelCount = channelCount,
                outputPath = outputPath.absolutePath,
                bitRate = 128000,
                volume = bgmVolume,
                loopToDuration = if (loopBgm) videoDurationUs else 0L,
                startOffsetUs = bgmStartOffsetUs,
                endOffsetUs = bgmEndOffsetUs,
                lengthAdjustMode = lengthAdjustMode
            )
            
            if (success) {
                // 檢查輸出檔案
                if (outputPath.exists() && outputPath.length() > 0) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", "SimpleBgmMixer", "AAC 轉換成功，檔案大小: ${outputPath.length()} bytes")
                    outputPath.absolutePath
                } else {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "AAC 轉換後檔案為空或不存在")
                    null
                }
            } else {
                com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "AAC 編碼失敗")
                null
            }
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", "SimpleBgmMixer", "AudioMixUtils 轉換異常: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    


    private fun safeDelete(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }
}
