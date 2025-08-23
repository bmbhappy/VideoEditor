# 🎵 音訊測試系統使用指南

您已經成功整合了一個專業的音訊測試系統！這個系統提供了完整的音訊處理測試功能，包括 PCM 混音、格式轉換和品質分析。

## 📁 系統架構

### 測試檔案結構
```
app/src/main/java/com/example/test/
├── AudioMixUtils.kt           # 測試專用音訊工具
├── AudioPipelineTester.kt     # 完整測試套件
└── TestRunner.kt              # 測試執行器

app/src/androidTest/java/com/example/test/
└── AudioPipelineFullMixTest.kt # Instrumentation 測試

app/src/androidTest/res/raw/
├── sample_video.mp4           # 測試影片（需要添加）
└── sample_bgm.mp3            # 測試音訊（需要添加）
```

## 🚀 快速開始

### 1. 添加測試資源檔案
將測試檔案放入 `app/src/androidTest/res/raw/` 目錄：
- `sample_video.mp4` - 5-10秒的測試影片（MP4, H.264+AAC）
- `sample_bgm.mp3` - 10-30秒的測試音訊（MP3, 128kbps+）

### 2. 在應用程式中使用測試系統

```kotlin
// 創建測試執行器
val testRunner = TestRunner(this) // this = Context

// 執行完整測試套件
testRunner.runFullTestSuite(
    callback = object : TestRunner.TestCallback {
        override fun onTestStarted(testName: String) {
            println("開始測試: $testName")
        }
        
        override fun onTestCompleted(testName: String, success: Boolean, message: String) {
            println("測試完成: $testName - ${if (success) "✅成功" else "❌失敗"}")
            println("詳細訊息: $message")
        }
        
        override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
            println("=== 測試套件完成 ===")
            println("成功率: ${String.format("%.1f", testSuite.successRate * 100)}%")
            println("成功: ${testSuite.successCount}/${testSuite.tests.size}")
            println("總耗時: ${testSuite.totalDuration}ms")
        }
        
        override fun onError(error: String) {
            println("測試錯誤: $error")
        }
    }
)
```

### 3. 執行單個測試

```kotlin
val testRunner = TestRunner(this)

// 執行 PCM 混音測試
testRunner.runSingleTest("PCM 混音測試", callback)

// 執行音訊解碼測試
testRunner.runSingleTest("音訊解碼測試", callback)

// 執行音訊編碼測試
testRunner.runSingleTest("音訊編碼測試", callback)

// 執行音訊品質分析測試
testRunner.runSingleTest("音訊品質分析測試", callback)

// 執行錯誤處理測試
testRunner.runSingleTest("錯誤處理測試", callback)
```

## 🧪 Instrumentation 測試

### 執行 Instrumentation 測試

```bash
# 連接設備並執行所有測試
./gradlew connectedAndroidTest

# 執行特定測試類
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.test.AudioPipelineFullMixTest

# 執行特定測試方法
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.test.AudioPipelineFullMixTest#testVideoWithBgmMixPipeline
```

### 測試報告位置
Instrumentation 測試會在設備的 cache 目錄生成報告：
- `audio_mix_report.txt` - 基本音訊測試報告
- `audio_rms_comparison.txt` - RMS 比較報告

## 📊 測試功能詳解

### AudioMixUtils - 音訊工具類
- **`mixPcm(orig, bgm)`** - 簡單混音（原聲=1.0, BGM=0.5）
- **`mixPcmWithVolume(orig, bgm, origVol, bgmVol)`** - 可調音量混音
- **`decodeAudioToPcm(path)`** - 音訊解碼為 PCM
- **`encodePcmToAac(pcm, path)`** - PCM 編碼為 AAC
- **`getAudioStats(pcm)`** - 音訊統計分析
- **`calculateRms(pcm)`** - 計算 RMS 值
- **`isSilent(pcm)`** - 檢測靜音

### AudioPipelineTester - 測試器
- **檔案存在性檢查** - 驗證測試檔案
- **音訊格式檢查** - 驗證格式支援
- **音訊解碼測試** - 測試解碼功能
- **PCM 混音測試** - 測試混音邏輯
- **BGM 混音測試** - 與 SimpleBgmMixer 整合測試
- **輸出檔案驗證** - 檢查輸出檔案
- **音訊品質檢查** - RMS/峰值分析

### AudioPipelineFullMixTest - 完整管道測試
- **完整影片+BGM混音流程**
  1. 提取影片的 video/audio track
  2. 解碼原音軌和 BGM 成 PCM
  3. 執行 PCM 混音
  4. 重新編碼 PCM → AAC
  5. Mux video + AAC 成新的 MP4
  6. 驗證輸出檔案有音訊且非靜音
  7. 生成測試報告
  8. 比較原始與輸出的 RMS 差異

## 📈 測試報告範例

### 基本測試報告
```
=== Audio Mix Test Report ===
File: /data/user/0/com.example.test/cache/pipeline_mix_test.mp4
Has Audio Track: true
RMS Value: 1432.57
Threshold: 500.0
Non Silent: true
```

### RMS 比較報告
```
=== Audio RMS Comparison Report ===
Original: /data/user/0/com.example.test/cache/sample_video.mp4
Mixed: /data/user/0/com.example.test/cache/pipeline_mix_test.mp4
Original RMS: 1850.33
Mixed RMS: 1325.71
Difference (dB): -2.87
Mixed > Threshold(500.0): true
```

### 完整測試套件報告
```
=== 音訊管道測試報告 ===
測試套件: 完整音訊管道測試
總測試數: 7
成功數: 7
失敗數: 0
成功率: 100.0%
總耗時: 2435ms

=== 詳細測試結果 ===
✅ 檔案存在性檢查
   訊息: 影片和 BGM 檔案都存在
   耗時: 15ms

✅ 音訊格式檢查
   訊息: 支援的音訊格式: audio/mp4a-latm
   耗時: 32ms

✅ 音訊解碼測試
   訊息: 解碼成功: 192000 樣本, 2000ms, RMS: 8532.4
   耗時: 245ms

✅ PCM 混音測試
   訊息: 混音成功: 原聲 RMS=8000.0, BGM RMS=4000.0, 混音 RMS=8944.3
   耗時: 18ms

✅ 簡單 BGM 混音測試
   訊息: BGM 混音成功
   耗時: 1856ms

✅ 輸出檔案驗證
   訊息: 輸出檔案有效: 1MB
   耗時: 12ms

✅ 音訊品質檢查
   訊息: 音訊品質正常 (RMS: 1432.57, 峰值: 15623)
   耗時: 257ms
```

## 🔧 自定義測試

### 創建自定義測試
```kotlin
// 使用 AudioPipelineTester 創建自定義測試
val tester = AudioPipelineTester(context)

// 自定義測試參數
val customTestSuite = tester.runFullPipelineTest(
    videoPath = "/path/to/your/video.mp4",
    bgmPath = "/path/to/your/bgm.mp3", 
    outputDir = "/path/to/output/"
)

// 生成自定義報告
val report = tester.generateTestReport(customTestSuite)
println(report)
```

### 直接使用測試工具
```kotlin
// 直接使用 AudioMixUtils
val origPcm = AudioMixUtils.decodeAudioToPcm("/path/to/original.mp3")
val bgmPcm = AudioMixUtils.decodeAudioToPcm("/path/to/bgm.mp3")

if (origPcm != null && bgmPcm != null) {
    // 混音
    val mixedPcm = AudioMixUtils.mixPcmWithVolume(origPcm, bgmPcm, 1.0f, 0.7f)
    
    // 分析
    val stats = AudioMixUtils.getAudioStats(mixedPcm)
    println("混音結果: ${stats.sampleCount} 樣本, RMS: ${stats.rms}")
    
    // 編碼輸出
    AudioMixUtils.encodePcmToAac(mixedPcm, "/path/to/output.aac")
}
```

## ⚠️ 注意事項

1. **測試檔案大小** - 使用小檔案（<10MB）避免測試超時
2. **格式支援** - 確保測試檔案使用支援的格式
3. **權限** - Instrumentation 測試需要儲存權限
4. **設備連接** - Instrumentation 測試需要連接實體設備或模擬器
5. **資源清理** - 測試會自動清理暫存檔案

## 🎯 測試最佳實踐

1. **定期執行** - 在程式碼變更後執行測試
2. **多格式測試** - 使用不同格式的測試檔案
3. **邊界測試** - 測試極端情況（超短/超長音訊）
4. **效能監控** - 關注測試執行時間和記憶體使用
5. **報告分析** - 定期檢查 RMS 值和品質指標

這個測試系統將幫助您確保音訊處理功能的穩定性和正確性！🎉
