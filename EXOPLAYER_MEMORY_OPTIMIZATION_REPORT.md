# ExoPlayer 記憶體優化解決方案報告

## 問題分析

### 新的 OutOfMemoryError 特徵
根據最新的崩潰日誌，發現了一個與之前不同的記憶體問題：

```
OutOfMemoryError: Failed to allocate a 56 byte allocation with 1480 free bytes and 1480B until OOM, 
target footprint 268435456, growth limit 268435456; giving up on allocation because <1% of heap free after GC.
```

**關鍵特徵**：
- **發生位置**：`FinalizerDaemon` 和 `ExoPlayer:Loader:ProgressiveMediaPeriod`
- **記憶體狀態**：只有 1480 bytes 可用，嘗試分配 56 bytes
- **觸發原因**：ExoPlayer 在載入媒體時無法獲得足夠記憶體

### 與之前 OOM 的差異

| 特徵 | 之前的 OOM | 新的 OOM |
|------|------------|----------|
| 發生位置 | UI 渲染線程 (RippleDrawable) | ExoPlayer 載入線程 |
| 記憶體需求 | 24 bytes | 56 bytes |
| 根本原因 | UI 資源爭奪記憶體 | ExoPlayer 內部緩衝區分配失敗 |
| 觸發時機 | UI 互動時 | 媒體載入時 |

## 解決方案設計

### 1. ExoPlayerMemoryOptimizer 核心功能

#### **記憶體感知的 ExoPlayer 創建**
```kotlin
fun createOptimizedExoPlayer(context: Context): ExoPlayer {
    // 檢查記憶體狀態
    val memoryStatus = MemoryOptimizer.checkMemoryStatus(context)
    val isLowMemory = MemoryOptimizer.isMemoryLow(context)
    
    return ExoPlayer.Builder(context)
        .setLoadControl(createOptimizedLoadControl(isLowMemory))
        .setBandwidthMeter(DefaultBandwidthMeter.Builder(context).build())
        .build()
}
```

#### **動態 LoadControl 配置**
```kotlin
private fun createOptimizedLoadControl(isLowMemory: Boolean): LoadControl {
    val allocator = DefaultAllocator(true, 
        if (isLowMemory) 1024 * 1024 else 2 * 1024 * 1024)
    
    return DefaultLoadControl.Builder()
        .setAllocator(allocator)
        .setBufferDurationsMs(
            if (isLowMemory) 5000 else 10000,  // 最小緩衝
            if (isLowMemory) 10000 else 30000, // 最大緩衝
            if (isLowMemory) 1000 else 2500,   // 播放緩衝
            if (isLowMemory) 2000 else 5000    // 重新緩衝
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}
```

### 2. 記憶體監控和自動清理

#### **播放狀態監控**
- **STATE_READY**：播放開始時檢查記憶體
- **STATE_ENDED**：播放結束時自動清理
- **STATE_BUFFERING**：緩衝期間監控記憶體壓力

#### **緊急記憶體清理**
```kotlin
private fun emergencyMemoryCleanup(context: Context) {
    // 強制垃圾回收
    System.gc()
    Thread.sleep(100)
    System.gc()
    
    // 清理 ExoPlayer 緩存
    clearExoPlayerCache()
    
    // 執行完整記憶體清理
    MemoryOptimizer.cleanupMemory(context)
}
```

### 3. 資源管理優化

#### **自動資源釋放**
```kotlin
fun releaseExoPlayer(player: ExoPlayer?) {
    player?.release()
    clearExoPlayerCache()
    optimizedLoadControl = null
}
```

#### **Fragment 生命週期整合**
在 `TrimFragment` 中：
```kotlin
override fun onDestroyView() {
    super.onDestroyView()
    ExoPlayerMemoryOptimizer.releaseExoPlayer(player)
    player = null
    _binding = null
}
```

## 實施的改進

### 1. 記憶體優化的 ExoPlayer 創建
- ✅ 根據記憶體狀態動態調整配置
- ✅ 低記憶體時自動執行垃圾回收
- ✅ 設置記憶體感知的播放器監聽器

### 2. 智能緩衝區管理
- ✅ 低記憶體時減少緩衝區大小
- ✅ 動態調整緩衝持續時間
- ✅ 優先時間而非大小的閾值設置

### 3. 自動記憶體監控
- ✅ 播放開始/結束時的記憶體檢查
- ✅ 緩衝期間的記憶體壓力監控
- ✅ OOM 檢測和緊急清理

### 4. 用戶界面增強
- ✅ 新增 "ExoPlayer 記憶體監控" 選項
- ✅ 實時顯示 ExoPlayer 記憶體統計
- ✅ 手動清理 ExoPlayer 緩存功能

## 預期效果

### 1. 記憶體使用優化
- **緩衝區大小**：低記憶體時從 2MB 降至 1MB
- **緩衝時間**：最小緩衝從 10秒降至 5秒
- **自動清理**：播放結束時自動釋放資源

### 2. OOM 預防
- **預防性檢查**：播放開始前檢查記憶體狀態
- **動態調整**：根據記憶體壓力調整配置
- **緊急處理**：檢測到 OOM 時立即執行清理

### 3. 用戶體驗改善
- **實時監控**：用戶可查看 ExoPlayer 記憶體狀態
- **手動控制**：提供手動清理緩存選項
- **詳細統計**：顯示緩存狀態和優化配置

## 測試建議

### 1. 大檔案測試
- 測試 300MB+ 影片載入
- 監控 ExoPlayer 記憶體使用
- 驗證自動清理功能

### 2. 記憶體壓力測試
- 在低記憶體環境下測試
- 驗證動態配置調整
- 檢查緊急清理效果

### 3. 長時間播放測試
- 連續播放多個大檔案
- 監控記憶體洩漏
- 驗證資源釋放

## 技術細節

### LoadControl 配置說明
- **DefaultAllocator**：控制內部緩衝區分配
- **BufferDurationsMs**：控制各種緩衝時間
- **PrioritizeTimeOverSizeThresholds**：優先時間而非大小

### 記憶體監控觸發點
- **播放開始**：檢查初始記憶體狀態
- **緩衝期間**：監控記憶體壓力
- **播放結束**：執行清理操作
- **錯誤發生**：緊急記憶體清理

### 與現有系統的整合
- **MemoryOptimizer**：利用現有記憶體管理功能
- **GuaranteedCrashReporter**：保持崩潰報告功能
- **CrashReportAnalyzer**：分析 ExoPlayer 相關崩潰

## 版本記錄

### v1.3.2: ExoPlayer 記憶體優化
- 新增 `ExoPlayerMemoryOptimizer` 工具類
- 實現記憶體感知的 ExoPlayer 配置
- 添加自動記憶體監控和清理
- 整合到 `TrimFragment` 和 `MainActivity`
- 新增 ExoPlayer 記憶體監控界面

這個解決方案專門針對 ExoPlayer 在大檔案處理時的記憶體問題，通過動態配置、自動監控和智能清理，有效預防 `ProgressiveMediaPeriod` 相關的 OOM 錯誤。
