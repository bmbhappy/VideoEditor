# 調試和UI改進總結

## 1. MediaCodec 剪裁執行步驟詳細日誌

### 問題描述
需要詳細追蹤 MediaCodec 剪裁過程，特別關注 `trackIndex is invalid` 錯誤。

### 解決方案
添加了詳細的執行步驟日誌，包括：

#### **執行步驟追蹤**
```kotlin
Log.d(TAG, "=== 開始裁剪影片 ===")
Log.d(TAG, "步驟 1: 創建 MediaExtractor")
Log.d(TAG, "步驟 2: 設定資料來源")
Log.d(TAG, "步驟 3: 檢查影片總長度: ${duration}ms")
Log.d(TAG, "步驟 4: 設定輸出檔案路徑: ${outputFile.absolutePath}")
Log.d(TAG, "步驟 5: 創建 MediaMuxer")
Log.d(TAG, "步驟 6: 檢測到 $trackCount 個軌道")
Log.d(TAG, "步驟 7: 開始設定軌道映射")
Log.d(TAG, "步驟 8: 啟動 MediaMuxer")
Log.d(TAG, "步驟 9: 定位到開始時間 ${startTimeMs}ms")
Log.d(TAG, "步驟 10: 開始處理樣本")
Log.d(TAG, "步驟 11: 處理完成統計")
Log.d(TAG, "步驟 12: 裁剪成功")
Log.d(TAG, "步驟 13: 清理資源")
```

#### **軌道映射詳細日誌**
```kotlin
Log.d(TAG, "軌道 $i: MIME類型 = $mimeType")
Log.d(TAG, "步驟 7.$i: 添加軌道 $i -> 輸出軌道 $outputTrackIndex (MIME: $mimeType)")
Log.d(TAG, "軌道映射表: $trackIndexMap")
```

#### **樣本處理詳細日誌**
```kotlin
Log.d(TAG, "步驟 10.$sampleCount: 已處理 $sampleCount 個樣本 (影片: $videoSampleCount, 音訊: $audioSampleCount)")
Log.d(TAG, "總共處理了 $sampleCount 個樣本")
Log.d(TAG, "影片樣本: $videoSampleCount 個")
Log.d(TAG, "音訊樣本: $audioSampleCount 個")
```

#### **錯誤追蹤**
```kotlin
try {
    muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
    // 處理成功
} catch (e: IllegalArgumentException) {
    Log.e(TAG, "步驟 10.$sampleCount: trackIndex is invalid 錯誤!")
    Log.e(TAG, "錯誤詳情: trackIndex=$trackIndex, outputTrackIndex=$outputTrackIndex")
    Log.e(TAG, "軌道映射表: $trackIndexMap")
    Log.e(TAG, "樣本信息: size=$sampleSize, time=${sampleTime}us, flags=${extractor.sampleFlags}")
    throw e
} catch (e: Exception) {
    Log.e(TAG, "步驟 10.$sampleCount: 寫入樣本時發生錯誤: ${e.message}")
    throw e
}
```

#### **完整錯誤報告**
```kotlin
Log.e(TAG, "=== 裁剪影片失敗 ===")
Log.e(TAG, "錯誤類型: ${e.javaClass.simpleName}")
Log.e(TAG, "錯誤訊息: ${e.message}")
e.printStackTrace()
```

### 預期的日誌輸出
當執行剪裁時，您會看到類似以下的詳細日誌：

```
D/VideoProcessor: === 開始裁剪影片 ===
D/VideoProcessor: 輸入 URI: content://media/external/video/media/123
D/VideoProcessor: 裁剪時間範圍: 1000ms - 5000ms
D/VideoProcessor: 步驟 1: 創建 MediaExtractor
D/VideoProcessor: 步驟 2: 設定資料來源
D/VideoProcessor: 步驟 3: 檢查影片總長度: 10000ms
D/VideoProcessor: 步驟 4: 設定輸出檔案路徑: /storage/emulated/0/Android/data/com.example.videoeditor/files/trimmed_1234567890.mp4
D/VideoProcessor: 步驟 5: 創建 MediaMuxer
D/VideoProcessor: 步驟 6: 檢測到 2 個軌道
D/VideoProcessor: 軌道 0: MIME類型 = video/avc
D/VideoProcessor: 步驟 7.0: 添加軌道 0 -> 輸出軌道 0 (MIME: video/avc)
D/VideoProcessor: 軌道 1: MIME類型 = audio/aac
D/VideoProcessor: 步驟 7.1: 添加軌道 1 -> 輸出軌道 1 (MIME: audio/aac)
D/VideoProcessor: 軌道映射表: {0=0, 1=1}
D/VideoProcessor: 步驟 8: 啟動 MediaMuxer
D/VideoProcessor: 步驟 9: 定位到開始時間 1000ms
D/VideoProcessor: 步驟 10: 開始處理樣本
D/VideoProcessor: 步驟 10.100: 已處理 100 個樣本 (影片: 60, 音訊: 40)
D/VideoProcessor: 步驟 10.200: 已處理 200 個樣本 (影片: 120, 音訊: 80)
...
D/VideoProcessor: 步驟 11: 處理完成統計
D/VideoProcessor: 總共處理了 500 個樣本
D/VideoProcessor: 影片樣本: 300 個
D/VideoProcessor: 音訊樣本: 200 個
D/VideoProcessor: 步驟 12: 裁剪成功
D/VideoProcessor: 輸出檔案: /storage/emulated/0/Android/data/com.example.videoeditor/files/trimmed_1234567890.mp4
D/VideoProcessor: 檔案大小: 2048576 bytes
D/VideoProcessor: 步驟 13: 清理資源
D/VideoProcessor: MediaExtractor 已釋放
D/VideoProcessor: MediaMuxer 已停止
D/VideoProcessor: MediaMuxer 已釋放
```

### 錯誤檢測
如果發生 `trackIndex is invalid` 錯誤，您會看到：

```
E/VideoProcessor: 步驟 10.150: trackIndex is invalid 錯誤!
E/VideoProcessor: 錯誤詳情: trackIndex=2, outputTrackIndex=null
E/VideoProcessor: 軌道映射表: {0=0, 1=1}
E/VideoProcessor: 樣本信息: size=1024, time=1500000us, flags=1
E/VideoProcessor: === 裁剪影片失敗 ===
E/VideoProcessor: 錯誤類型: IllegalArgumentException
E/VideoProcessor: 錯誤訊息: trackIndex is invalid
```

## 2. 放大影片播放區域

### 問題描述
影片播放區域太小，需要放大以提供更好的觀看體驗。

### 解決方案
修改所有 fragment 的布局文件，放大影片播放區域：

#### **修改內容**
1. **設置固定高度比例**：使用 `app:layout_constraintHeight_percent="0.7"` 讓影片播放器佔據螢幕高度的 70%
2. **減少邊距**：將 `android:layout_margin` 從 `16dp` 減少到 `8dp`
3. **減少控制區域的 padding**：從 `16dp` 減少到 `12dp`

#### **修改的文件**
- `fragment_trim.xml`
- `fragment_speed.xml`
- `fragment_audio.xml`
- `fragment_filter.xml`

#### **修改前後對比**

**修改前**：
```xml
<com.google.android.exoplayer2.ui.PlayerView
    android:id="@+id/playerView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_margin="16dp"
    app:layout_constraintBottom_toTopOf="@id/trimControls"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toBottomOf="@id/topBar" />
```

**修改後**：
```xml
<com.google.android.exoplayer2.ui.PlayerView
    android:id="@+id/playerView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_margin="8dp"
    app:layout_constraintBottom_toTopOf="@id/trimControls"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toBottomOf="@id/topBar"
    app:layout_constraintHeight_percent="0.7" />
```

### 效果
- **影片播放區域**：從約 50% 增加到 70% 的螢幕高度
- **控制區域**：保持功能完整，但佔用更少空間
- **整體體驗**：更好的影片觀看體驗，更清晰的編輯效果預覽

## 測試結果

✅ **詳細日誌系統** - 已實現
✅ **錯誤追蹤機制** - 已實現
✅ **影片播放區域放大** - 已實現
✅ **所有 fragment 統一改進** - 已實現
✅ **應用程式構建成功** - 已確認

## 使用說明

### 1. 調試日誌
- 在 Android Studio 的 Logcat 中過濾 `VideoProcessor` 標籤
- 執行剪裁功能時會看到詳細的執行步驟
- 如果發生錯誤，會顯示完整的錯誤信息和上下文

### 2. UI 改進
- 影片播放區域現在佔據螢幕高度的 70%
- 控制區域更加緊湊，但仍保持易用性
- 所有功能頁面都有一致的改進

### 3. 錯誤診斷
如果遇到 `trackIndex is invalid` 錯誤，日誌會顯示：
- 具體的軌道索引值
- 軌道映射表
- 樣本信息
- 完整的錯誤堆疊

現在您可以安裝並測試改進後的應用程式了！詳細的日誌系統將幫助您診斷任何 MediaCodec 相關的問題，而放大的影片播放區域將提供更好的用戶體驗。
