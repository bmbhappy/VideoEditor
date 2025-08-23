# 🎵 如何在應用程式中使用音訊測試系統

## 📱 在應用程式中啟用測試功能

### 方法 1: 隱藏測試菜單（已整合）

我已經在 `MainActivity` 中整合了隱藏的測試功能！

**🔐 啟用方式：**
1. 打開應用程式
2. **長按「檔案管理器」按鈕** 
3. 會彈出音訊測試系統菜單

**📋 可用的測試選項：**
- 🚀 **執行完整測試套件** - 運行所有測試項目
- 🎵 **PCM 混音測試** - 測試音訊混音功能
- 🔊 **音訊解碼測試** - 測試音訊解碼功能  
- 📊 **音訊品質檢查** - 分析音訊品質
- 🏥 **系統健康檢查** - 檢查音訊系統狀態
- ⚡ **效能基準測試** - 測試處理效能

### 方法 2: 手動在代碼中調用

如果您想在其他地方手動調用測試，可以使用以下方式：

#### 2.1 在 Activity 中使用

```kotlin
import com.example.test.TestRunner
import com.example.test.TestExample

class YourActivity : AppCompatActivity() {
    
    private fun runAudioTests() {
        // 方式 1: 使用 TestExample（推薦）
        TestExample.runFullTestExample(this)
        
        // 方式 2: 直接使用 TestRunner
        val testRunner = TestRunner(this)
        testRunner.runFullTestSuite(
            callback = object : TestRunner.TestCallback {
                override fun onTestStarted(testName: String) {
                    Log.i("TEST", "開始測試: $testName")
                }
                
                override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                    val status = if (success) "✅" else "❌"
                    Log.i("TEST", "$status $testName: $message")
                    
                    // 顯示結果給用戶
                    Toast.makeText(this@YourActivity, 
                        "$status $testName", Toast.LENGTH_SHORT).show()
                }
                
                override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                    val successRate = String.format("%.1f", testSuite.successRate * 100)
                    Log.i("TEST", "測試完成，成功率: $successRate%")
                    
                    // 顯示完整結果
                    AlertDialog.Builder(this@YourActivity)
                        .setTitle("測試完成")
                        .setMessage("成功率: $successRate%\n耗時: ${testSuite.totalDuration}ms")
                        .setPositiveButton("確定", null)
                        .show()
                }
                
                override fun onError(error: String) {
                    Log.e("TEST", "測試錯誤: $error")
                    Toast.makeText(this@YourActivity, "測試錯誤: $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
```

#### 2.2 在 Fragment 中使用

```kotlin
import com.example.test.TestRunner

class YourFragment : Fragment() {
    
    private fun runQuickTest() {
        val testRunner = TestRunner(requireContext())
        
        // 執行單個測試
        testRunner.runSingleTest("PCM 混音測試", object : TestRunner.TestCallback {
            override fun onTestStarted(testName: String) {
                // 顯示載入指示器
                view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.VISIBLE
            }
            
            override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                // 隱藏載入指示器
                view?.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
                
                // 顯示結果
                val resultText = if (success) "✅ 測試通過" else "❌ 測試失敗"
                view?.findViewById<TextView>(R.id.resultText)?.text = resultText
            }
            
            override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
                // 單個測試不會觸發此回調
            }
            
            override fun onError(error: String) {
                Toast.makeText(context, "測試出錯: $error", Toast.LENGTH_LONG).show()
            }
        })
    }
}
```

#### 2.3 使用預設範例

最簡單的方式是使用我們提供的 `TestExample`：

```kotlin
// 完整測試套件
TestExample.runFullTestExample(context)

// 自定義檔案測試
TestExample.customAudioTestExample(context, videoPath, bgmPath)

// 直接音訊工具測試
TestExample.directAudioToolExample(context)

// 健康檢查
TestExample.audioHealthCheck(context)

// 效能基準測試
TestExample.performanceBenchmark(context)
```

## 🔧 進階使用方式

### 3.1 自定義測試回調

創建您自己的測試回調來處理結果：

```kotlin
class CustomTestCallback(private val activity: Activity) : TestRunner.TestCallback {
    override fun onTestStarted(testName: String) {
        // 更新 UI 顯示測試進度
        activity.runOnUiThread {
            activity.findViewById<TextView>(R.id.statusText)?.text = "正在執行: $testName"
            activity.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.VISIBLE
        }
    }
    
    override fun onTestCompleted(testName: String, success: Boolean, message: String) {
        // 記錄結果到日誌系統
        if (success) {
            LogDisplayManager.addLog("I", "TEST", "✅ $testName 成功: $message")
        } else {
            LogDisplayManager.addLog("E", "TEST", "❌ $testName 失敗: $message")
        }
        
        // 發送到分析系統（如果有的話）
        // Analytics.track("audio_test_completed", mapOf("test" to testName, "success" to success))
    }
    
    override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {
        activity.runOnUiThread {
            activity.findViewById<ProgressBar>(R.id.progressBar)?.visibility = View.GONE
            
            // 根據成功率顯示不同顏色
            val statusText = activity.findViewById<TextView>(R.id.statusText)
            when {
                testSuite.successRate >= 0.9 -> {
                    statusText?.setTextColor(Color.GREEN)
                    statusText?.text = "🎉 音訊系統狀態優秀"
                }
                testSuite.successRate >= 0.7 -> {
                    statusText?.setTextColor(Color.YELLOW)
                    statusText?.text = "⚠️ 音訊系統需要關注"
                }
                else -> {
                    statusText?.setTextColor(Color.RED)
                    statusText?.text = "❌ 音訊系統需要修復"
                }
            }
        }
    }
    
    override fun onError(error: String) {
        LogDisplayManager.addLog("E", "TEST", "💥 測試系統錯誤: $error")
        activity.runOnUiThread {
            Toast.makeText(activity, "測試系統錯誤", Toast.LENGTH_LONG).show()
        }
    }
}

// 使用自定義回調
val testRunner = TestRunner(this)
testRunner.runFullTestSuite(callback = CustomTestCallback(this))
```

### 3.2 定時自動測試

設定定時自動執行健康檢查：

```kotlin
class MainActivity : AppCompatActivity() {
    private val healthCheckInterval = 30 * 60 * 1000L // 30分鐘
    private val healthCheckHandler = Handler(Looper.getMainLooper())
    
    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            Log.i("HealthCheck", "執行定時音訊健康檢查")
            TestExample.audioHealthCheck(this@MainActivity)
            
            // 排程下次檢查
            healthCheckHandler.postDelayed(this, healthCheckInterval)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 開始定時健康檢查
        healthCheckHandler.postDelayed(healthCheckRunnable, healthCheckInterval)
    }
    
    override fun onPause() {
        super.onPause()
        // 停止定時檢查
        healthCheckHandler.removeCallbacks(healthCheckRunnable)
    }
}
```

### 3.3 條件式測試

根據應用狀態決定是否執行測試：

```kotlin
class AudioTestManager(private val context: Context) {
    
    fun runConditionalTest() {
        // 檢查是否在 Debug 模式
        if (!BuildConfig.DEBUG) {
            Log.d("AudioTest", "Release 模式下跳過測試")
            return
        }
        
        // 檢查是否有測試檔案
        if (!hasTestResources()) {
            Log.w("AudioTest", "缺少測試資源檔案")
            return
        }
        
        // 檢查設備效能
        if (isLowPerformanceDevice()) {
            Log.i("AudioTest", "低效能設備，執行簡化測試")
            runSimplifiedTest()
        } else {
            Log.i("AudioTest", "執行完整測試")
            TestExample.runFullTestExample(context)
        }
    }
    
    private fun hasTestResources(): Boolean {
        return try {
            context.resources.openRawResource(R.raw.sample_video).close()
            context.resources.openRawResource(R.raw.sample_bgm).close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isLowPerformanceDevice(): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.isLowRamDevice
    }
    
    private fun runSimplifiedTest() {
        val testRunner = TestRunner(context)
        testRunner.runSingleTest("PCM 混音測試", object : TestRunner.TestCallback {
            override fun onTestStarted(testName: String) {}
            override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                Log.i("AudioTest", "簡化測試結果: $success")
            }
            override fun onTestSuiteCompleted(testSuite: AudioPipelineTester.TestSuite) {}
            override fun onError(error: String) {
                Log.e("AudioTest", "簡化測試錯誤: $error")
            }
        })
    }
}
```

## 📊 測試結果解讀

### 成功率指標

- **90%+ (💚)** - 優秀：音訊系統運行完美
- **80-89% (💛)** - 良好：系統正常，可能有小問題
- **60-79% (🧡)** - 需要關注：有明顯問題需要修復
- **<60% (❤️)** - 需要立即修復：系統有嚴重問題

### 常見測試結果

#### ✅ 全部通過
```
測試完成
成功率: 100.0%
成功: 7/7
耗時: 2435ms
```
**含義：** 音訊系統運行完美，所有功能正常。

#### ⚠️ 部分失敗
```
測試完成
成功率: 85.7%
成功: 6/7
耗時: 3120ms

❌ 簡單 BGM 混音測試
   訊息: BGM 混音失敗: 不支援的音訊格式
```
**含義：** 大部分功能正常，但 BGM 混音有格式相容性問題。

#### ❌ 多項失敗
```
測試完成
成功率: 42.9%
成功: 3/7
耗時: 1250ms

❌ 音訊解碼測試: 解碼器初始化失敗
❌ PCM 混音測試: 混音算法錯誤
❌ BGM 混音測試: 檔案權限錯誤
❌ 音訊品質檢查: RMS 計算異常
```
**含義：** 音訊系統有嚴重問題，需要立即檢查和修復。

## 🚀 快速開始

1. **編譯並安裝應用程式**
2. **長按「檔案管理器」按鈕**
3. **選擇「🚀 執行完整測試套件」**
4. **查看測試結果和報告**

就這麼簡單！您的音訊測試系統已經完全整合到應用程式中了！🎉
