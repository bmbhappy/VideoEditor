package com.example.test

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 測試系統使用範例
 * 展示如何在實際應用中使用音訊測試功能
 */
object TestExample {
    
    private const val TAG = "TestExample"
    
    /**
     * 範例 1: 執行完整測試套件
     */
    fun runFullTestExample(context: Context) {
        val testRunner = TestRunner(context)
        
        testRunner.runFullTestSuite(
            callback = object : TestRunner.TestCallback {
                override fun onTestStarted(testName: String) {
                    Log.i(TAG, "🚀 開始測試: $testName")
                }
                
                override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                    val status = if (success) "✅ 成功" else "❌ 失敗"
                    Log.i(TAG, "$status $testName: $message")
                }
                
                override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                    Log.i(TAG, "📊 測試套件完成:")
                    Log.i(TAG, "   成功率: ${String.format("%.1f", testSuite.successRate * 100)}%")
                    Log.i(TAG, "   成功: ${testSuite.successCount}/${testSuite.tests.size}")
                    Log.i(TAG, "   總耗時: ${testSuite.totalDuration}ms")
                    
                    // 如果成功率低於 80%，記錄警告
                    if (testSuite.successRate < 0.8) {
                        Log.w(TAG, "⚠️ 測試成功率偏低，建議檢查音訊處理邏輯")
                    }
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "💥 測試錯誤: $error")
                }
            }
        )
    }
    
    /**
     * 範例 2: 執行單個測試
     */
    fun runSingleTestExample(context: Context) {
        val testRunner = TestRunner(context)
        
        // 執行 PCM 混音測試
        testRunner.runSingleTest(
            testName = "PCM 混音測試",
            callback = object : TestRunner.TestCallback {
                override fun onTestStarted(testName: String) {
                    Log.i(TAG, "開始單個測試: $testName")
                }
                
                override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                    if (success) {
                        Log.i(TAG, "✅ $testName 通過: $message")
                    } else {
                        Log.e(TAG, "❌ $testName 失敗: $message")
                    }
                }
                
                override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                    // 單個測試不會觸發此回調
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "測試出錯: $error")
                }
            }
        )
    }
    
    /**
     * 範例 3: 自定義音訊測試
     */
    fun customAudioTestExample(context: Context, videoPath: String, bgmPath: String) {
        val testRunner = TestRunner(context)
        
        // 使用指定的檔案執行測試
        testRunner.runFullTestSuite(
            videoPath = videoPath,
            bgmPath = bgmPath,
            callback = object : TestRunner.TestCallback {
                override fun onTestStarted(testName: String) {
                    Log.i(TAG, "自定義測試開始: $testName")
                }
                
                override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                    Log.i(TAG, "自定義測試結果: $testName - $success")
                    Log.d(TAG, "詳細訊息: $message")
                }
                
                override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                    // 生成詳細報告
                    val tester = AudioPipelineTester(context)
                    val report = tester.generateTestReport(testSuite)
                    Log.i(TAG, "自定義測試報告:\n$report")
                    
                    // 檢查特定測試結果
                    val bgmMixTest = testSuite.tests.find { it.testName == "簡單 BGM 混音測試" }
                    if (bgmMixTest?.success == true) {
                        Log.i(TAG, "✅ BGM 混音功能正常")
                    } else {
                        Log.w(TAG, "⚠️ BGM 混音可能有問題")
                    }
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "自定義測試錯誤: $error")
                }
            }
        )
    }
    
    /**
     * 範例 4: 直接使用音訊工具進行測試
     */
    fun directAudioToolExample(context: Context) {
        try {
            // 創建測試音訊數據
            val sampleRate = 48000
            val durationMs = 1000L
            val sampleCount = (sampleRate * durationMs / 1000).toInt() * 2 // 立體聲
            
            // 生成 440Hz 正弦波
            val origAudio = ShortArray(sampleCount) { i ->
                (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / (sampleRate * 2)) * 8000).toInt().toShort()
            }
            
            // 生成 880Hz 正弦波作為 BGM
            val bgmAudio = ShortArray(sampleCount) { i ->
                (kotlin.math.sin(2 * kotlin.math.PI * 880 * i / (sampleRate * 2)) * 4000).toInt().toShort()
            }
            
            Log.i(TAG, "🎵 開始直接音訊工具測試")
            
            // 執行混音
            val mixedAudio = AudioMixUtils.mixPcmWithVolume(origAudio, bgmAudio, 1.0f, 0.5f)
            
            // 分析音訊統計
            val origStats = AudioMixUtils.getAudioStats(origAudio)
            val bgmStats = AudioMixUtils.getAudioStats(bgmAudio)
            val mixedStats = AudioMixUtils.getAudioStats(mixedAudio)
            
            Log.i(TAG, "📊 音訊統計:")
            Log.i(TAG, "   原聲 - 樣本數: ${origStats.sampleCount}, RMS: ${origStats.rms}")
            Log.i(TAG, "   BGM - 樣本數: ${bgmStats.sampleCount}, RMS: ${bgmStats.rms}")
            Log.i(TAG, "   混音 - 樣本數: ${mixedStats.sampleCount}, RMS: ${mixedStats.rms}")
            
            // 檢查混音結果
            if (mixedStats.rms > origStats.rms * 0.5 && mixedStats.rms < origStats.rms * 1.5) {
                Log.i(TAG, "✅ 混音結果正常")
            } else {
                Log.w(TAG, "⚠️ 混音結果可能異常")
            }
            
            // 編碼測試
            val outputPath = context.cacheDir.absolutePath + "/test_mixed_audio.aac"
            val encodeSuccess = AudioMixUtils.encodePcmToAac(mixedAudio, outputPath, sampleRate, 2)
            
            if (encodeSuccess) {
                Log.i(TAG, "✅ 音訊編碼成功: $outputPath")
                
                // 驗證編碼後的檔案
                val decodedPcm = AudioMixUtils.decodeAudioToPcm(outputPath)
                if (decodedPcm != null && decodedPcm.isNotEmpty()) {
                    Log.i(TAG, "✅ 編碼檔案可正常解碼")
                } else {
                    Log.e(TAG, "❌ 編碼檔案解碼失敗")
                }
            } else {
                Log.e(TAG, "❌ 音訊編碼失敗")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "直接音訊工具測試失敗: ${e.message}", e)
        }
    }
    
    /**
     * 範例 5: 定期健康檢查
     */
    fun audioHealthCheck(context: Context) {
        Log.i(TAG, "🏥 開始音訊系統健康檢查")
        
        val testRunner = TestRunner(context)
        testRunner.runFullTestSuite(
            callback = object : TestRunner.TestCallback {
                override fun onTestStarted(testName: String) {
                    // 健康檢查通常不需要詳細日誌
                }
                
                override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                    if (!success) {
                        Log.w(TAG, "健康檢查發現問題: $testName - $message")
                    }
                }
                
                override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                    when {
                        testSuite.successRate >= 0.9 -> {
                            Log.i(TAG, "💚 音訊系統健康狀況: 優秀 (${String.format("%.1f", testSuite.successRate * 100)}%)")
                        }
                        testSuite.successRate >= 0.8 -> {
                            Log.i(TAG, "💛 音訊系統健康狀況: 良好 (${String.format("%.1f", testSuite.successRate * 100)}%)")
                        }
                        testSuite.successRate >= 0.6 -> {
                            Log.w(TAG, "🧡 音訊系統健康狀況: 需要關注 (${String.format("%.1f", testSuite.successRate * 100)}%)")
                        }
                        else -> {
                            Log.e(TAG, "❤️ 音訊系統健康狀況: 需要立即修復 (${String.format("%.1f", testSuite.successRate * 100)}%)")
                        }
                    }
                    
                    // 記錄失敗的測試
                    val failedTests = testSuite.tests.filter { !it.success }
                    if (failedTests.isNotEmpty()) {
                        Log.w(TAG, "失敗的測試項目:")
                        failedTests.forEach { test ->
                            Log.w(TAG, "  - ${test.testName}: ${test.message}")
                        }
                    }
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "健康檢查執行錯誤: $error")
                }
            }
        )
    }
    
    /**
     * 範例 6: 效能基準測試
     */
    fun performanceBenchmark(context: Context) {
        Log.i(TAG, "⚡ 開始音訊處理效能基準測試")
        
        val iterations = 5
        val totalTimes = mutableListOf<Long>()
        
        repeat(iterations) { iteration ->
            Log.i(TAG, "執行第 ${iteration + 1} 輪基準測試")
            
            val testRunner = TestRunner(context)
            testRunner.runFullTestSuite(
                callback = object : TestRunner.TestCallback {
                    override fun onTestStarted(testName: String) {}
                    override fun onTestCompleted(testName: String, success: Boolean, message: String) {}
                    
                    override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                        totalTimes.add(testSuite.totalDuration)
                        Log.i(TAG, "第 ${iteration + 1} 輪耗時: ${testSuite.totalDuration}ms")
                        
                        if (iteration == iterations - 1) {
                            // 計算統計數據
                            val avgTime = totalTimes.average()
                            val minTime = totalTimes.minOrNull() ?: 0L
                            val maxTime = totalTimes.maxOrNull() ?: 0L
                            
                            Log.i(TAG, "📈 效能基準測試結果:")
                            Log.i(TAG, "   平均耗時: ${String.format("%.1f", avgTime)}ms")
                            Log.i(TAG, "   最短耗時: ${minTime}ms")
                            Log.i(TAG, "   最長耗時: ${maxTime}ms")
                            Log.i(TAG, "   測試輪數: $iterations")
                            
                            // 效能評估
                            when {
                                avgTime < 2000 -> Log.i(TAG, "🚀 效能評級: 優秀")
                                avgTime < 5000 -> Log.i(TAG, "✅ 效能評級: 良好")
                                avgTime < 10000 -> Log.i(TAG, "⚠️ 效能評級: 需要優化")
                                else -> Log.w(TAG, "🐌 效能評級: 性能不佳")
                            }
                        }
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "基準測試錯誤: $error")
                    }
                }
            )
        }
    }
    
    /**
     * 範例 7: BGM 功能驗證測試
     */
    @JvmStatic
    fun bgmFunctionalityTest(context: Context, callback: TestRunner.TestCallback? = null) {
        val testCallback = callback ?: object : TestRunner.TestCallback {
            override fun onTestStarted(testName: String) {
                Log.i(TAG, "🎵 開始BGM功能測試: $testName")
            }
            
            override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                val status = if (success) "✅" else "❌"
                Log.i(TAG, "$status BGM測試: $testName - $message")
            }
            
            override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                Log.i(TAG, "🎵 BGM功能測試完成")
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "❌ BGM測試錯誤: $error")
            }
        }
        
        Thread {
            try {
                Log.i(TAG, "🔄 開始 BGM 功能測試...")
                testCallback.onTestStarted("BGM功能驗證測試")
                
                // 檢查測試資源是否存在
                val testVideoPath = createDummyTestVideoPath(context)
                val testBgmPath = createDummyTestBgmPath(context)
                
                if (testVideoPath == null || testBgmPath == null) {
                    testCallback.onError("測試資源檔案不存在")
                    return@Thread
                }
                
                Log.i(TAG, "🔄 檢查 SimpleBgmMixer 可用性...")
                
                // 使用 SimpleBgmMixer 測試實際 BGM 功能
                val outputFile = java.io.File(context.cacheDir, "bgm_test_output_${System.currentTimeMillis()}.mp4")
                
                val config = com.example.videoeditor.engine.BgmMixConfig(
                    mainVolume = 0.8f,
                    bgmVolume = 0.3f,
                    loopBgm = true,
                    outSampleRate = 44100,
                    outChannelCount = 2
                )
                
                Log.i(TAG, "🔄 執行 BGM 混音...")
                
                try {
                    com.example.videoeditor.engine.SimpleBgmMixer.mixVideoWithBgm(
                        context = context,
                        inputVideoPath = testVideoPath,
                        inputBgmPath = testBgmPath,
                        outputPath = outputFile.absolutePath,
                        config = config
                    )
                    
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Log.i(TAG, "🔄 BGM 混音成功！檔案大小: ${outputFile.length()} bytes")
                        
                        // 驗證輸出檔案品質
                        val hasAudio = AudioPipelineTester.hasAudioTrack(outputFile.absolutePath)
                        val isNonSilent = AudioPipelineTester.hasNonSilentAudio(outputFile.absolutePath)
                        
                        if (hasAudio && isNonSilent) {
                            testCallback.onTestCompleted("BGM功能驗證測試", true, "BGM 功能測試成功！音訊品質良好。")
                        } else {
                            testCallback.onTestCompleted("BGM功能驗證測試", false, "輸出音訊品質不佳")
                        }
                    } else {
                        testCallback.onTestCompleted("BGM功能驗證測試", false, "沒有生成輸出檔案")
                    }
                    
                } catch (e: Exception) {
                    testCallback.onTestCompleted("BGM功能驗證測試", false, "BGM 混音過程失敗: ${e.message}")
                }
                
            } catch (e: Exception) {
                testCallback.onError("BGM 功能測試異常: ${e.message}")
            }
        }.start()
    }
    
    // 創建測試用的影片檔案路徑
    private fun createDummyTestVideoPath(context: Context): String? {
        return try {
            // 嘗試從主要資源獲取測試影片
            val videoFile = File(context.cacheDir, "test_video.mp4")
            if (!videoFile.exists()) {
                // 如果沒有測試影片，複製 raw 資源
                try {
                    context.resources.openRawResource(com.example.videoeditor.R.raw.sample_video).use { input ->
                        videoFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "無法複製測試影片: ${e.message}")
                    return null
                }
            }
            videoFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "創建測試影片路徑失敗: ${e.message}")
            null
        }
    }
    
    // 創建測試用的BGM檔案路徑
    private fun createDummyTestBgmPath(context: Context): String? {
        return try {
            // 嘗試從主要資源獲取測試BGM
            val bgmFile = File(context.cacheDir, "test_bgm.mp3")
            if (!bgmFile.exists()) {
                // 如果沒有測試BGM，複製 raw 資源
                try {
                    context.resources.openRawResource(com.example.videoeditor.R.raw.sample_bgm).use { input ->
                        bgmFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "無法複製測試BGM: ${e.message}")
                    return null
                }
            }
            bgmFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "創建測試BGM路徑失敗: ${e.message}")
            null
        }
    }
}
