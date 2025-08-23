# 🚀 音訊測試系統 - 快速開始指南

## 🎯 立即體驗測試功能

### 📱 在應用中啟用測試菜單

**只需 3 步！**

1. **編譯並安裝應用**
   ```bash
   ./gradlew assembleDebug
   # 安裝到設備
   ```

2. **啟動應用程式**

3. **長按「檔案管理器」按鈕** 🔒
   - 會彈出 "🧪 音訊測試系統" 菜單

### 🧪 測試選項說明

| 選項 | 功能 | 適用場景 |
|------|------|----------|
| 🚀 執行完整測試套件 | 運行全部 7 項測試 | 完整功能驗證 |
| 🎵 PCM 混音測試 | 測試基礎音訊混音 | 快速功能檢查 |
| 🔊 音訊解碼測試 | 測試音訊檔案解碼 | 格式相容性檢查 |
| 📊 音訊品質檢查 | 分析音訊統計數據 | 品質評估 |
| 🏥 系統健康檢查 | 評估整體系統狀態 | 定期維護檢查 |
| ⚡ 效能基準測試 | 測試處理效能 | 效能優化 |

## 📊 測試結果說明

### ✅ 成功示例
```
🎉 測試套件完成
成功率: 100.0%
成功: 7/7
耗時: 2435ms
```

### ⚠️ 部分失敗示例
```
🎉 測試套件完成  
成功率: 85.7%
成功: 6/7
耗時: 3120ms

❌ 簡單 BGM 混音測試
訊息: BGM 混音失敗: 不支援的音訊格式
```

## 💡 快速提示

- **綠色結果 (✅)** = 功能正常
- **紅色結果 (❌)** = 需要檢查
- **長按其他按鈕** = 暫無隱藏功能
- **查看 LogDisplayActivity** = 詳細測試日誌

## 🔧 自定義使用

### 在您的代碼中調用測試

```kotlin
// 最簡單的使用方式
TestExample.runFullTestExample(this)

// 單個測試
val testRunner = TestRunner(this)
testRunner.runSingleTest("PCM 混音測試", callback)

// 健康檢查
TestExample.audioHealthCheck(this)
```

### 自定義回調

```kotlin
val callback = object : TestRunner.TestCallback {
    override fun onTestCompleted(testName: String, success: Boolean, message: String) {
        if (success) {
            Toast.makeText(this@MainActivity, "✅ $testName 通過", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this@MainActivity, "❌ $testName 失敗", Toast.LENGTH_LONG).show()
        }
    }
    // ... 其他回調方法
}
```

## 🎯 常見使用場景

### 1. 開發時測試
```kotlin
// 在開發過程中快速驗證音訊功能
if (BuildConfig.DEBUG) {
    TestExample.runFullTestExample(this)
}
```

### 2. 發布前檢查
```kotlin
// 發布前執行完整測試
TestExample.performanceBenchmark(this)
```

### 3. 用戶問題診斷
```kotlin
// 用戶反應音訊問題時，運行診斷
TestExample.audioHealthCheck(this)
```

## 🚀 立即開始

1. **編譯專案** ✅ (已完成)
2. **安裝到設備** 📱
3. **長按檔案管理器按鈕** 🔒  
4. **選擇測試項目** 🧪
5. **查看結果** 📊

就這麼簡單！您的音訊測試系統已經完全整合並可以使用了！🎉
