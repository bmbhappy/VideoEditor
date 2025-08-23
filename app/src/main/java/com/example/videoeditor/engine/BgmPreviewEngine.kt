package com.example.videoeditor.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.example.videoeditor.utils.AudioMixUtils
import com.example.videoeditor.utils.LogDisplayManager
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

/**
 * BGM 預覽引擎
 * 支援實時預覽調整後的背景音樂效果
 */
class BgmPreviewEngine {

    private var audioTrack: AudioTrack? = null
    private var previewJob: Job? = null
    private var isPlaying = false
    private var isPrepared = false
    
    // 音訊配置
    private val outputSampleRate = 48000  // 輸出採樣率
    private var inputSampleRate = 48000   // 輸入採樣率（動態設定）
    private val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioTrack.getMinBufferSize(outputSampleRate, channelConfig, audioFormat) * 2
    
    // 預覽配置
    private var videoDurationMs: Long = 0
    private var bgmDurationMs: Long = 0
    private var previewStartMs: Long = 0
    private var previewEndMs: Long = 0
    
    companion object {
        private const val TAG = "BgmPreviewEngine"
        private const val PREVIEW_DURATION_MS = 10000L // 預覽10秒
    }

    /**
     * 初始化預覽引擎
     */
    fun initialize(context: Context) {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(outputSampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            isPrepared = true
            LogDisplayManager.addLog("D", TAG, "預覽引擎初始化成功")
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "預覽引擎初始化失敗: ${e.message}")
        }
    }

    /**
     * 設定預覽參數
     */
    fun setPreviewConfig(
        videoDurationMs: Long,
        bgmDurationMs: Long,
        startOffsetPercent: Float = 0f,
        endOffsetPercent: Float = 100f
    ) {
        this.videoDurationMs = videoDurationMs
        this.bgmDurationMs = bgmDurationMs
        
        // 計算預覽範圍（取影片的前10秒或全部）
        this.previewStartMs = (startOffsetPercent / 100f * bgmDurationMs).toLong()
        this.previewEndMs = min(
            (endOffsetPercent / 100f * bgmDurationMs).toLong(),
            previewStartMs + PREVIEW_DURATION_MS
        )
        
        LogDisplayManager.addLog("D", TAG, 
            "預覽配置: 影片${videoDurationMs}ms, BGM${bgmDurationMs}ms, 預覽${previewStartMs}-${previewEndMs}ms")
    }

    /**
     * 開始預覽
     */
    fun startPreview(
        bgmPath: String,
        mode: PreviewMode,
        volume: Float = 0.4f
    ) {
        if (!isPrepared) {
            LogDisplayManager.addLog("E", TAG, "預覽引擎未初始化")
            return
        }

        stopPreview() // 停止之前的預覽

        previewJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                isPlaying = true
                LogDisplayManager.addLog("D", TAG, "開始預覽: $mode")

                when (mode) {
                    PreviewMode.LOOP -> previewLoopMode(bgmPath, volume)
                    PreviewMode.TRIM -> previewTrimMode(bgmPath, volume)
                    PreviewMode.STRETCH -> previewStretchMode(bgmPath, volume)
                    PreviewMode.FADE_OUT -> previewFadeOutMode(bgmPath, volume)
                }

            } catch (e: Exception) {
                LogDisplayManager.addLog("E", TAG, "預覽失敗: ${e.message}")
            } finally {
                isPlaying = false
            }
        }
    }

    /**
     * 停止預覽
     */
    fun stopPreview() {
        isPlaying = false
        previewJob?.cancel()
        
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    track.stop()
                    track.flush()
                } catch (e: Exception) {
                    LogDisplayManager.addLog("W", TAG, "停止預覽時發生錯誤: ${e.message}")
                }
            }
        }
        
        LogDisplayManager.addLog("D", TAG, "預覽已停止")
    }

    /**
     * 預覽循環模式
     */
    private suspend fun previewLoopMode(bgmPath: String, volume: Float) {
        val pcmData = extractPcmData(bgmPath) ?: return
        
        audioTrack?.play()
        
        var currentTime = 0L
        val maxPreviewTime = min(PREVIEW_DURATION_MS, videoDurationMs)
        
        while (isPlaying && currentTime < maxPreviewTime) {
            val remainingTime = maxPreviewTime - currentTime
            val playDuration = min(bgmDurationMs, remainingTime)
            
            val samplesToPlay = ((playDuration * outputSampleRate) / 1000).toInt() * 2 // stereo
            val endIndex = min(samplesToPlay, pcmData.size)
            
            // 應用音量並播放
            val volumeAdjustedData = applyVolume(pcmData.sliceArray(0 until endIndex), volume)
            playPcmData(volumeAdjustedData)
            
            currentTime += playDuration
        }
    }

    /**
     * 預覽裁剪模式
     */
    private suspend fun previewTrimMode(bgmPath: String, volume: Float) {
        val pcmData = extractPcmData(bgmPath) ?: return
        
        // 計算裁剪範圍
        val startSample = ((previewStartMs * outputSampleRate) / 1000).toInt() * 2
        val endSample = min(
            ((previewEndMs * outputSampleRate) / 1000).toInt() * 2,
            pcmData.size
        )
        
        if (startSample >= endSample) return
        
        val trimmedData = pcmData.sliceArray(startSample until endSample)
        val volumeAdjustedData = applyVolume(trimmedData, volume)
        
        audioTrack?.play()
        playPcmData(volumeAdjustedData)
    }

    /**
     * 預覽拉伸模式
     */
    private suspend fun previewStretchMode(bgmPath: String, volume: Float) {
        val pcmData = extractPcmData(bgmPath) ?: return
        
        // 計算拉伸比例
        val stretchRatio = videoDurationMs.toFloat() / bgmDurationMs.toFloat()
        val targetSampleRate = (outputSampleRate * stretchRatio).toInt()
        
        // 重採樣以模擬拉伸效果
        val stretchedData = resamplePcmData(pcmData, outputSampleRate, targetSampleRate)
        val volumeAdjustedData = applyVolume(stretchedData, volume)
        
        // 播放預覽長度
        val previewSamples = ((PREVIEW_DURATION_MS * outputSampleRate) / 1000).toInt() * 2
        val dataToPlay = volumeAdjustedData.sliceArray(0 until min(previewSamples, volumeAdjustedData.size))
        
        audioTrack?.play()
        playPcmData(dataToPlay)
    }

    /**
     * 預覽淡出模式
     */
    private suspend fun previewFadeOutMode(bgmPath: String, volume: Float) {
        val pcmData = extractPcmData(bgmPath) ?: return
        
        // 模擬淡出效果（最後2秒淡出）
        val fadeOutStartMs = max(0L, PREVIEW_DURATION_MS - 2000L)
        val fadeOutSamples = ((2000L * outputSampleRate) / 1000).toInt() * 2
        
        val previewSamples = ((PREVIEW_DURATION_MS * outputSampleRate) / 1000).toInt() * 2
        val dataToPlay = pcmData.sliceArray(0 until min(previewSamples, pcmData.size))
        
        // 應用音量和淡出效果
        val processedData = applyVolumeWithFadeOut(dataToPlay, volume, fadeOutSamples)
        
        audioTrack?.play()
        playPcmData(processedData)
    }

    /**
     * 提取 PCM 數據（包含採樣率檢測和重採樣）
     */
    private suspend fun extractPcmData(audioPath: String): ShortArray? {
        return withContext(Dispatchers.IO) {
            try {
                // 首先檢測音檔的採樣率
                val detectedSampleRate = detectAudioSampleRate(audioPath)
                if (detectedSampleRate > 0) {
                    inputSampleRate = detectedSampleRate
                    LogDisplayManager.addLog("D", TAG, "檢測到音檔採樣率: ${inputSampleRate}Hz")
                }
                
                val allPcmData = mutableListOf<ShortArray>()
                
                AudioMixUtils.decodeMp3ToPcm(audioPath) { pcm, _ ->
                    allPcmData.add(pcm)
                }
                
                if (allPcmData.isEmpty()) return@withContext null
                
                // 合併所有 PCM 數據
                val totalSize = allPcmData.sumOf { it.size }
                val mergedPcm = ShortArray(totalSize)
                var offset = 0
                
                for (pcm in allPcmData) {
                    pcm.copyInto(mergedPcm, offset)
                    offset += pcm.size
                }
                
                LogDisplayManager.addLog("D", TAG, "PCM 數據提取完成，總樣本數: $totalSize")
                
                // 如果採樣率不匹配，進行重採樣
                if (inputSampleRate != outputSampleRate) {
                    LogDisplayManager.addLog("D", TAG, "重採樣: ${inputSampleRate}Hz -> ${outputSampleRate}Hz")
                    val resampled = resamplePcmData(mergedPcm, inputSampleRate, outputSampleRate)
                    LogDisplayManager.addLog("D", TAG, "重採樣完成，新樣本數: ${resampled.size}")
                    resampled
                } else {
                    mergedPcm
                }
                
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", TAG, "PCM 數據提取失敗: ${e.message}")
                null
            }
        }
    }

    /**
     * 播放 PCM 數據
     */
    private suspend fun playPcmData(pcmData: ShortArray) {
        val byteData = ByteArray(pcmData.size * 2)
        
        // 將 ShortArray 轉換為 ByteArray
        for (i in pcmData.indices) {
            val sample = pcmData[i]
            byteData[i * 2] = (sample.toInt() and 0xFF).toByte()
            byteData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        
        // 分批播放，避免阻塞
        var offset = 0
        val chunkSize = bufferSize / 4 // 小塊播放
        
        while (offset < byteData.size && isPlaying) {
            val remainingBytes = byteData.size - offset
            val bytesToWrite = min(chunkSize, remainingBytes)
            
            audioTrack?.write(byteData, offset, bytesToWrite)
            offset += bytesToWrite
            
            // 計算實際的播放延遲（基於採樣率和數據量）
            val samplesWritten = bytesToWrite / 2 // 每個樣本2字節
            val playTimeMs = (samplesWritten.toDouble() / (outputSampleRate * 2) * 1000).toLong() // 立體聲
            val delayMs = maxOf(10L, playTimeMs / 2) // 至少10ms延遲，最多是播放時間的一半
            
            delay(delayMs)
        }
    }

    /**
     * 應用音量
     */
    private fun applyVolume(pcmData: ShortArray, volume: Float): ShortArray {
        return ShortArray(pcmData.size) { i ->
            (pcmData[i] * volume).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    /**
     * 應用音量和淡出效果
     */
    private fun applyVolumeWithFadeOut(pcmData: ShortArray, volume: Float, fadeOutSamples: Int): ShortArray {
        val result = ShortArray(pcmData.size)
        val fadeStartIndex = maxOf(0, pcmData.size - fadeOutSamples)
        
        for (i in pcmData.indices) {
            var finalVolume = volume
            
            // 應用淡出效果
            if (i >= fadeStartIndex) {
                val fadeProgress = (i - fadeStartIndex).toFloat() / fadeOutSamples
                finalVolume *= (1f - fadeProgress)
            }
            
            result[i] = (pcmData[i] * finalVolume).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        
        return result
    }

    /**
     * 釋放資源
     */
    fun release() {
        stopPreview()
        
        audioTrack?.let { track ->
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.release()
                }
            } catch (e: Exception) {
                LogDisplayManager.addLog("W", TAG, "釋放 AudioTrack 時發生錯誤: ${e.message}")
            }
        }
        
        audioTrack = null
        isPrepared = false
        LogDisplayManager.addLog("D", TAG, "預覽引擎已釋放")
    }

    /**
     * 檢查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * 檢測音檔的採樣率
     */
    private fun detectAudioSampleRate(audioPath: String): Int {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioPath)
            
            var detectedSampleRate = 0
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    detectedSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    break
                }
            }
            
            extractor.release()
            detectedSampleRate
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("W", TAG, "無法檢測音檔採樣率: ${e.message}")
            0 // 返回 0 表示檢測失敗
        }
    }

    /**
     * 簡單的線性重採樣
     */
    private fun resamplePcmData(pcmData: ShortArray, inputRate: Int, outputRate: Int): ShortArray {
        if (inputRate == outputRate) return pcmData
        
        val ratio = outputRate.toDouble() / inputRate.toDouble()
        val outputSize = (pcmData.size * ratio).toInt()
        val resampled = ShortArray(outputSize)
        
        for (i in resampled.indices) {
            val sourceIndex = (i / ratio).toInt()
            if (sourceIndex < pcmData.size) {
                resampled[i] = pcmData[sourceIndex]
            }
        }
        
        return resampled
    }

    /**
     * 預覽模式枚舉
     */
    enum class PreviewMode {
        LOOP,      // 循環播放
        TRIM,      // 裁剪模式
        STRETCH,   // 拉伸模式
        FADE_OUT   // 淡出模式
    }
}
