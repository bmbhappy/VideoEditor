# 快速回滾指南 - 恢復到穩定版本 v1.0.0

## 當出現以下問題時，請使用此指南：

### 🔴 常見問題症狀
1. 播放按鈕無響應
2. 控制列不顯示
3. 控制列被功能區域遮擋
4. 編譯錯誤
5. 播放器功能異常

## 🚀 快速回滾步驟

### 步驟 1：檢查佈局檔案
確保以下四個檔案的PlayerView設定正確：

**fragment_trim.xml**
```xml
<com.google.android.exoplayer2.ui.PlayerView
    android:id="@+id/playerView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_margin="8dp"
    app:layout_constraintBottom_toTopOf="@id/trimControls"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintHeight_percent="0.65"
    app:use_controller="true"
    app:show_buffering="when_playing" />
```

**fragment_speed.xml**
```xml
<com.google.android.exoplayer2.ui.PlayerView
    android:id="@+id/playerView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_margin="8dp"
    app:layout_constraintBottom_toTopOf="@id/speedControls"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintHeight_percent="0.55"
    app:use_controller="true"
    app:show_buffering="when_playing" />
```

**fragment_audio.xml**
```xml
<com.google.android.exoplayer2.ui.PlayerView
    android:id="@+id/playerView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_margin="8dp"
    app:layout_constraintBottom_toTopOf="@id/audioControls"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintHeight_percent="0.55"
    app:use_controller="true"
    app:show_buffering="when_playing" />
```

**fragment_filter.xml**
```xml
<com.google.android.exoplayer2.ui.PlayerView
    android:id="@+id/playerView"
    android:layout_width="0dp"
    android:layout_height="0dp"
    android:layout_margin="8dp"
    app:layout_constraintBottom_toTopOf="@id/filterControls"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintHeight_percent="0.65"
    app:use_controller="true"
    app:show_buffering="when_playing" />
```

### 步驟 2：檢查Fragment代碼
確保所有Fragment的 `onVideoLoaded` 方法包含以下代碼：

```kotlin
fun onVideoLoaded(uri: Uri, path: String?) {
    Log.d(TAG, "影片載入: $path")
    
    videoUri = uri
    videoPath = path
    
    // 確保播放器已初始化
    if (player == null) {
        setupPlayer()
    }
    
    // 載入影片到播放器
    val mediaItem = MediaItem.fromUri(uri)
    player?.setMediaItem(mediaItem)
    player?.prepare()
    
    // 獲取影片時長
    videoDuration = VideoUtils.getVideoDuration(requireContext(), uri)
    Log.d(TAG, "影片時長: ${VideoUtils.formatDuration(videoDuration)}")
    
    // 確保播放器控制列可用
    binding.playerView.useController = true
    Log.d(TAG, "播放器控制列已啟用")
}
```

### 步驟 3：刪除自定義控制列檔案
如果存在以下檔案，請刪除：
- `app/src/main/res/layout/exo_player_control_view.xml`

### 步驟 4：編譯測試
```bash
./gradlew assembleDebug --no-daemon
```

### 步驟 5：功能測試
測試以下功能：
- [ ] 影片載入
- [ ] 播放器控制列顯示
- [ ] 播放/暫停按鈕響應
- [ ] 進度條拖拽
- [ ] 各頁面佈局正常

## 🔧 常見修復

### 問題：播放按鈕無響應
**解決方案：**
1. 確保 `app:use_controller="true"`
2. 確保 `binding.playerView.useController = true`
3. 刪除自定義控制列檔案

### 問題：控制列被遮擋
**解決方案：**
1. 檢查播放器高度設定
2. 剪裁/濾鏡頁面：65%
3. 變速/音樂頁面：55%

### 問題：編譯錯誤
**解決方案：**
1. 清理專案：`./gradlew clean`
2. 重新編譯：`./gradlew assembleDebug`

## 📞 緊急聯繫
如果以上步驟無法解決問題，請：
1. 檢查 `VERSION_SNAPSHOT_v1.0.0.md` 檔案
2. 對比當前代碼與穩定版本
3. 恢復到最後一次正常工作的狀態

## ✅ 成功標誌
當看到以下情況時，表示回滾成功：
- 編譯成功 (BUILD SUCCESSFUL)
- 播放按鈕正常響應
- 控制列正常顯示
- 所有頁面佈局正確
