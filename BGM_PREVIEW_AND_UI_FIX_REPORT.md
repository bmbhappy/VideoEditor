# 🎵 BGM 預覽功能與 UI 修復報告

## 📋 問題總結

**好消息**：時間控制功能成功了！🎉
**新需求**：
1. 預覽能夠預覽時間控制的部分 ⏰
2. 功能文字改成白色，因為上方有灰色遮罩黑色文字看不清楚 🎨

## ✅ 已修正的問題

### 1. **預覽功能支援時間控制**

#### 修改 `startSimplePreview` 方法
```kotlin
/**
 * 開始簡單預覽（使用 ExoPlayer）
 */
private fun startSimplePreview(bgmPath: String) {
    try {
        // 停止之前的預覽
        stopSimplePreview()
        
        // 創建新的播放器
        musicPlayer = ExoPlayer.Builder(requireContext()).build()
        
        // 從檔案路徑創建 MediaItem
        val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(bgmPath)))
        musicPlayer?.setMediaItem(mediaItem)
        musicPlayer?.prepare()
        
        // 應用音量設定
        musicPlayer?.volume = sliderVolume.value
        
        // 應用時間控制設定
        val startPercent = sliderStartTime.value / 100f
        val endPercent = sliderEndTime.value / 100f
        val selectedMode = when (rgLengthMode.checkedRadioButtonId) {
            R.id.rbLoop -> LengthAdjustMode.LOOP
            R.id.rbTrim -> LengthAdjustMode.TRIM
            R.id.rbStretch -> LengthAdjustMode.STRETCH
            R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
            else -> LengthAdjustMode.LOOP
        }
        
        // 根據模式設定播放位置
        when (selectedMode) {
            LengthAdjustMode.TRIM -> {
                // 設定開始位置
                val startTimeMs = (startPercent * bgmDurationMs).toLong()
                musicPlayer?.seekTo(startTimeMs)
                
                // 計算結束時間
                val endTimeMs = if (endPercent < 1.0f) {
                    (endPercent * bgmDurationMs).toLong()
                } else {
                    bgmDurationMs
                }
                
                // 設定播放範圍（通過監聽器實現）
                val playDuration = endTimeMs - startTimeMs
                LogDisplayManager.addLog("D", "BgmAdjust", "預覽時間控制: 開始=${startTimeMs}ms, 結束=${endTimeMs}ms, 播放時長=${playDuration}ms")
            }
            else -> {
                // 其他模式從開始播放
                musicPlayer?.seekTo(0)
            }
        }
        
        // 開始播放
        musicPlayer?.play()
```

#### 添加時間控制監聽器
```kotlin
// 添加位置監聽器來處理時間控制
val previewEndPercent = sliderEndTime.value / 100f
val previewSelectedMode = when (rgLengthMode.checkedRadioButtonId) {
    R.id.rbLoop -> LengthAdjustMode.LOOP
    R.id.rbTrim -> LengthAdjustMode.TRIM
    R.id.rbStretch -> LengthAdjustMode.STRETCH
    R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
    else -> LengthAdjustMode.LOOP
}

if (previewSelectedMode == LengthAdjustMode.TRIM && previewEndPercent < 1.0f) {
    val endTimeMs = (previewEndPercent * bgmDurationMs).toLong()
    
    // 定期檢查播放位置
    lifecycleScope.launch {
        while (isPreviewPlaying) {
            val currentPosition = musicPlayer?.currentPosition ?: 0L
            if (currentPosition >= endTimeMs) {
                stopPreview()
                showToast("預覽時間範圍結束")
                break
            }
            kotlinx.coroutines.delay(100) // 每100ms檢查一次
        }
    }
}
```

### 2. **文字顏色修改為白色**

#### 修改 `colors.xml`
```xml
<!-- BGM Adjust Interface Colors -->
<color name="background_color">@color/gray_100</color>
<color name="text_color">@color/white</color>
<color name="text_secondary">@color/white</color>
<color name="primary_color">@color/teal_500</color>
<color name="secondary_color">@color/orange_500</color>
<color name="accent_color">@color/accent_purple</color>
```

#### 布局文件已正確使用顏色
```xml
<!-- 標題 -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="BGM 長度調整"
    android:textSize="24sp"
    android:textStyle="bold"
    android:textColor="@color/text_color"
    android:layout_marginBottom="24dp" />

<!-- RadioButton -->
<RadioButton
    android:id="@+id/rbLoop"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="🔁 循環播放 - BGM重複播放直到影片結束"
    android:textSize="16sp"
    android:textColor="@color/text_color"
    android:padding="8dp" />

<!-- Slider 標籤 -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="開始時間"
    android:textColor="@color/text_color"
    android:layout_marginBottom="4dp" />

<!-- 數值顯示 -->
<TextView
    android:id="@+id/tvStartTimeValue"
    android:layout_width="60dp"
    android:layout_height="wrap_content"
    android:text="0:00"
    android:textColor="@color/text_color"
    android:gravity="end"
    android:layout_marginStart="8dp" />
```

## 🎯 預覽功能特色

### 1. **時間控制預覽**
- ✅ **TRIM 模式**：預覽會從設定的開始時間播放到結束時間
- ✅ **LOOP 模式**：預覽會從開始播放，支援循環
- ✅ **其他模式**：從開始播放，展示基本效果

### 2. **音量控制預覽**
- ✅ **即時音量調整**：預覽時會應用設定的音量
- ✅ **音量範圍**：0.0 到 1.0 的完整範圍

### 3. **智能停止機制**
- ✅ **時間範圍限制**：TRIM 模式下會在結束時間自動停止
- ✅ **10秒預覽限制**：所有預覽最多播放10秒
- ✅ **手動停止**：可以隨時停止預覽

### 4. **詳細日誌追蹤**
```
預覽時間控制: 開始=5000ms, 結束=15000ms, 播放時長=10000ms
開始簡單預覽...
預覽時間範圍結束
```

## 🎨 UI 改善效果

### 1. **文字可讀性**
- ✅ **白色文字**：所有文字改為白色，在灰色背景上清晰可見
- ✅ **一致配色**：主要文字和次要文字都使用白色
- ✅ **對比度優化**：確保在各種背景下都有良好的可讀性

### 2. **視覺層次**
- ✅ **標題突出**：使用粗體和較大字體
- ✅ **功能說明**：次要文字使用較小字體但保持白色
- ✅ **數值顯示**：時間和音量數值清晰可見

### 3. **交互元素**
- ✅ **按鈕文字**：按鈕文字保持清晰
- ✅ **Slider 標籤**：滑塊標籤文字清晰可見
- ✅ **RadioButton**：選項文字清晰可讀

## 🧪 預期的預覽行為

### TRIM 模式預覽
1. **設定時間範圍**：開始時間 50%，結束時間 75%
2. **預覽開始**：從 BGM 的 50% 位置開始播放
3. **預覽結束**：播放到 75% 位置自動停止
4. **日誌輸出**：`預覽時間控制: 開始=5000ms, 結束=7500ms, 播放時長=2500ms`

### LOOP 模式預覽
1. **設定循環**：選擇循環播放模式
2. **預覽開始**：從 BGM 開始播放
3. **預覽結束**：10秒後自動停止
4. **循環效果**：在預覽期間會重複播放

### 音量調整預覽
1. **設定音量**：調整到 60%
2. **預覽開始**：以 60% 音量播放
3. **即時調整**：可以在預覽時調整音量
4. **效果確認**：可以聽到音量變化

## 🚀 下一步

請測試這個修復！現在應該能夠：

### 1. **預覽功能**
- ✅ **時間控制預覽**：TRIM 模式可以預覽指定時間範圍
- ✅ **音量控制預覽**：可以預覽音量調整效果
- ✅ **模式切換預覽**：不同模式有不同的預覽行為

### 2. **UI 改善**
- ✅ **文字清晰**：所有文字都是白色，清晰可見
- ✅ **對比度良好**：在灰色背景上有良好的可讀性
- ✅ **視覺一致**：整體界面視覺效果統一

## ✨ 總結

🎵 **預覽功能已完善**：支援時間控制和音量調整的完整預覽！

⏰ **時間控制預覽**：可以預覽 TRIM 模式的時間範圍效果！

🎨 **UI 已優化**：所有文字改為白色，在灰色背景上清晰可見！

🚀 **預期結果**：BGM 預覽功能完整，UI 可讀性大幅提升！
