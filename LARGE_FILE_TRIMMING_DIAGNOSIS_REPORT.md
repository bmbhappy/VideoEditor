# 🔍 大檔案剪裁問題診斷報告

## 📋 問題描述

**用戶報告**: 小檔案剪裁成功，大檔案剪裁失敗

## 🔍 問題分析

### 1. 可能的原因

#### A. 檔案路徑解析問題
- **Content URI 處理**: 大檔案通常來自外部儲存，使用 content:// URI
- **權限問題**: 可能無法正確獲取檔案實際路徑
- **檔案大小檢測**: 無法正確檢測檔案大小，導致路由錯誤

#### B. 大檔案處理器觸發問題
- **閾值設定**: 100MB 閾值可能不適合所有情況
- **檔案存在性檢查**: 路徑解析失敗導致檔案不存在
- **MediaExtractor 初始化**: 大檔案可能導致 MediaExtractor 初始化失敗

#### C. 記憶體管理問題
- **緩衝區大小**: 256KB 緩衝區可能對某些大檔案不夠
- **資源清理**: 大檔案處理時資源清理不及時
- **OOM 風險**: 大檔案處理過程中可能觸發 OutOfMemoryError

### 2. 診斷改進

#### A. 增強日誌記錄
```kotlin
// 在 VideoProcessor.trimVideo() 中添加詳細日誌
com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "解析的檔案路徑: $inputPath")
com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案大小: ${fileSizeMB}MB")
com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "檔案存在: ${file.exists()}")
```

#### B. 改進 URI 路徑解析
```kotlin
// 在 getFilePathFromUri() 中添加詳細日誌
com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "處理 content URI: $uri")
com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoProcessor", "從 content URI 獲取路徑: $path")
```

#### C. 增強大檔案處理器錯誤檢查
```kotlin
// 在 LargeVideoProcessor.trimLargeVideo() 中添加檔案檢查
val inputFile = File(inputPath)
if (!inputFile.exists()) {
    Log.e(TAG, "輸入檔案不存在: $inputPath")
    updateState(State.ERROR)
    return@withContext false
}

Log.d(TAG, "輸入檔案大小: ${inputFile.length() / (1024 * 1024)}MB")
```

## 🛠️ 解決方案

### 1. 即時改進

#### A. 詳細日誌記錄
- ✅ 添加檔案路徑解析日誌
- ✅ 添加檔案大小和存在性檢查日誌
- ✅ 添加 URI 處理詳細日誌

#### B. 錯誤處理增強
- ✅ 改進檔案存在性檢查
- ✅ 增強影片長度檢測邏輯
- ✅ 添加更詳細的錯誤信息

#### C. 大檔案處理器優化
- ✅ 改進軌道檢測邏輯
- ✅ 增強錯誤狀態管理
- ✅ 添加檔案大小驗證

### 2. 測試步驟

#### A. 小檔案測試 (已成功)
- ✅ 檔案大小: < 100MB
- ✅ 處理方式: 普通處理器
- ✅ 結果: 成功

#### B. 大檔案測試 (需要驗證)
- 🔄 檔案大小: > 100MB
- 🔄 處理方式: 大檔案處理器
- 🔄 預期結果: 成功

### 3. 監控指標

#### A. 日誌監控
```bash
# 監控大檔案處理日誌
adb logcat -d | grep -E "(VideoProcessor|LargeVideoProcessor|大檔案)" | tail -20

# 監控錯誤日誌
adb logcat -d | grep -E "(ERROR|Exception|Failed)" | tail -20
```

#### B. 關鍵指標
- 檔案路徑解析成功率
- 檔案大小檢測準確性
- 大檔案處理器觸發率
- 處理成功率

## 📊 預期效果

### 1. 問題定位
- **精確診斷**: 通過詳細日誌快速定位問題
- **錯誤分類**: 區分路徑問題、處理問題、記憶體問題
- **解決方案**: 針對性修復

### 2. 穩定性提升
- **錯誤預防**: 提前檢測和處理潛在問題
- **資源管理**: 更好的記憶體和檔案資源管理
- **用戶體驗**: 更清晰的錯誤提示

### 3. 維護性改善
- **日誌追蹤**: 完整的處理流程日誌
- **問題重現**: 詳細的錯誤信息便於重現
- **性能監控**: 實時監控處理性能

## 🔄 下一步行動

### 1. 立即測試
- [ ] 測試大檔案剪裁功能
- [ ] 檢查詳細日誌輸出
- [ ] 驗證錯誤處理效果

### 2. 持續監控
- [ ] 監控大檔案處理成功率
- [ ] 收集用戶反饋
- [ ] 分析性能數據

### 3. 進一步優化
- [ ] 根據測試結果調整閾值
- [ ] 優化緩衝區大小
- [ ] 改進錯誤恢復機制

---

## 🔧 關鍵修復

### 第一階段修復：軌道選擇問題
**問題根源**: 大檔案處理器沒有調用 `extractor.selectTrack()` 來選擇要讀取的軌道，導致無法讀取任何數據。

**修復內容**:
```kotlin
// 在 trimLargeVideo() 和 changeLargeVideoSpeed() 中添加軌道選擇
extractor.selectTrack(videoTrackIndex)
if (audioTrackIndex >= 0) {
    extractor.selectTrack(audioTrackIndex)
}
```

**結果**: ✅ 大檔案剪裁功能修復成功

### 第二階段修復：變速和背景音樂問題
**問題根源**: MediaFormat 空指針異常，導致變速和背景音樂處理失敗。

**修復內容**:
```kotlin
// 在 changeLargeVideoSpeed() 中添加空檢查
require(videoFormat != null) { "影片格式為空" }
val outVideoTrack = muxer.addTrack(videoFormat)
if (audioTrackIndex >= 0 && audioFormat != null) {
    outAudioTrack = muxer.addTrack(audioFormat)
}

// 在 LargeBgmMixer 中添加空檢查
require(videoFormat != null) { "影片格式為空" }
require(bgmFormat != null) { "BGM格式為空" }
val outVideoTrack = muxer.addTrack(videoFormat)
val outBgmTrack = muxer.addTrack(bgmFormat)
```

**結果**: ✅ 背景音樂混音功能修復成功

### 第三階段修復：背景音樂 BufferOverflowException
**問題根源**: LargeBgmMixer 中的 `convertToAac` 方法發生 `BufferOverflowException`，編碼器輸入緩衝區容量不足。

**修復內容**:
```kotlin
// 檢查緩衝區大小，避免溢出
val remaining = encInBuf.remaining()
val dataSize = bufferInfo.size

if (remaining >= dataSize) {
    // 直接複製
    encInBuf.put(decodedBuf)
    encoder.queueInputBuffer(inEncIndex, 0, dataSize, 
        bufferInfo.presentationTimeUs, bufferInfo.flags)
} else {
    // 分塊複製
    val tempBuffer = ByteArray(remaining)
    decodedBuf.get(tempBuffer)
    encInBuf.put(tempBuffer)
    encoder.queueInputBuffer(inEncIndex, 0, remaining, 
        bufferInfo.presentationTimeUs, bufferInfo.flags)
    
    // 處理剩餘數據
    if (dataSize > remaining) {
        val remainingData = ByteArray(dataSize - remaining)
        decodedBuf.get(remainingData)
        
        val nextInEncIndex = encoder.dequeueInputBuffer(10_000)
        if (nextInEncIndex >= 0) {
            val nextEncInBuf = encoder.getInputBuffer(nextInEncIndex)!!
            nextEncInBuf.clear()
            nextEncInBuf.put(remainingData)
            encoder.queueInputBuffer(nextInEncIndex, 0, remainingData.size, 
                bufferInfo.presentationTimeUs, bufferInfo.flags)
        }
    }
}
```

**結果**: 🔄 等待測試驗證

### 第四階段修復：變速功能 IllegalArgumentException
**問題根源**: `IllegalArgumentException` 在 `MediaExtractor.readSampleData` 中，由於同時選擇多個軌道導致緩衝區狀態混亂。

**修復內容**:
```kotlin
// 分別處理影片和音訊軌道，避免緩衝區衝突
// 先處理影片軌道
extractor.selectTrack(videoTrackIndex)
while (true) {
    val size = extractor.readSampleData(buffer, 0)
    if (size < 0) break
    // 處理影片數據
    muxer.writeSampleData(outVideoTrack, buffer, info)
    extractor.advance()
}

// 再處理音訊軌道
if (audioTrackIndex >= 0 && outAudioTrack >= 0) {
    extractor.selectTrack(audioTrackIndex)
    while (true) {
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        // 處理音訊數據
        muxer.writeSampleData(outAudioTrack, buffer, info)
        extractor.advance()
    }
}
```

**結果**: 🔄 等待測試驗證

### 第五階段修復：緩衝區準備問題
**問題根源**: `readSampleData` 需要正確準備的 `ByteBuffer`，緩衝區的 position 和 limit 需要正確設置。

**修復內容**:
```kotlin
// 確保緩衝區準備好
buffer.clear()
info.offset = 0

// 確保緩衝區準備好
buffer.position(0)
buffer.limit(buffer.capacity())

val size = extractor.readSampleData(buffer, 0)
```

**結果**: 🔄 等待測試驗證

### 第六階段修復：trimLargeVideo 緩衝區準備問題
**問題根源**: `trimLargeVideo` 方法也需要同樣的緩衝區準備修復，但之前的修復沒有應用到這個方法。

**修復內容**:
```kotlin
// 在 trimLargeVideo 方法中添加緩衝區準備
buffer.clear()
info.offset = 0

// 確保緩衝區準備好
buffer.position(0)
buffer.limit(buffer.capacity())

val size = extractor.readSampleData(buffer, 0)
```

**結果**: 🔄 等待測試驗證

### 第七階段修復：ByteBuffer 類型問題（關鍵修復）
**問題根源**: 根據用戶提供的詳細分析，`MediaExtractor.readSampleData()` 需要 direct `ByteBuffer`（原生記憶體分配），但我們使用的是 `ByteBuffer.allocate()`（堆記憶體分配），導致 `IllegalArgumentException`。

**修復內容**:
```kotlin
// 修復前
val buffer = ByteBuffer.allocate(BUFFER_SIZE)

// 修復後
val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
```

**修復位置**:
- `trimLargeVideo` 方法中的 `ByteBuffer` 分配
- `changeLargeVideoSpeed` 方法中的 `ByteBuffer` 分配
- 所有使用 `readSampleData` 的地方

**技術說明**:
- `ByteBuffer.allocate()`: 在 Java 堆上分配記憶體，不適合 `readSampleData`
- `ByteBuffer.allocateDirect()`: 在原生記憶體上分配，適合 `readSampleData`
- 這是 Android Media API 的標準要求

**結果**: 🔄 等待測試驗證

### 第八階段修復：軌道選擇時機問題
**問題根源**: 在 `changeLargeVideoSpeed` 方法中，我們在 `muxer.start()` 之後才調用 `extractor.selectTrack(videoTrackIndex)`，但根據 Android Media API 的要求，應該在開始讀取之前就選擇軌道。

**修復內容**:
```kotlin
// 在 muxer.start() 之前選擇軌道
extractor.selectTrack(videoTrackIndex)
Log.d(TAG, "已選擇影片軌道進行讀取")

muxer.start()

// 分別處理影片和音訊軌道
Log.d(TAG, "開始分別處理影片和音訊軌道")
```

**技術說明**:
- `MediaExtractor` 需要在開始讀取樣本之前就選擇要讀取的軌道
- 在 `muxer.start()` 之前選擇軌道可以確保 `MediaExtractor` 處於正確的狀態
- 這是 Android Media API 的標準使用模式

**結果**: 🔄 等待測試驗證

### 第九階段修復：詳細診斷和錯誤處理（基於用戶分析）
**問題根源**: 根據用戶提供的詳細分析，`IllegalArgumentException` 在 `readSampleData()` 中可能由多個原因引起，需要詳細的診斷日誌和錯誤處理。

**修復內容**:

#### A. 動態緩衝區大小分配
```kotlin
// 根據軌道格式動態分配緩衝區大小
val maxInputSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BUFFER_SIZE)
Log.d(TAG, "影片軌道最大輸入大小: $maxInputSize bytes")

val buffer = ByteBuffer.allocateDirect(maxInputSize)
```

#### B. 詳細診斷日誌
```kotlin
// 詳細日誌記錄，診斷 readSampleData 問題
Log.d(TAG, "準備讀取樣本 - 緩衝區容量: ${buffer.capacity()}, 位置: ${buffer.position()}, 限制: ${buffer.limit()}")
Log.d(TAG, "當前軌道索引: ${extractor.sampleTrackIndex}, 樣本時間: ${extractor.sampleTime}, 樣本標誌: ${extractor.sampleFlags}")

val size = extractor.readSampleData(buffer, 0)
Log.d(TAG, "readSampleData 結果: $size bytes")
```

#### C. 軌道選擇驗證
```kotlin
// 驗證軌道選擇是否成功
if (extractor.sampleTrackIndex != videoTrackIndex) {
    Log.e(TAG, "軌道選擇失敗 - 期望: $videoTrackIndex, 實際: ${extractor.sampleTrackIndex}")
    throw IllegalStateException("軌道選擇失敗")
}
```

#### D. advance() 返回值檢查
```kotlin
// 檢查 advance() 的返回值
if (!extractor.advance()) {
    Log.d(TAG, "影片軌道已到達結尾")
    break
}
```

**技術說明**:
- 根據 `MediaFormat.KEY_MAX_INPUT_SIZE` 動態分配緩衝區大小，確保足夠的容量
- 詳細記錄 `MediaExtractor` 狀態和 `ByteBuffer` 狀態，便於診斷問題
- 驗證軌道選擇是否成功，避免無效的軌道索引
- 檢查 `advance()` 返回值，正確處理流結束情況

**結果**: 🔄 等待測試驗證

### 第十階段修復：trimLargeVideo 診斷和音訊軌道重新初始化
**問題根源**: 
1. `trimLargeVideo` 方法還沒有應用動態緩衝區分配和詳細日誌記錄
2. 變速功能中音訊軌道處理時，`extractor` 已經到達影片軌道結尾，需要重新初始化來處理音訊軌道

**修復內容**:

#### A. trimLargeVideo 動態緩衝區分配
```kotlin
// 根據軌道格式動態分配緩衝區大小
val maxInputSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BUFFER_SIZE)
Log.d(TAG, "影片軌道最大輸入大小: $maxInputSize bytes")

val buffer = ByteBuffer.allocateDirect(maxInputSize)
```

#### B. trimLargeVideo 詳細日誌記錄
```kotlin
// 詳細日誌記錄，診斷 readSampleData 問題
Log.d(TAG, "準備讀取剪裁樣本 - 緩衝區容量: ${buffer.capacity()}, 位置: ${buffer.position()}, 限制: ${buffer.limit()}")
Log.d(TAG, "當前軌道索引: ${extractor.sampleTrackIndex}, 樣本時間: ${extractor.sampleTime}, 樣本標誌: ${extractor.sampleFlags}")

val size = extractor.readSampleData(buffer, 0)
Log.d(TAG, "剪裁 readSampleData 結果: $size bytes")
```

#### C. 音訊軌道重新初始化
```kotlin
// 重新初始化 extractor 來處理音訊軌道
extractor.release()
extractor = MediaExtractor()
extractor.setDataSource(inputPath)
extractor.selectTrack(audioTrackIndex)
```

**技術說明**:
- 確保 `trimLargeVideo` 方法也應用所有診斷修復
- 在處理音訊軌道前重新初始化 `MediaExtractor`，確保能正確讀取音訊樣本
- 這樣可以確保音訊軌道的 PTS 調整能正確生效

**結果**: 🔄 等待測試驗證

### 第十一階段修復：音訊軌道變速優化（基於用戶分析）
**問題根源**: 
基於用戶的詳細分析，音訊軌道變速需要更精確的時間戳處理和單調性檢查，以確保 MediaMuxer 能正確處理變速後的音訊。

**修復內容**:

#### A. 音訊軌道時間戳單調性檢查
```kotlin
// 音訊軌道時間戳單調性檢查
var lastAdjustedAudioPtsUs: Long = 0
var audioSamplesProcessed = 0L
```

#### B. 精確的時間戳調整（策略1：直接傳遞壓縮音訊）
```kotlin
// 調整 PTS 來改變速度（策略1：直接傳遞壓縮音訊，只調整時間戳）
val originalPtsUs = extractor.sampleTime
val adjustedPtsUs = (originalPtsUs / speedFactor).toLong()

// 確保時間戳單調性（MediaMuxer 要求時間戳單調遞增）
val finalPtsUs = if (adjustedPtsUs < lastAdjustedAudioPtsUs && lastAdjustedAudioPtsUs != 0L) {
    lastAdjustedAudioPtsUs + 1 // 確保至少比前一個時間戳大1微秒
} else {
    adjustedPtsUs
}

info.presentationTimeUs = finalPtsUs
info.flags = extractor.sampleFlags
lastAdjustedAudioPtsUs = finalPtsUs
```

#### C. 詳細的音訊處理日誌
```kotlin
Log.d(TAG, "音訊樣本 $audioSamplesProcessed - 原始PTS: ${originalPtsUs}us, 調整後PTS: ${adjustedPtsUs}us, 最終PTS: ${finalPtsUs}us")
```

#### D. 音訊格式驗證和日誌
```kotlin
if (audioTrackIndex >= 0 && audioFormat != null) {
    outAudioTrack = muxer.addTrack(audioFormat)
    Log.d(TAG, "音訊軌道已添加到muxer，格式: ${audioFormat.getString(MediaFormat.KEY_MIME)}")
    Log.d(TAG, "音訊採樣率: ${audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)}Hz")
    Log.d(TAG, "音訊聲道數: ${audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}")
} else {
    Log.d(TAG, "沒有音訊軌道或音訊格式為空")
}
```

#### E. 音訊處理統計
```kotlin
Log.d(TAG, "速度變更完成，總共處理 $processedSamples 個影片樣本，$audioSamplesProcessed 個音訊樣本")
```

**技術說明**:
- **策略1實現**: 直接傳遞壓縮音訊，只調整時間戳（更簡單且高效）
- **時間戳單調性**: 確保 MediaMuxer 接收到的時間戳是單調遞增的
- **精確計算**: `newPresentationTimeUs = originalPresentationTimeUs / speedFactor`
- **音訊格式驗證**: 確保音訊軌道格式正確並記錄詳細信息
- **處理統計**: 分別統計影片和音訊樣本的處理數量

**預期效果**:
- ✅ 音訊軌道正確變速（音調會改變，這是預期的）
- ✅ 時間戳單調性得到保證
- ✅ 詳細的音訊處理日誌便於調試
- ✅ 音訊格式信息完整記錄

**結果**: 🔄 等待測試驗證

### 輸出檔案驗證
```kotlin
// 添加輸出檔案大小檢查
val outputFile = File(outputPath)
if (outputFile.exists() && outputFile.length() > 0) {
    Log.d(TAG, "輸出檔案大小: ${outputFile.length() / (1024 * 1024)}MB")
    // 成功
} else {
    Log.e(TAG, "輸出檔案不存在或為空: $outputPath")
    // 失敗
}
```

### 詳細日誌
- ✅ 添加軌道索引日誌
- ✅ 添加軌道選擇確認日誌
- ✅ 添加輸出檔案大小驗證日誌
- ✅ 添加 MediaFormat 空檢查

---

**報告創建時間**: 2024-12-19  
**版本**: v1.5.1  
**狀態**: 關鍵問題已修復，等待測試驗證
