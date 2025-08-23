package com.example.test

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.videoeditor.utils.LogDisplayManager
import java.io.File

/**
 * 測試執行器
 * 提供簡單的介面來運行音訊測試
 */
class TestRunner(private val context: Context) {
    
    private val TAG = "TestRunner"
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 測試回調介面
     */
    interface TestCallback {
        fun onTestStarted(testName: String)
        fun onTestCompleted(testName: String, success: Boolean, message: String)
        fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite)
        fun onError(error: String)
    }
    
    /**
     * 執行完整測試套件
     */
    fun runFullTestSuite(
        videoPath: String? = null,
        bgmPath: String? = null,
        callback: TestCallback? = null
    ) {
        Thread {
            try {
                // 獲取測試檔案路徑
                val testVideoPath = videoPath ?: getDefaultTestVideoPath()
                val testBgmPath = bgmPath ?: getDefaultTestBgmPath()
                
                // 創建測試輸出目錄
                val testOutputDir = File(context.filesDir, "test_output").absolutePath
                File(testOutputDir).mkdirs()
                
                LogDisplayManager.addLog("I", TAG, "開始執行完整測試套件")
                callback?.onTestStarted("完整音訊管道測試")
                
                // 創建測試器並執行測試
                val tester = AudioPipelineTester(context)
                val testSuite = tester.runFullPipelineTest(testVideoPath, testBgmPath, testOutputDir)
                
                // 在主線程中回調結果
                mainHandler.post {
                    callback?.onTestSuiteCompleted(testSuite)
                    
                    // 記錄詳細結果
                    val report = tester.generateTestReport(testSuite)
                    LogDisplayManager.addLog("I", TAG, "測試報告:\n$report")
                    
                    LogDisplayManager.addLog("I", TAG, 
                        "測試完成: ${testSuite.successCount}/${testSuite.tests.size} 成功, " +
                        "成功率: ${String.format("%.1f", testSuite.successRate * 100)}%")
                }
                
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", TAG, "測試執行失敗: ${e.message}")
                mainHandler.post {
                    callback?.onError("測試執行失敗: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * 執行單個測試
     */
    fun runSingleTest(
        testName: String,
        callback: TestCallback? = null
    ) {
        Thread {
            try {
                callback?.onTestStarted(testName)
                
                when (testName) {
                    "音訊解碼測試" -> runAudioDecodingTest(callback)
                    "PCM 混音測試" -> runPcmMixingTest(callback)
                    "音訊編碼測試" -> runAudioEncodingTest(callback)
                    "音訊品質分析測試" -> runAudioQualityTest(callback)
                    "錯誤處理測試" -> runErrorHandlingTest(callback)
                    else -> {
                        mainHandler.post {
                            callback?.onError("未知的測試: $testName")
                        }
                    }
                }
                
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", TAG, "單個測試執行失敗: ${e.message}")
                mainHandler.post {
                    callback?.onError("測試執行失敗: ${e.message}")
                }
            }
        }.start()
    }
    
    /**
     * 執行音訊解碼測試
     */
    private fun runAudioDecodingTest(callback: TestCallback?) {
        try {
            val bgmPath = getDefaultTestBgmPath()
            val pcmData = AudioMixUtils.decodeAudioToPcm(bgmPath)
            
            if (pcmData != null && pcmData.isNotEmpty()) {
                val stats = AudioMixUtils.getAudioStats(pcmData)
                val message = "解碼成功: ${stats.sampleCount} 樣本, ${stats.durationMs}ms, RMS: ${stats.rms}"
                
                mainHandler.post {
                    callback?.onTestCompleted("音訊解碼測試", true, message)
                }
            } else {
                mainHandler.post {
                    callback?.onTestCompleted("音訊解碼測試", false, "解碼失敗: 沒有 PCM 數據")
                }
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback?.onTestCompleted("音訊解碼測試", false, "解碼時發生錯誤: ${e.message}")
            }
        }
    }
    
    /**
     * 執行 PCM 混音測試
     */
    private fun runPcmMixingTest(callback: TestCallback?) {
        try {
            // 創建測試音訊數據
            val sampleRate = 48000
            val durationMs = 1000L
            val sampleCount = (sampleRate * durationMs / 1000).toInt() * 2
            
            val origAudio = ShortArray(sampleCount) { i ->
                (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / (sampleRate * 2)) * 8000).toInt().toShort()
            }
            
            val bgmAudio = ShortArray(sampleCount) { i ->
                (kotlin.math.sin(2 * kotlin.math.PI * 880 * i / (sampleRate * 2)) * 4000).toInt().toShort()
            }
            
            // 執行混音
            val mixedAudio = AudioMixUtils.mixPcmWithVolume(origAudio, bgmAudio, 1.0f, 0.5f)
            
            // 檢查混音結果
            val origStats = AudioMixUtils.getAudioStats(origAudio)
            val bgmStats = AudioMixUtils.getAudioStats(bgmAudio)
            val mixedStats = AudioMixUtils.getAudioStats(mixedAudio)
            
            val success = mixedStats.rms > origStats.rms * 0.5 && mixedStats.rms < origStats.rms * 1.5
            val message = "混音結果: 原聲 RMS=${origStats.rms}, BGM RMS=${bgmStats.rms}, 混音 RMS=${mixedStats.rms}"
            
            mainHandler.post {
                callback?.onTestCompleted("PCM 混音測試", success, message)
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback?.onTestCompleted("PCM 混音測試", false, "混音時發生錯誤: ${e.message}")
            }
        }
    }
    
    /**
     * 執行音訊編碼測試
     */
    private fun runAudioEncodingTest(callback: TestCallback?) {
        try {
            // 創建測試音訊數據
            val sampleRate = 48000
            val durationMs = 2000L
            val sampleCount = (sampleRate * durationMs / 1000).toInt() * 2
            
            val testAudio = ShortArray(sampleCount) { i ->
                val freq1 = 440.0
                val freq2 = 880.0
                val wave1 = kotlin.math.sin(2 * kotlin.math.PI * freq1 * i / (sampleRate * 2))
                val wave2 = kotlin.math.sin(2 * kotlin.math.PI * freq2 * i / (sampleRate * 2))
                ((wave1 + wave2) * 4000).toInt().toShort()
            }
            
            // 編碼為 AAC 檔案
            val outputPath = File(context.filesDir, "test_encoded_${System.currentTimeMillis()}.aac").absolutePath
            val success = AudioMixUtils.encodePcmToAac(testAudio, outputPath, sampleRate, 2)
            
            if (success) {
                val outputFile = File(outputPath)
                val message = "編碼成功: ${outputFile.length()} bytes"
                mainHandler.post {
                    callback?.onTestCompleted("音訊編碼測試", true, message)
                }
            } else {
                mainHandler.post {
                    callback?.onTestCompleted("音訊編碼測試", false, "編碼失敗")
                }
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback?.onTestCompleted("音訊編碼測試", false, "編碼時發生錯誤: ${e.message}")
            }
        }
    }
    
    /**
     * 執行音訊品質分析測試
     */
    private fun runAudioQualityTest(callback: TestCallback?) {
        try {
            val sampleRate = 48000
            val durationMs = 1000L
            val sampleCount = (sampleRate * durationMs / 1000).toInt() * 2
            
            // 正常音訊
            val normalAudio = ShortArray(sampleCount) { i ->
                (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / (sampleRate * 2)) * 8000).toInt().toShort()
            }
            
            // 靜音音訊
            val silentAudio = ShortArray(sampleCount) { 0 }
            
            // 過大音量音訊
            val loudAudio = ShortArray(sampleCount) { i ->
                (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / (sampleRate * 2)) * 30000).toInt().toShort()
            }
            
            // 分析音訊品質
            val normalStats = AudioMixUtils.getAudioStats(normalAudio)
            val silentStats = AudioMixUtils.getAudioStats(silentAudio)
            val loudStats = AudioMixUtils.getAudioStats(loudAudio)
            
            val success = !normalStats.isSilent && silentStats.isSilent && !loudStats.isSilent
            val message = "品質分析: 正常=${normalStats.rms}, 靜音=${silentStats.rms}, 過大=${loudStats.rms}"
            
            mainHandler.post {
                callback?.onTestCompleted("音訊品質分析測試", success, message)
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback?.onTestCompleted("音訊品質分析測試", false, "品質分析時發生錯誤: ${e.message}")
            }
        }
    }
    
    /**
     * 執行錯誤處理測試
     */
    private fun runErrorHandlingTest(callback: TestCallback?) {
        try {
            // 測試不存在的檔案
            val nonExistentPath = "/path/to/nonexistent/file.mp3"
            val pcmData = AudioMixUtils.decodeAudioToPcm(nonExistentPath)
            
            // 測試空音訊數據
            val emptyAudio = ShortArray(0)
            val emptyStats = AudioMixUtils.getAudioStats(emptyAudio)
            
            val success = pcmData == null && emptyStats.sampleCount == 0
            val message = "錯誤處理: 不存在檔案=${pcmData == null}, 空音訊樣本數=${emptyStats.sampleCount}"
            
            mainHandler.post {
                callback?.onTestCompleted("錯誤處理測試", success, message)
            }
        } catch (e: Exception) {
            mainHandler.post {
                callback?.onTestCompleted("錯誤處理測試", false, "錯誤處理測試時發生錯誤: ${e.message}")
            }
        }
    }
    
    /**
     * 獲取預設測試影片路徑
     */
    private fun getDefaultTestVideoPath(): String {
        // 嘗試從 raw 資源獲取
        val rawVideoPath = copyRawResourceToFile("sample_video.mp4")
        if (rawVideoPath != null) {
            return rawVideoPath
        }
        
        // 如果沒有 raw 資源，創建一個簡單的測試影片
        return createTestVideoFile()
    }
    
    /**
     * 獲取預設測試 BGM 路徑
     */
    private fun getDefaultTestBgmPath(): String {
        // 嘗試從 raw 資源獲取
        val rawBgmPath = copyRawResourceToFile("sample_bgm.mp3")
        if (rawBgmPath != null) {
            return rawBgmPath
        }
        
        // 如果沒有 raw 資源，創建一個簡單的測試音訊
        return createTestAudioFile()
    }
    
    /**
     * 從 raw 資源複製檔案
     */
    private fun copyRawResourceToFile(resourceName: String): String? {
        return try {
            val resourceId = context.resources.getIdentifier(
                resourceName.substringBeforeLast("."), 
                "raw", 
                context.packageName
            )
            
            if (resourceId == 0) {
                LogDisplayManager.addLog("W", TAG, "找不到 raw 資源: $resourceName")
                return null
            }
            
            val inputStream = context.resources.openRawResource(resourceId)
            val outputFile = File(context.filesDir, resourceName)
            
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            LogDisplayManager.addLog("I", TAG, "成功複製 raw 資源: $resourceName")
            outputFile.absolutePath
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", TAG, "複製 raw 資源失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 創建測試影片檔案
     */
    private fun createTestVideoFile(): String {
        val testVideoPath = File(context.filesDir, "test_video.mp4").absolutePath
        File(testVideoPath).createNewFile()
        LogDisplayManager.addLog("W", TAG, "創建了空的測試影片檔案")
        return testVideoPath
    }
    
    /**
     * 創建測試音訊檔案
     */
    private fun createTestAudioFile(): String {
        val testAudioPath = File(context.filesDir, "test_bgm.mp3").absolutePath
        
        // 創建測試音訊數據並編碼為 AAC
        val sampleRate = 48000
        val durationMs = 3000L
        val sampleCount = (sampleRate * durationMs / 1000).toInt() * 2
        
        val testAudio = ShortArray(sampleCount) { i ->
            (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / (sampleRate * 2)) * 8000).toInt().toShort()
        }
        
        val success = AudioMixUtils.encodePcmToAac(testAudio, testAudioPath, sampleRate, 2)
        
        if (success) {
            LogDisplayManager.addLog("I", TAG, "成功創建測試音訊檔案")
        } else {
            LogDisplayManager.addLog("W", TAG, "創建測試音訊檔案失敗")
        }
        
        return testAudioPath
    }
}
