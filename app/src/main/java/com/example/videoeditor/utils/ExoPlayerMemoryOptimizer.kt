package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

/**
 * ExoPlayer 記憶體優化器 - 基於 OutOfMemoryError 分析優化
 * 專門解決 ExoPlayer 在處理大檔案時的記憶體問題
 */
object ExoPlayerMemoryOptimizer {
    private const val TAG = "ExoPlayerMemoryOptimizer"
    
    // 記憶體緩衝區大小配置
    private const val NORMAL_BUFFER_SIZE = 1024 * 1024 // 1MB
    private const val LOW_MEMORY_BUFFER_SIZE = 512 * 1024 // 512KB
    private const val ULTRA_LOW_MEMORY_BUFFER_SIZE = 256 * 1024 // 256KB
    
    // 緩衝時間配置
    private const val NORMAL_MIN_BUFFER_MS = 15000 // 15秒
    private const val NORMAL_MAX_BUFFER_MS = 30000 // 30秒
    private const val LOW_MEMORY_MIN_BUFFER_MS = 5000 // 5秒
    private const val LOW_MEMORY_MAX_BUFFER_MS = 10000 // 10秒
    private const val ULTRA_LOW_MEMORY_MIN_BUFFER_MS = 2000 // 2秒
    private const val ULTRA_LOW_MEMORY_MAX_BUFFER_MS = 5000 // 5秒
    
    // 播放緩衝配置
    private const val NORMAL_BUFFER_FOR_PLAYBACK_MS = 2500 // 2.5秒
    private const val LOW_MEMORY_BUFFER_FOR_PLAYBACK_MS = 1000 // 1秒
    private const val ULTRA_LOW_MEMORY_BUFFER_FOR_PLAYBACK_MS = 500 // 0.5秒
    
    // 重新緩衝配置
    private const val NORMAL_REBUFFER_WHEN_STALLED_MS = 5000 // 5秒
    private const val LOW_MEMORY_REBUFFER_WHEN_STALLED_MS = 2000 // 2秒
    private const val ULTRA_LOW_MEMORY_REBUFFER_WHEN_STALLED_MS = 1000 // 1秒
    
    private var mediaCache: Cache? = null
    private var optimizedLoadControl: LoadControl? = null
    
    /**
     * 創建優化的 ExoPlayer 實例
     * 根據當前記憶體狀態動態調整配置
     */
    fun createOptimizedExoPlayer(context: Context): ExoPlayer {
        val memoryStatus = getMemoryStatus(context)
        Log.d(TAG, "創建 ExoPlayer，記憶體狀態: $memoryStatus")
        
        // 強制垃圾回收
        if (memoryStatus.isLowMemory) {
            System.gc()
            Log.d(TAG, "低記憶體狀態，執行強制垃圾回收")
        }
        
        val loadControl = createOptimizedLoadControl(memoryStatus)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .build()
            .apply {
                addListener(createMemoryAwareListener(context))
                Log.d(TAG, "ExoPlayer 創建完成，使用優化配置")
            }
    }
    
    /**
     * 創建優化的 LoadControl
     * 根據記憶體狀態動態調整緩衝區大小和時間
     */
    private fun createOptimizedLoadControl(memoryStatus: MemoryStatus): LoadControl {
        val (bufferSize, minBufferMs, maxBufferMs, bufferForPlaybackMs, rebufferWhenStalledMs) = 
            when {
                memoryStatus.isUltraLowMemory -> {
                    Log.d(TAG, "使用超低記憶體配置")
                    UltraLowMemoryConfig
                }
                memoryStatus.isLowMemory -> {
                    Log.d(TAG, "使用低記憶體配置")
                    LowMemoryConfig
                }
                else -> {
                    Log.d(TAG, "使用正常記憶體配置")
                    NormalConfig
                }
            }
        
        val allocator = DefaultAllocator(true, bufferSize)
        
        return DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                rebufferWhenStalledMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
    
    /**
     * 創建記憶體感知的監聽器
     */
    private fun createMemoryAwareListener(context: Context): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        Log.d(TAG, "播放器準備就緒，執行主動記憶體清理")
                        proactiveMemoryCleanup(context)
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "播放結束，執行完整記憶體清理")
                        fullMemoryCleanup(context)
                    }
                    Player.STATE_BUFFERING -> {
                        val memoryStatus = getMemoryStatus(context)
                        if (memoryStatus.usedMemoryPercent > 80) {
                            Log.w(TAG, "緩衝中且記憶體使用率高，執行輕量級清理")
                            lightweightMemoryCleanup()
                        }
                    }
                }
            }
            

        }
    }
    
    /**
     * 主動記憶體清理
     */
    private fun proactiveMemoryCleanup(context: Context) {
        try {
            val memoryStatus = getMemoryStatus(context)
            if (memoryStatus.usedMemoryPercent > 70) {
                Log.d(TAG, "記憶體使用率超過70%，執行主動清理")
                System.gc()
                clearExoPlayerCache()
            }
        } catch (e: Exception) {
            Log.e(TAG, "主動記憶體清理失敗", e)
        }
    }
    
    /**
     * 完整記憶體清理
     */
    private fun fullMemoryCleanup(context: Context) {
        try {
            Log.d(TAG, "執行完整記憶體清理")
            System.gc()
            clearExoPlayerCache()
            MemoryOptimizer.cleanupMemory(context)
        } catch (e: Exception) {
            Log.e(TAG, "完整記憶體清理失敗", e)
        }
    }
    
    /**
     * 輕量級記憶體清理
     */
    private fun lightweightMemoryCleanup() {
        try {
            Log.d(TAG, "執行輕量級記憶體清理")
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "輕量級記憶體清理失敗", e)
        }
    }
    
    /**
     * 緊急記憶體清理
     */
    fun emergencyMemoryCleanup(context: Context) {
        try {
            Log.e(TAG, "執行緊急記憶體清理")
            
            // 強制垃圾回收
            System.gc()
            System.runFinalization()
            
            // 清理 ExoPlayer 緩存
            clearExoPlayerCache()
            
            // 調用通用記憶體優化器
            MemoryOptimizer.cleanupMemory(context)
            
            // 再次強制垃圾回收
            System.gc()
            
            Log.d(TAG, "緊急記憶體清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "緊急記憶體清理失敗", e)
        }
    }
    
    /**
     * 清理 ExoPlayer 緩存
     */
    fun clearExoPlayerCache() {
        try {
            mediaCache?.release()
            mediaCache = null
            optimizedLoadControl = null
            Log.d(TAG, "ExoPlayer 緩存清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理 ExoPlayer 緩存失敗", e)
        }
    }
    
    /**
     * 釋放 ExoPlayer 實例
     */
    fun releaseExoPlayer(player: ExoPlayer?) {
        try {
            player?.release()
            clearExoPlayerCache()
            optimizedLoadControl = null
            Log.d(TAG, "ExoPlayer 釋放完成")
        } catch (e: Exception) {
            Log.e(TAG, "釋放 ExoPlayer 失敗", e)
        }
    }
    
    /**
     * 獲取記憶體狀態
     */
    private fun getMemoryStatus(context: Context): MemoryStatus {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usedMemoryPercent = (usedMemory.toFloat() / maxMemory * 100).toInt()
        
        val isLowMemory = usedMemoryPercent > 70
        val isUltraLowMemory = usedMemoryPercent > 85
        
        return MemoryStatus(
            maxMemory = maxMemory,
            totalMemory = totalMemory,
            freeMemory = freeMemory,
            usedMemory = usedMemory,
            usedMemoryPercent = usedMemoryPercent,
            isLowMemory = isLowMemory,
            isUltraLowMemory = isUltraLowMemory
        )
    }
    
    /**
     * 獲取 ExoPlayer 記憶體統計信息
     */
    fun getExoPlayerMemoryStats(context: Context): String {
        val memoryStatus = getMemoryStatus(context)
        val cacheInfo = if (mediaCache != null) "已初始化" else "未初始化"
        
        return """
            🎬 ExoPlayer 記憶體監控
            
            📊 記憶體狀態:
            • 最大記憶體: ${memoryStatus.maxMemory / (1024 * 1024)}MB
            • 已使用記憶體: ${memoryStatus.usedMemory / (1024 * 1024)}MB
            • 可用記憶體: ${memoryStatus.freeMemory / (1024 * 1024)}MB
            • 使用率: ${memoryStatus.usedMemoryPercent}%
            • 記憶體狀態: ${getMemoryStatusText(memoryStatus)}
            
            🎯 ExoPlayer 配置:
            • 緩存狀態: $cacheInfo
            • 緩衝區大小: ${getCurrentBufferSize(memoryStatus)}KB
            • 最小緩衝時間: ${getCurrentMinBufferMs(memoryStatus)}ms
            • 最大緩衝時間: ${getCurrentMaxBufferMs(memoryStatus)}ms
            
            ⚠️ 建議操作:
            ${getMemoryRecommendations(memoryStatus)}
        """.trimIndent()
    }
    
    private fun getMemoryStatusText(status: MemoryStatus): String {
        return when {
            status.isUltraLowMemory -> "⚠️ 超低記憶體 (${status.usedMemoryPercent}%)"
            status.isLowMemory -> "⚠️ 低記憶體 (${status.usedMemoryPercent}%)"
            else -> "✅ 正常 (${status.usedMemoryPercent}%)"
        }
    }
    
    private fun getCurrentBufferSize(status: MemoryStatus): Int {
        return when {
            status.isUltraLowMemory -> ULTRA_LOW_MEMORY_BUFFER_SIZE / 1024
            status.isLowMemory -> LOW_MEMORY_BUFFER_SIZE / 1024
            else -> NORMAL_BUFFER_SIZE / 1024
        }
    }
    
    private fun getCurrentMinBufferMs(status: MemoryStatus): Int {
        return when {
            status.isUltraLowMemory -> ULTRA_LOW_MEMORY_MIN_BUFFER_MS
            status.isLowMemory -> LOW_MEMORY_MIN_BUFFER_MS
            else -> NORMAL_MIN_BUFFER_MS
        }
    }
    
    private fun getCurrentMaxBufferMs(status: MemoryStatus): Int {
        return when {
            status.isUltraLowMemory -> ULTRA_LOW_MEMORY_MAX_BUFFER_MS
            status.isLowMemory -> LOW_MEMORY_MAX_BUFFER_MS
            else -> NORMAL_MAX_BUFFER_MS
        }
    }
    
    private fun getMemoryRecommendations(status: MemoryStatus): String {
        return when {
            status.isUltraLowMemory -> "• 立即清理記憶體\n• 避免載入大檔案\n• 考慮重啟應用"
            status.isLowMemory -> "• 建議清理記憶體\n• 監控記憶體使用\n• 避免同時處理多個檔案"
            else -> "• 記憶體狀態良好\n• 可以正常處理檔案"
        }
    }
    
    // 配置常量
    private val NormalConfig = MemoryConfig(
        bufferSize = NORMAL_BUFFER_SIZE,
        minBufferMs = NORMAL_MIN_BUFFER_MS,
        maxBufferMs = NORMAL_MAX_BUFFER_MS,
        bufferForPlaybackMs = NORMAL_BUFFER_FOR_PLAYBACK_MS,
        rebufferWhenStalledMs = NORMAL_REBUFFER_WHEN_STALLED_MS
    )
    
    private val LowMemoryConfig = MemoryConfig(
        bufferSize = LOW_MEMORY_BUFFER_SIZE,
        minBufferMs = LOW_MEMORY_MIN_BUFFER_MS,
        maxBufferMs = LOW_MEMORY_MAX_BUFFER_MS,
        bufferForPlaybackMs = LOW_MEMORY_BUFFER_FOR_PLAYBACK_MS,
        rebufferWhenStalledMs = LOW_MEMORY_REBUFFER_WHEN_STALLED_MS
    )
    
    private val UltraLowMemoryConfig = MemoryConfig(
        bufferSize = ULTRA_LOW_MEMORY_BUFFER_SIZE,
        minBufferMs = ULTRA_LOW_MEMORY_MIN_BUFFER_MS,
        maxBufferMs = ULTRA_LOW_MEMORY_MAX_BUFFER_MS,
        bufferForPlaybackMs = ULTRA_LOW_MEMORY_BUFFER_FOR_PLAYBACK_MS,
        rebufferWhenStalledMs = ULTRA_LOW_MEMORY_REBUFFER_WHEN_STALLED_MS
    )
    
    data class MemoryStatus(
        val maxMemory: Long,
        val totalMemory: Long,
        val freeMemory: Long,
        val usedMemory: Long,
        val usedMemoryPercent: Int,
        val isLowMemory: Boolean,
        val isUltraLowMemory: Boolean
    )
    
    private data class MemoryConfig(
        val bufferSize: Int,
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val rebufferWhenStalledMs: Int
    )
}
