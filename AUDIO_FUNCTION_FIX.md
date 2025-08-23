# 音訊功能修復 - 背景音樂添加錯誤解決

## 問題分析

根據日誌顯示，背景音樂功能出現了「Failed to add the track to the muxer」錯誤。這個錯誤通常發生在以下情況：

1. **音樂檔案格式問題**: 音樂檔案可能沒有音訊軌道或格式不兼容
2. **MediaMuxer 添加軌道失敗**: 在嘗試添加音訊軌道到 MediaMuxer 時發生異常
3. **缺少錯誤處理**: 原始代碼沒有適當的錯誤處理和診斷信息

## 修復內容

### 1. 增強軌道檢測和錯誤處理

#### 影片軌道處理
```kotlin
for (i in 0 until videoExtractor.trackCount) {
    val f = videoExtractor.getTrackFormat(i)
    val mime = f.getString(MediaFormat.KEY_MIME)
    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "影片檔案軌道 $i: MIME類型 = $mime")
    if (mime?.startsWith("video/") == true) {
        try {
            videoInTrack = i
            videoOutTrack = muxer.addTrack(f)
            videoExtractor.selectTrack(i)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "選取影片軌道 $i -> 輸出 $videoOutTrack")
            break
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "添加影片軌道失敗: ${e.message}")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "軌道格式: $f")
            throw e
        }
    }
}

if (videoInTrack < 0) {
    com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "影片檔案中沒有找到影片軌道")
    throw IllegalArgumentException("影片檔案中沒有找到影片軌道")
}
```

#### 音訊軌道處理
```kotlin
for (i in 0 until musicExtractor.trackCount) {
    val f = musicExtractor.getTrackFormat(i)
    val mime = f.getString(MediaFormat.KEY_MIME)
    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "音樂檔案軌道 $i: MIME類型 = $mime")
    if (mime?.startsWith("audio/") == true) {
        try {
            audioInTrack = i
            audioOutTrack = muxer.addTrack(f)
            musicExtractor.selectTrack(i)
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "選取音樂軌道 $i -> 輸出 $audioOutTrack")
            break
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "添加音樂軌道失敗: ${e.message}")
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "軌道格式: $f")
            throw e
        }
    }
}

if (audioInTrack < 0) {
    com.example.videoeditor.utils.LogDisplayManager.addLog("W", TAG, "音樂檔案中沒有找到音訊軌道")
    // 如果沒有音訊軌道，只處理影片
    audioOutTrack = -1
}
```

### 2. 改進影片樣本處理

```kotlin
// 複製影片樣本
if (videoInTrack >= 0 && videoOutTrack >= 0) {
    var videoSampleCount = 0
    while (true) {
        val t = videoExtractor.sampleTrackIndex
        if (t < 0) break
        val size = videoExtractor.readSampleData(buffer, 0)
        if (size < 0) break
        bufferInfo.offset = 0
        bufferInfo.size = size
        bufferInfo.presentationTimeUs = videoExtractor.sampleTime
        bufferInfo.flags = videoExtractor.sampleFlags
        
        try {
            muxer.writeSampleData(videoOutTrack, buffer, bufferInfo)
            videoSampleCount++
            
            if (videoSampleCount % 100 == 0) {
                com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "已處理 $videoSampleCount 個影片樣本")
            }
        } catch (e: Exception) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "寫入影片樣本失敗: ${e.message}")
            throw e
        }
        
        videoExtractor.advance()
    }
    
    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "影片處理完成，總共處理 $videoSampleCount 個樣本")
}
```

### 3. 改進音樂循環播放邏輯

```kotlin
// 複製音樂樣本並循環播放
if (audioInTrack >= 0 && audioOutTrack >= 0) {
    val videoDuration = getVideoDuration(videoExtractor)
    val musicDuration = getMusicDuration(musicExtractor)
    
    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "影片時長: ${videoDuration}us, 音樂時長: ${musicDuration}us")
    
    if (musicDuration <= 0) {
        com.example.videoeditor.utils.LogDisplayManager.addLog("W", TAG, "音樂時長為0或無效，跳過音樂處理")
    } else {
        // 音樂循環播放邏輯...
        var currentTimeUs = 0L
        var musicCycleCount = 0
        
        while (currentTimeUs < videoDuration) {
            // 重置音樂到開始位置
            musicExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            musicCycleCount++
            
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "開始音樂循環 $musicCycleCount, 當前時間: ${currentTimeUs}us")
            
            while (currentTimeUs < videoDuration) {
                val t = musicExtractor.sampleTrackIndex
                if (t < 0) break
                
                val size = musicExtractor.readSampleData(buffer, 0)
                if (size < 0) break
                
                val sampleTime = musicExtractor.sampleTime
                if (sampleTime >= musicDuration) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "音樂循環 $musicCycleCount 完成，準備下一個循環")
                    break
                }
                
                bufferInfo.offset = 0
                bufferInfo.size = size
                bufferInfo.presentationTimeUs = currentTimeUs + sampleTime
                bufferInfo.flags = musicExtractor.sampleFlags
                
                try {
                    muxer.writeSampleData(audioOutTrack, buffer, bufferInfo)
                } catch (e: Exception) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("E", TAG, "寫入音樂樣本失敗: ${e.message}")
                    throw e
                }
                
                musicExtractor.advance()
                
                // 更新當前時間
                currentTimeUs = bufferInfo.presentationTimeUs
                
                if (currentTimeUs >= videoDuration) {
                    com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "已達到影片時長，停止音樂處理")
                    break
                }
            }
        }
        
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", TAG, "音樂循環播放完成，總循環次數: $musicCycleCount, 總時長: ${currentTimeUs}us")
    }
} else {
    com.example.videoeditor.utils.LogDisplayManager.addLog("W", TAG, "沒有音訊軌道，只處理影片")
}
```

## 修復特點

### 1. 詳細的診斷日誌
- 記錄每個軌道的 MIME 類型
- 記錄軌道添加過程的詳細信息
- 提供錯誤發生時的具體上下文

### 2. 健壯的錯誤處理
- 使用 try-catch 包裝關鍵操作
- 提供具體的錯誤訊息和軌道格式信息
- 優雅處理沒有音訊軌道的情況

### 3. 改進的音樂循環邏輯
- 檢查音樂時長的有效性
- 添加音樂循環計數和進度日誌
- 改進時間戳計算和同步

### 4. 樣本處理監控
- 添加影片樣本處理計數
- 定期輸出處理進度
- 監控寫入操作的異常

## 測試建議

1. **測試不同格式的音樂檔案**:
   - MP3, AAC, WAV, OGG 等格式
   - 有音訊軌道和無音訊軌道的檔案

2. **測試邊界情況**:
   - 非常短的音樂檔案
   - 非常長的音樂檔案
   - 損壞的音樂檔案

3. **檢查日誌輸出**:
   - 確認軌道檢測日誌
   - 確認處理進度日誌
   - 確認錯誤處理日誌

## 編譯狀態

✅ **編譯成功** - 所有修復都已成功整合並通過編譯測試

## 預期效果

修復後，背景音樂功能應該能夠：
- 正確檢測和處理不同格式的音樂檔案
- 提供詳細的診斷信息幫助問題排查
- 優雅處理錯誤情況而不崩潰
- 在沒有音訊軌道時仍能正常處理影片
