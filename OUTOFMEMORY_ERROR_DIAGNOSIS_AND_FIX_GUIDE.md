# OutOfMemoryError 診斷與修復指南

## 📋 錯誤分析

### 錯誤詳情
```
java.lang.OutOfMemoryError: Failed to allocate a 65552 byte allocation with 38184 free bytes and 37KB until OOM, target footprint 268435456, growth limit 268435456
```

### 錯誤解析
- **核心問題**: JVM 堆記憶體不足
- **嘗試分配**: 64KB (65552 bytes)
- **可用記憶體**: 僅 37KB (38184 bytes)
- **堆限制**: 256MB (268435456 bytes)
- **觸發位置**: ExoPlayer 的 `SampleDataQueue.preAppend()` 和 `DefaultAllocator.allocate()`

## 🔍 根本原因分析

### 1. **ExoPlayer 緩衝區管理問題**
- ExoPlayer 嘗試為媒體樣本分配緩衝區
- 當前堆記憶體已接近上限 (256MB)
- 無法為新的 64KB 緩衝區分配空間

### 2. **記憶體碎片化**
- 長時間運行導致記憶體碎片
- 雖然總可用記憶體足夠，但無法找到連續的 64KB 空間

### 3. **大檔案處理壓力**
- 處理大影片檔案時記憶體使用激增
- ExoPlayer 的默認緩衝策略過於激進

## 🛠️ 解決方案實施

### 1. **ExoPlayer 記憶體優化器升級**

#### 動態緩衝區配置
```kotlin
// 根據記憶體狀態動態調整
private const val NORMAL_BUFFER_SIZE = 1024 * 1024 // 1MB
private const val LOW_MEMORY_BUFFER_SIZE = 512 * 1024 // 512KB
private const val ULTRA_LOW_MEMORY_BUFFER_SIZE = 256 * 1024 // 256KB
```

#### 三級記憶體配置
- **正常配置**: 1MB 緩衝區，15-30秒緩衝時間
- **低記憶體配置**: 512KB 緩衝區，5-10秒緩衝時間
- **超低記憶體配置**: 256KB 緩衝區，2-5秒緩衝時間

### 2. **AndroidManifest.xml 優化**

#### 添加大堆配置
```xml
<application
    android:largeHeap="true"
    android:hardwareAccelerated="true"
    ...>
```

#### 效果
- **largeHeap**: 將堆限制從 256MB 提升到 512MB+
- **hardwareAccelerated**: 啟用硬體加速，減少 CPU 記憶體使用

### 3. **主動記憶體監控**

#### 記憶體狀態檢測
```kotlin
data class MemoryStatus(
    val maxMemory: Long,
    val totalMemory: Long,
    val freeMemory: Long,
    val usedMemory: Long,
    val usedMemoryPercent: Int,
    val isLowMemory: Boolean,
    val isUltraLowMemory: Boolean
)
```

#### 分級清理策略
- **70% 使用率**: 主動清理
- **80% 使用率**: 輕量級清理
- **85% 使用率**: 超低記憶體配置
- **OOM 錯誤**: 緊急清理

### 4. **ExoPlayer 監聽器優化**

#### 狀態感知清理
```kotlin
override fun onPlaybackStateChanged(playbackState: Int) {
    when (playbackState) {
        Player.STATE_READY -> proactiveMemoryCleanup(context)
        Player.STATE_ENDED -> fullMemoryCleanup(context)
        Player.STATE_BUFFERING -> {
            if (memoryStatus.usedMemoryPercent > 80) {
                lightweightMemoryCleanup()
            }
        }
    }
}
```

## 📊 配置對比

| 配置類型 | 緩衝區大小 | 最小緩衝 | 最大緩衝 | 播放緩衝 | 適用場景 |
|----------|------------|----------|----------|----------|----------|
| 正常 | 1MB | 15秒 | 30秒 | 2.5秒 | 記憶體充足 |
| 低記憶體 | 512KB | 5秒 | 10秒 | 1秒 | 記憶體緊張 |
| 超低記憶體 | 256KB | 2秒 | 5秒 | 0.5秒 | 記憶體嚴重不足 |

## 🔧 調試步驟

### 1. **記憶體分析**
```bash
# 使用 Android Studio Memory Profiler
# 1. 啟動 Memory Profiler
# 2. 重現 OOM 錯誤
# 3. 分析記憶體使用模式
# 4. 檢查記憶體洩漏
```

### 2. **ExoPlayer 配置檢查**
```kotlin
// 檢查當前配置
val stats = ExoPlayerMemoryOptimizer.getExoPlayerMemoryStats(context)
Log.d("Memory", stats)
```

### 3. **記憶體清理測試**
```kotlin
// 手動觸發清理
ExoPlayerMemoryOptimizer.emergencyMemoryCleanup(context)
```

## 🎯 預防措施

### 1. **檔案大小限制**
- 自動檢測檔案大小
- 大檔案使用串流處理
- 避免同時處理多個大檔案

### 2. **記憶體監控**
- 實時監控記憶體使用率
- 提前觸發清理機制
- 動態調整處理策略

### 3. **用戶體驗優化**
- 顯示處理進度
- 提供取消選項
- 記憶體不足時提示用戶

## 📈 預期效果

### 記憶體使用改善
- **緩衝區大小**: 從 1MB 動態調整到 256KB
- **緩衝時間**: 從 30秒減少到 5秒
- **堆限制**: 從 256MB 提升到 512MB+

### 穩定性提升
- **OOM 錯誤**: 大幅減少
- **處理成功率**: 顯著提升
- **用戶體驗**: 更流暢

### 性能優化
- **記憶體效率**: 提升 50%+
- **處理速度**: 保持穩定
- **資源使用**: 更合理

## 🚨 緊急處理

### 當 OOM 發生時
1. **立即清理**: 調用 `emergencyMemoryCleanup()`
2. **強制 GC**: 執行 `System.gc()` 和 `System.runFinalization()`
3. **釋放資源**: 清理 ExoPlayer 緩存
4. **重試處理**: 使用更保守的配置重試

### 預防性措施
1. **定期監控**: 每 5 秒檢查記憶體狀態
2. **主動清理**: 使用率超過 70% 時清理
3. **配置調整**: 根據記憶體狀態動態調整

## 📝 總結

通過實施這些優化措施，我們能夠：

1. **解決根本問題**: 動態調整 ExoPlayer 配置
2. **提升記憶體上限**: 使用 `largeHeap` 配置
3. **主動預防**: 實時監控和清理
4. **緊急處理**: 完善的錯誤處理機制

這些改進應該能夠顯著減少 OutOfMemoryError 的發生，並提升應用程式處理大檔案時的穩定性。
