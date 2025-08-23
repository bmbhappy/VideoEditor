package com.example.test

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.example.videoeditor.engine.SimpleBgmMixer
import com.example.videoeditor.utils.LogDisplayManager
import com.example.videoeditor.utils.VideoUtils
import java.io.File
import java.nio.ShortBuffer
import kotlin.math.sqrt

/**
 * 音訊管道測試器
 * 用於驗證音訊處理功能的完整性和正確性
 */
class AudioPipelineTester(private val context: Context) {
    
    private val TAG = "AudioPipelineTester"
    
    /**
     * 測試結果
     */
    data class TestResult(
        val testName: String,
        val success: Boolean,
        val message: String,
        val duration: Long,
        val outputPath: String? = null
    )
    
    /**
     * 完整測試套件
     */
    data class TestSuite(
        val name: String,
        val tests: List<TestResult>,
        val totalDuration: Long,
        val successCount: Int,
        val failureCount: Int
    ) {
        val successRate: Double get() = if (tests.isNotEmpty()) successCount.toDouble() / tests.size else 0.0
    }
    
    /**
     * 執行完整音訊管道測試
     */
    fun runFullPipelineTest(
        videoPath: String,
        bgmPath: String,
        outputDir: String
    ): TestSuite {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<TestResult>()
        
        LogDisplayManager.addLog("I", TAG, "開始執行完整音訊管道測試")
        
        // 測試 1: 檔案存在性檢查
        results.add(testFileExistence(videoPath, bgmPath))
        
        // 測試 2: 音訊格式檢查
        results.add(testAudioFormat(bgmPath))
        
        // 測試 3: 音訊解碼測試
        results.add(testAudioDecoding(bgmPath))
        
        // 測試 4: PCM 混音測試
        results.add(testPcmMixing())
        
        // 測試 5: 簡單 BGM 混音測試
        val simpleMixResult = testSimpleBgmMixing(videoPath, bgmPath, outputDir)
        results.add(simpleMixResult)
        
        // 測試 6: 輸出檔案驗證
        if (simpleMixResult.success && simpleMixResult.outputPath != null) {
            results.add(testOutputFile(simpleMixResult.outputPath))
        }
        
        // 測試 7: 音訊品質檢查
        if (simpleMixResult.success && simpleMixResult.outputPath != null) {
            results.add(testAudioQuality(simpleMixResult.outputPath))
        }
        
        val totalDuration = System.currentTimeMillis() - startTime
        val successCount = results.count { it.success }
        val failureCount = results.size - successCount
        
        val testSuite = TestSuite(
            name = "完整音訊管道測試",
            tests = results,
            totalDuration = totalDuration,
            successCount = successCount,
            failureCount = failureCount
        )
        
        LogDisplayManager.addLog("I", TAG, 
            "測試完成: ${successCount}/${results.size} 成功, 耗時: ${totalDuration}ms")
        
        return testSuite
    }
    
    /**
     * 測試檔案存在性
     */
    private fun testFileExistence(videoPath: String, bgmPath: String): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val videoExists = File(videoPath).exists()
            val bgmExists = File(bgmPath).exists()
            
            if (videoExists && bgmExists) {
                TestResult(
                    testName = "檔案存在性檢查",
                    success = true,
                    message = "影片和 BGM 檔案都存在",
                    duration = System.currentTimeMillis() - startTime
                )
            } else {
                TestResult(
                    testName = "檔案存在性檢查",
                    success = false,
                    message = "檔案不存在: 影片=$videoExists, BGM=$bgmExists",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName = "檔案存在性檢查",
                success = false,
                message = "檢查檔案時發生錯誤: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 測試音訊格式
     */
    private fun testAudioFormat(bgmPath: String): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(bgmPath)
            
            var hasAudioTrack = false
            var audioMimeType = ""
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    hasAudioTrack = true
                    audioMimeType = mime
                    break
                }
            }
            
            extractor.release()
            
            if (hasAudioTrack) {
                TestResult(
                    testName = "音訊格式檢查",
                    success = true,
                    message = "支援的音訊格式: $audioMimeType",
                    duration = System.currentTimeMillis() - startTime
                )
            } else {
                TestResult(
                    testName = "音訊格式檢查",
                    success = false,
                    message = "未找到支援的音訊軌道",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName = "音訊格式檢查",
                success = false,
                message = "檢查音訊格式時發生錯誤: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 測試音訊解碼
     */
    private fun testAudioDecoding(bgmPath: String): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val pcmData = AudioMixUtils.decodeAudioToPcm(bgmPath)
            
            if (pcmData != null && pcmData.isNotEmpty()) {
                val stats = AudioMixUtils.getAudioStats(pcmData)
                TestResult(
                    testName = "音訊解碼測試",
                    success = true,
                    message = "解碼成功: ${stats.sampleCount} 樣本, ${stats.durationMs}ms, RMS: ${stats.rms}",
                    duration = System.currentTimeMillis() - startTime
                )
            } else {
                TestResult(
                    testName = "音訊解碼測試",
                    success = false,
                    message = "解碼失敗: 沒有 PCM 數據",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName = "音訊解碼測試",
                success = false,
                message = "解碼時發生錯誤: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 測試 PCM 混音
     */
    private fun testPcmMixing(): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 創建測試音訊數據
            val sampleRate = 48000
            val durationMs = 1000L
            val sampleCount = (sampleRate * durationMs / 1000).toInt() * 2 // 立體聲
            
            val origAudio = ShortArray(sampleCount) { i ->
                // 生成 440Hz 正弦波
                (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / (sampleRate * 2)) * 8000).toInt().toShort()
            }
            
            val bgmAudio = ShortArray(sampleCount) { i ->
                // 生成 880Hz 正弦波
                (kotlin.math.sin(2 * kotlin.math.PI * 880 * i / (sampleRate * 2)) * 4000).toInt().toShort()
            }
            
            // 執行混音
            val mixedAudio = AudioMixUtils.mixPcmWithVolume(origAudio, bgmAudio, 1.0f, 0.5f)
            
            // 檢查混音結果
            val origStats = AudioMixUtils.getAudioStats(origAudio)
            val bgmStats = AudioMixUtils.getAudioStats(bgmAudio)
            val mixedStats = AudioMixUtils.getAudioStats(mixedAudio)
            
            if (mixedStats.rms > origStats.rms * 0.5 && mixedStats.rms < origStats.rms * 1.5) {
                TestResult(
                    testName = "PCM 混音測試",
                    success = true,
                    message = "混音成功: 原聲 RMS=${origStats.rms}, BGM RMS=${bgmStats.rms}, 混音 RMS=${mixedStats.rms}",
                    duration = System.currentTimeMillis() - startTime
                )
            } else {
                TestResult(
                    testName = "PCM 混音測試",
                    success = false,
                    message = "混音結果異常: 混音 RMS=${mixedStats.rms}",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName = "PCM 混音測試",
                success = false,
                message = "混音時發生錯誤: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 測試簡單 BGM 混音
     */
    private fun testSimpleBgmMixing(videoPath: String, bgmPath: String, outputDir: String): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val outputPath = File(outputDir, "test_bgm_mix_${System.currentTimeMillis()}.mp4").absolutePath
            
            // 使用 SimpleBgmMixer 進行混音
            SimpleBgmMixer.mixVideoWithBgm(
                context = context,
                inputVideoPath = videoPath,
                inputBgmPath = bgmPath,
                outputPath = outputPath,
                config = com.example.videoeditor.engine.BgmMixConfig(
                    bgmVolume = 0.5f,
                    loopBgm = true
                )
            )
            
            TestResult(
                testName = "簡單 BGM 混音測試",
                success = true,
                message = "BGM 混音成功",
                duration = System.currentTimeMillis() - startTime,
                outputPath = outputPath
            )
        } catch (e: Exception) {
            TestResult(
                testName = "簡單 BGM 混音測試",
                success = false,
                message = "BGM 混音失敗: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 測試輸出檔案
     */
    private fun testOutputFile(outputPath: String): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val outputFile = File(outputPath)
            
            if (outputFile.exists() && outputFile.length() > 0) {
                val fileSizeMB = outputFile.length() / (1024 * 1024)
                TestResult(
                    testName = "輸出檔案驗證",
                    success = true,
                    message = "輸出檔案有效: ${fileSizeMB}MB",
                    duration = System.currentTimeMillis() - startTime,
                    outputPath = outputPath
                )
            } else {
                TestResult(
                    testName = "輸出檔案驗證",
                    success = false,
                    message = "輸出檔案無效或為空",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName = "輸出檔案驗證",
                success = false,
                message = "驗證輸出檔案時發生錯誤: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 測試音訊品質
     */
    private fun testAudioQuality(outputPath: String): TestResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val pcmData = AudioMixUtils.decodeAudioToPcm(outputPath)
            
            if (pcmData != null && pcmData.isNotEmpty()) {
                val stats = AudioMixUtils.getAudioStats(pcmData)
                
                val qualityMessage = when {
                    stats.isSilent -> "輸出檔案為靜音"
                    stats.rms < 100 -> "音量過低"
                    stats.rms > 15000 -> "音量過高"
                    else -> "音訊品質正常"
                }
                
                val isGoodQuality = !stats.isSilent && stats.rms in 100.0..15000.0
                
                TestResult(
                    testName = "音訊品質檢查",
                    success = isGoodQuality,
                    message = "$qualityMessage (RMS: ${stats.rms}, 峰值: ${stats.peak})",
                    duration = System.currentTimeMillis() - startTime
                )
            } else {
                TestResult(
                    testName = "音訊品質檢查",
                    success = false,
                    message = "無法解碼輸出檔案",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            TestResult(
                testName = "音訊品質檢查",
                success = false,
                message = "檢查音訊品質時發生錯誤: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
    
    /**
     * 生成測試報告
     */
    fun generateTestReport(testSuite: TestSuite): String {
        val report = StringBuilder()
        report.appendLine("=== 音訊管道測試報告 ===")
        report.appendLine("測試套件: ${testSuite.name}")
        report.appendLine("總測試數: ${testSuite.tests.size}")
        report.appendLine("成功數: ${testSuite.successCount}")
        report.appendLine("失敗數: ${testSuite.failureCount}")
        report.appendLine("成功率: ${String.format("%.1f", testSuite.successRate * 100)}%")
        report.appendLine("總耗時: ${testSuite.totalDuration}ms")
        report.appendLine()
        
        report.appendLine("=== 詳細測試結果 ===")
        testSuite.tests.forEach { result ->
            val status = if (result.success) "✅" else "❌"
            report.appendLine("$status ${result.testName}")
            report.appendLine("   訊息: ${result.message}")
            report.appendLine("   耗時: ${result.duration}ms")
            if (result.outputPath != null) {
                report.appendLine("   輸出: ${result.outputPath}")
            }
            report.appendLine()
        }
        
        return report.toString()
    }

    companion object {
        /** 檢查 MP4 是否至少有一個 audio track */
        fun hasAudioTrack(path: String): Boolean {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    extractor.release()
                    return true
                }
            }
            extractor.release()
            return false
        }

        /** 計算輸出檔案的 RMS，避免靜音 */
        fun calcAudioRms(path: String, maxFrames: Int = 5000): Double {
            val extractor = MediaExtractor()
            extractor.setDataSource(path)

            var audioTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i
                    break
                }
            }
            if (audioTrack < 0) return 0.0

            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val samples = ArrayList<Short>()
            var frames = 0

            loop@ while (frames < maxFrames) {
                val inIndex = codec.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                        extractor.advance()
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)!!
                    val shorts = ShortArray(bufferInfo.size / 2)
                    outBuf.asShortBuffer().get(shorts)
                    samples.addAll(shorts.toList())
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)

                    frames++
                    if (frames >= maxFrames) break@loop
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }

            codec.stop()
            codec.release()
            extractor.release()

            // 計算 RMS
            if (samples.isEmpty()) return 0.0
            val energy = samples.map { it.toDouble() * it.toDouble() }.sum()
            return sqrt(energy / samples.size)
        }

        /** 檢查是否有足夠音量（非靜音） */
        fun hasNonSilentAudio(path: String, rmsThreshold: Double = 500.0): Boolean {
            val rms = calcAudioRms(path)
            return rms > rmsThreshold
        }

        /** 輸出測試報表到檔案 (cacheDir) */
        fun exportAudioReport(context: android.content.Context, path: String, rmsThreshold: Double = 500.0): File {
            val reportFile = File(context.cacheDir, "audio_mix_report.txt")
            val hasAudio = hasAudioTrack(path)
            val rms = calcAudioRms(path)
            val nonSilent = rms > rmsThreshold

            reportFile.writeText(buildString {
                appendLine("=== Audio Mix Test Report ===")
                appendLine("File: $path")
                appendLine("Has Audio Track: $hasAudio")
                appendLine("RMS Value: $rms")
                appendLine("Threshold: $rmsThreshold")
                appendLine("Non Silent: $nonSilent")
            })

            return reportFile
        }

        /** 比較原始與輸出音訊 RMS，輸出 dB 差異 */
        fun compareAudioRms(
            context: android.content.Context,
            originalPath: String,
            mixedPath: String,
            rmsThreshold: Double = 500.0
        ): File {
            val originalRms = calcAudioRms(originalPath)
            val mixedRms = calcAudioRms(mixedPath)

            // 避免除以零
            val diffDb = if (originalRms > 0) {
                20 * kotlin.math.log10(mixedRms / originalRms)
            } else {
                Double.NaN
            }

            val reportFile = File(context.cacheDir, "audio_rms_comparison.txt")
            reportFile.writeText(buildString {
                appendLine("=== Audio RMS Comparison Report ===")
                appendLine("Original: $originalPath")
                appendLine("Mixed: $mixedPath")
                appendLine("Original RMS: $originalRms")
                appendLine("Mixed RMS: $mixedRms")
                appendLine("Difference (dB): $diffDb")
                appendLine("Mixed > Threshold($rmsThreshold): ${mixedRms > rmsThreshold}")
            })

            return reportFile
        }
    }
}
