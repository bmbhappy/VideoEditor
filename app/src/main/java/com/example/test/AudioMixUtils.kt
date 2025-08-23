package com.example.test

import android.media.*
import android.util.Log
import com.example.videoeditor.utils.LogDisplayManager
import java.io.File
import java.nio.ByteBuffer

/**
 * 測試用音訊混音工具
 * 提供簡單的 PCM 混音和音訊處理功能
 */
object AudioMixUtils {
    
    private const val TAG = "TestAudioMixUtils"
    
    /**
     * 簡單 PCM16 混音：原聲音量=1.0，BGM 音量=0.5
     */
    fun mixPcm(orig: ShortArray, bgm: ShortArray): ShortArray {
        val len = minOf(orig.size, bgm.size)
        val out = ShortArray(len)
        for (i in 0 until len) {
            val mixed = orig[i].toInt() + (bgm[i] * 0.5f).toInt()
            out[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
    
    /**
     * 可調音量 PCM16 混音
     */
    fun mixPcmWithVolume(
        orig: ShortArray, 
        bgm: ShortArray, 
        origVolume: Float = 1.0f, 
        bgmVolume: Float = 0.5f
    ): ShortArray {
        val len = minOf(orig.size, bgm.size)
        val out = ShortArray(len)
        for (i in 0 until len) {
            val mixed = (orig[i] * origVolume + bgm[i] * bgmVolume).toInt()
            out[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }
    
    /**
     * 解碼音訊檔案到 PCM
     */
    fun decodeAudioToPcm(audioPath: String): ShortArray? {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioPath)
            
            // 找到音訊軌道
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                LogDisplayManager.addLog("E", TAG, "未找到音訊軌道")
                return null
            }
            
            extractor.selectTrack(audioTrackIndex)
            
            // 創建解碼器
            val decoder = MediaCodec.createDecoderByType(audioFormat!!.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()
            
            val allPcmData = mutableListOf<ShortArray>()
            val bufferInfo = MediaCodec.BufferInfo()
            
            var sawInputEOS = false
            var sawOutputEOS = false
            
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputIndex = decoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                
                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (bufferInfo.size > 0 && outputBuffer != null) {
                        val pcmData = ShortArray(bufferInfo.size / 2)
                        outputBuffer.asShortBuffer().get(pcmData)
                        allPcmData.add(pcmData)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                }
            }
            
            decoder.stop()
            decoder.release()
            extractor.release()
            
            // 合併所有 PCM 數據
            val totalSize = allPcmData.sumOf { it.size }
            val mergedPcm = ShortArray(totalSize)
            var offset = 0
            for (pcm in allPcmData) {
                pcm.copyInto(mergedPcm, offset)
                offset += pcm.size
            }
            
            LogDisplayManager.addLog("D", TAG, "音訊解碼完成，總樣本數: $totalSize")
            mergedPcm
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "音訊解碼失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 將 PCM 數據編碼為 AAC 檔案
     */
    fun encodePcmToAac(pcmData: ShortArray, outputPath: String, sampleRate: Int = 48000, channelCount: Int = 2): Boolean {
        return try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                channelCount
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var outputTrackIndex = -1
            var muxerStarted = false
            
            val bufferInfo = MediaCodec.BufferInfo()
            var samplesWritten = 0L
            
            // 分批處理 PCM 數據
            val frameSize = 1024 * channelCount // AAC 每幀 1024 樣本
            var offset = 0
            
            while (offset < pcmData.size) {
                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val remaining = pcmData.size - offset
                        val toWrite = minOf(frameSize, remaining)
                        
                        if (toWrite > 0) {
                            inputBuffer.clear()
                            inputBuffer.asShortBuffer().put(pcmData, offset, toWrite)
                            
                            val ptsUs = samplesWritten * 1_000_000L / sampleRate
                            encoder.queueInputBuffer(inputIndex, 0, toWrite * 2, ptsUs, 0)
                            
                            offset += toWrite
                            samplesWritten += toWrite / channelCount
                        } else {
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
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 沒有輸出可用
                    }
                    else -> {
                        if (outputIndex >= 0) {
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                            if (bufferInfo.size > 0 && outputBuffer != null && muxerStarted) {
                                muxer.writeSampleData(outputTrackIndex, outputBuffer, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outputIndex, false)
                            
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break
                            }
                        }
                    }
                }
            }
            
            encoder.stop()
            encoder.release()
            muxer.stop()
            muxer.release()
            
            LogDisplayManager.addLog("D", TAG, "PCM 編碼為 AAC 完成: $outputPath")
            true
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "PCM 編碼失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 計算音訊的 RMS (Root Mean Square) 值
     */
    fun calculateRms(pcmData: ShortArray): Double {
        var sum = 0.0
        for (sample in pcmData) {
            sum += sample * sample
        }
        return kotlin.math.sqrt(sum / pcmData.size)
    }
    
    /**
     * 計算音訊的峰值
     */
    fun calculatePeak(pcmData: ShortArray): Int {
        return pcmData.maxOrNull()?.toInt() ?: 0
    }
    
    /**
     * 檢查音訊是否靜音
     */
    fun isSilent(pcmData: ShortArray, threshold: Int = 100): Boolean {
        return calculateRms(pcmData) < threshold
    }
    
    /**
     * 音訊統計資訊
     */
    data class AudioStats(
        val sampleCount: Int,
        val durationMs: Long,
        val rms: Double,
        val peak: Int,
        val isSilent: Boolean
    )
    
    /**
     * 獲取音訊統計資訊
     */
    fun getAudioStats(pcmData: ShortArray, sampleRate: Int = 48000): AudioStats {
        val durationMs = (pcmData.size * 1000L) / (sampleRate * 2) // 假設立體聲
        val rms = calculateRms(pcmData)
        val peak = calculatePeak(pcmData)
        val silent = isSilent(pcmData)
        
        return AudioStats(
            sampleCount = pcmData.size,
            durationMs = durationMs,
            rms = rms,
            peak = peak,
            isSilent = silent
        )
    }
}
