package com.example.videoeditor.utils

import android.media.*
import android.util.Log
import java.io.File

/**
 * 音訊混音工具類
 * 提供 MP3 解碼、PCM 處理、混音等功能
 */
object AudioMixUtils {
    
    private const val TAG = "AudioMixUtils"
    
    /**
     * 解碼 MP3 到 PCM
     */
    fun decodeMp3ToPcm(mp3Path: String, onFrame: (pcm: ShortArray, ptsUs: Long) -> Unit) {
        LogDisplayManager.addLog("D", TAG, "=== decodeMp3ToPcm 方法開始 ===")
        LogDisplayManager.addLog("D", TAG, "參數: mp3Path=$mp3Path")
        
        val extractor = MediaExtractor()
        
        try {
            LogDisplayManager.addLog("D", TAG, "開始解碼 MP3: $mp3Path")
            
            // 檢查檔案是否存在
            val file = File(mp3Path)
            if (!file.exists()) {
                throw IllegalArgumentException("MP3 檔案不存在: $mp3Path")
            }
            LogDisplayManager.addLog("D", TAG, "MP3 檔案存在，大小: ${file.length()} bytes")
            
            extractor.setDataSource(mp3Path)
            LogDisplayManager.addLog("D", TAG, "MediaExtractor 設置數據源成功")

            // 找到 MP3 音訊 Track
            LogDisplayManager.addLog("D", TAG, "開始查找音訊軌道，總軌道數: ${extractor.trackCount}")
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                LogDisplayManager.addLog("D", TAG, "軌道 $i MIME: $mime")
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    extractor.selectTrack(i)
                    LogDisplayManager.addLog("D", TAG, "選擇音訊軌道: $i")
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                LogDisplayManager.addLog("E", TAG, "找不到音訊軌道")
                throw IllegalStateException("找不到音訊 Track")
            }
            
            LogDisplayManager.addLog("D", TAG, "選擇音訊軌道: $audioTrackIndex")

            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            LogDisplayManager.addLog("D", TAG, "創建解碼器: $mime")
            LogDisplayManager.addLog("D", TAG, "音訊格式: $format")
            
            val codec = MediaCodec.createDecoderByType(mime)
            LogDisplayManager.addLog("D", TAG, "解碼器創建成功")
            
            codec.configure(format, null, null, 0)
            LogDisplayManager.addLog("D", TAG, "解碼器配置成功")
            
            codec.start()
            LogDisplayManager.addLog("D", TAG, "解碼器啟動成功")

            val bufferInfo = MediaCodec.BufferInfo()
            var frameCount = 0
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                // --- Input ---
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                            LogDisplayManager.addLog("D", TAG, "輸入 EOS")
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                // --- Output ---
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        if (bufferInfo.size > 0) {
                            // 將 ByteBuffer 轉換為 ShortArray
                            val shorts = ShortArray(bufferInfo.size / 2)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            outputBuffer.asShortBuffer().get(shorts)
                            
                            onFrame(shorts, bufferInfo.presentationTimeUs)
                            frameCount++
                            
                            if (frameCount % 100 == 0) {
                                LogDisplayManager.addLog("D", TAG, "已解碼 $frameCount 幀")
                            }
                        }

                        codec.releaseOutputBuffer(outputIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEOS = true
                            LogDisplayManager.addLog("D", TAG, "輸出 EOS，解碼完成")
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        LogDisplayManager.addLog("D", TAG, "解碼器輸出格式變更: $newFormat")
                    }
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 沒有輸出可用
                    }
                }
            }

            LogDisplayManager.addLog("D", TAG, "解碼完成，總幀數: $frameCount")
            codec.stop()
            codec.release()
            extractor.release()
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "MP3 解碼失敗: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 根據樣本數計算 PTS (單位: us)
     */
    fun computePtsUs(samplesWritten: Long, sampleRate: Int): Long {
        return samplesWritten * 1_000_000L / sampleRate
    }

    /**
     * 將單聲道轉換成立體聲
     */
    fun monoToStereo(src: ShortArray): ShortArray {
        val out = ShortArray(src.size * 2)
        for (i in src.indices) {
            out[i * 2] = src[i]
            out[i * 2 + 1] = src[i]
        }
        return out
    }

    /**
     * 將立體聲轉換成單聲道
     */
    fun stereoToMono(src: ShortArray): ShortArray {
        val out = ShortArray(src.size / 2)
        var j = 0
        for (i in out.indices) {
            val l = src[j++].toInt()
            val r = src[j++].toInt()
            out[i] = ((l + r) / 2).toShort()
        }
        return out
    }

    /**
     * 混音 (with volume control)
     * 主聲道與 BGM 混合，並防止溢位
     */
    fun mixPcm(
        main: ShortArray,
        bgm: ShortArray,
        mainVol: Float = 1.0f,
        bgmVol: Float = 1.0f
    ): ShortArray {
        val len = minOf(main.size, bgm.size)
        val out = ShortArray(len)
        for (i in 0 until len) {
            val mixed = (main[i] * mainVol + bgm[i] * bgmVol).toInt()
            out[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /**
     * 線性重採樣 (例如 44.1k → 48k)
     */
    fun resampleLinear(src: ShortArray, srcSr: Int, dstSr: Int): ShortArray {
        if (srcSr == dstSr) return src
        val ratio = dstSr.toDouble() / srcSr
        val out = ShortArray((src.size * ratio).toInt())
        for (i in out.indices) {
            val x = i / ratio
            val x0 = x.toInt().coerceIn(0, src.lastIndex)
            val x1 = (x0 + 1).coerceIn(0, src.lastIndex)
            val t = (x - x0)
            val y = src[x0] * (1 - t) + src[x1] * t
            out[i] = y.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /**
     * BGM 循環 (crossfade loop)
     */
    fun loopWithCrossfade(
        src: ShortArray,
        fadeMs: Int,
        sampleRate: Int,
        channels: Int
    ): ShortArray {
        val fadeSamples = (fadeMs * sampleRate / 1000) * channels
        if (src.size < fadeSamples * 2) return src // 太短不處理

        val out = src.copyOf()
        for (i in 0 until fadeSamples) {
            val t = i / fadeSamples.toFloat()
            val a = (1f - t)
            val b = t
            val mixed = (src[src.size - fadeSamples + i] * a + src[i] * b).toInt()
            out[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    /**
     * 將 PCM 編碼為 AAC（支援音量處理、循環和時間控制）
     */
    fun encodePcmToAac(
        pcmData: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        outputPath: String,
        bitRate: Int = 128000,
        volume: Float = 1.0f,
        loopToDuration: Long = 0L,
        startOffsetUs: Long = 0L,
        endOffsetUs: Long = 0L,
        lengthAdjustMode: String = "LOOP"
    ): Boolean {
        return try {
            LogDisplayManager.addLog("D", TAG, "開始 AAC 編碼: 樣本數=${pcmData.size}, 採樣率=$sampleRate, 聲道數=$channelCount, 音量=$volume, 循環時長=${loopToDuration}us")
            
            // 處理音量、循環和時間控制
            val processedPcmData = processAudioData(pcmData, sampleRate, channelCount, volume, loopToDuration, startOffsetUs, endOffsetUs, lengthAdjustMode)
            LogDisplayManager.addLog("D", TAG, "音訊處理完成，處理後樣本數: ${processedPcmData.size}")
            
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            
            LogDisplayManager.addLog("D", TAG, "配置 AAC 編碼器")
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            LogDisplayManager.addLog("D", TAG, "AAC 編碼器啟動成功")
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var outputTrackIndex = -1
            var muxerStarted = false
            
            val bufferInfo = MediaCodec.BufferInfo()
            var samplesWritten = 0L
            var encodedFrames = 0
            
            // 將 PCM 數據分批送入編碼器
            val frameSize = 1024 * channelCount // AAC 每幀 1024 樣本
            var offset = 0
            
            LogDisplayManager.addLog("D", TAG, "開始編碼循環，總樣本數: ${processedPcmData.size}, 幀大小: $frameSize")
            
            while (offset < processedPcmData.size) {
                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val remaining = processedPcmData.size - offset
                        val toWrite = minOf(frameSize, remaining)
                        
                        if (toWrite > 0) {
                            inputBuffer.clear()
                            inputBuffer.asShortBuffer().put(processedPcmData, offset, toWrite)
                            
                            val ptsUs = computePtsUs(samplesWritten, sampleRate)
                            encoder.queueInputBuffer(inputIndex, 0, toWrite * 2, ptsUs, 0)
                            
                            offset += toWrite
                            samplesWritten += toWrite / channelCount
                        } else {
                            // 發送 EOS
                            LogDisplayManager.addLog("D", TAG, "發送 EOS 標記")
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }
                
                // 處理編碼輸出
                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when (outputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = encoder.outputFormat
                        outputTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                        LogDisplayManager.addLog("D", TAG, "AAC 編碼器格式變更，輸出軌道索引: $outputTrackIndex")
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 沒有輸出可用
                    }
                    else -> {
                        if (outputIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            if (bufferInfo.size > 0 && outputBuffer != null && muxerStarted) {
                                muxer.writeSampleData(outputTrackIndex, outputBuffer, bufferInfo)
                                encodedFrames++
                                
                                if (encodedFrames % 100 == 0) {
                                    LogDisplayManager.addLog("D", TAG, "已編碼 $encodedFrames 幀")
                                }
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                            
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                LogDisplayManager.addLog("D", TAG, "收到 EOS 標記，編碼完成")
                                break
                            }
                        }
                    }
                }
            }
            
            LogDisplayManager.addLog("D", TAG, "停止編碼器")
            encoder.stop()
            encoder.release()
            
            LogDisplayManager.addLog("D", TAG, "停止 Muxer")
            muxer.stop()
            muxer.release()
            
            // 檢查輸出檔案
            val outputFile = File(outputPath)
            if (outputFile.exists() && outputFile.length() > 0) {
                LogDisplayManager.addLog("D", TAG, "PCM 編碼為 AAC 完成: $outputPath, 檔案大小: ${outputFile.length()} bytes")
                true
            } else {
                LogDisplayManager.addLog("E", TAG, "AAC 編碼完成但輸出檔案為空或不存在")
                false
            }
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "PCM 編碼為 AAC 失敗: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 處理音訊數據（音量調整、循環和時間控制）
     */
    private fun processAudioData(
        pcmData: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        volume: Float,
        loopToDuration: Long,
        startOffsetUs: Long,
        endOffsetUs: Long,
        lengthAdjustMode: String
    ): ShortArray {
        LogDisplayManager.addLog("D", TAG, "開始處理音訊數據: 音量=$volume, 循環時長=${loopToDuration}us, 開始偏移=${startOffsetUs}us, 結束偏移=${endOffsetUs}us, 模式=$lengthAdjustMode")
        
        // 應用音量
        val volumeAdjustedData = if (volume != 1.0f) {
            LogDisplayManager.addLog("D", TAG, "應用音量調整: $volume")
            pcmData.map { (it * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }.toShortArray()
        } else {
            pcmData
        }
        
        // 處理時間控制
        var timeAdjustedData = volumeAdjustedData
        if (startOffsetUs > 0 || endOffsetUs > 0) {
            val startSample = (startOffsetUs * sampleRate / 1000000L * channelCount).toInt()
            val endSample = if (endOffsetUs > 0) {
                (endOffsetUs * sampleRate / 1000000L * channelCount).toInt()
            } else {
                volumeAdjustedData.size
            }
            
            val actualEndSample = minOf(endSample, volumeAdjustedData.size)
            val actualStartSample = minOf(startSample, actualEndSample)
            
            if (actualStartSample < actualEndSample) {
                timeAdjustedData = volumeAdjustedData.slice(actualStartSample until actualEndSample).toShortArray()
                LogDisplayManager.addLog("D", TAG, "時間裁剪: 從樣本 $actualStartSample 到 $actualEndSample (總樣本數: ${volumeAdjustedData.size})")
            } else {
                LogDisplayManager.addLog("W", TAG, "時間裁剪參數無效，使用原始數據")
            }
        }
        
        // 處理循環
        if (loopToDuration > 0) {
            val originalDurationUs = (timeAdjustedData.size / channelCount * 1000000L) / sampleRate
            LogDisplayManager.addLog("D", TAG, "時間調整後音訊時長: ${originalDurationUs}us, 目標時長: ${loopToDuration}us")
            
            if (originalDurationUs < loopToDuration) {
                val targetSamples = (loopToDuration * sampleRate / 1000000L * channelCount).toInt()
                val loopCount = (targetSamples / timeAdjustedData.size) + 1
                LogDisplayManager.addLog("D", TAG, "需要循環 $loopCount 次")
                
                val loopedData = ShortArray(targetSamples)
                var offset = 0
                for (i in 0 until loopCount) {
                    val remaining = targetSamples - offset
                    val toCopy = minOf(timeAdjustedData.size, remaining)
                    timeAdjustedData.copyInto(loopedData, offset, 0, toCopy)
                    offset += toCopy
                    if (offset >= targetSamples) break
                }
                
                LogDisplayManager.addLog("D", TAG, "循環處理完成，最終樣本數: ${loopedData.size}")
                return loopedData
            }
        }
        
        return timeAdjustedData
    }

    /**
     * 自我測試 decodeMp3ToPcm 功能
     */
    fun testDecodeMp3ToPcmFunction(mp3FilePath: String) {
        LogDisplayManager.addLog("D", TAG, "--- 開始測試 decodeMp3ToPcm 功能 ---")
        LogDisplayManager.addLog("D", TAG, "測試檔案路徑: $mp3FilePath")
        try {
            val testPcmData = mutableListOf<ShortArray>()
            decodeMp3ToPcm(mp3FilePath) { pcm, ptsUs ->
                testPcmData.add(pcm)
                LogDisplayManager.addLog("D", TAG, "測試解碼幀: PCM大小=${pcm.size}, PTS=${ptsUs}us")
            }
            if (testPcmData.isNotEmpty()) {
                LogDisplayManager.addLog("D", TAG, "--- decodeMp3ToPcm 測試成功！共解碼 ${testPcmData.size} 幀 PCM 數據 ---")
            } else {
                LogDisplayManager.addLog("E", TAG, "--- decodeMp3ToPcm 測試失敗：未解碼到任何 PCM 數據 ---")
            }
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "--- decodeMp3ToPcm 測試中發生異常: ${e.message} ---")
            e.printStackTrace()
        } finally {
            LogDisplayManager.addLog("D", TAG, "--- 結束測試 decodeMp3ToPcm 功能 ---")
        }
    }
}
