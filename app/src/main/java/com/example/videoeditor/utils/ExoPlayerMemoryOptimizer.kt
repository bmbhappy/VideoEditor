package com.example.videoeditor.utils

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

/**
 * ExoPlayer 記憶體優化器
 * 專門解決 ExoPlayer 在大檔案處理時的記憶體問題
 */
object ExoPlayerMemoryOptimizer {
    
    private const val TAG = "ExoPlayerMemoryOptimizer"
    
    // 記憶體優化的 ExoPlayer 配置
    private var optimizedLoadControl: LoadControl? = null
    private var mediaCache: Cache? = null
    
    /**
     * 創建記憶體優化的 ExoPlayer 實例
     */
    fun createOptimizedExoPlayer(context: Context): ExoPlayer {
        Log.d(TAG, "創建記憶體優化的 ExoPlayer")
        
        // 檢查記憶體狀態
        val memoryStatus = MemoryOptimizer.checkMemoryStatus(context)
        Log.d(TAG, "當前記憶體狀態: ${memoryStatus.getFormattedStatus()}")
        
        // 根據記憶體狀態調整配置
        val isLowMemory = MemoryOptimizer.isMemoryLow(context)
        
        return ExoPlayer.Builder(context)
            .setLoadControl(createOptimizedLoadControl(isLowMemory))
            .setBandwidthMeter(DefaultBandwidthMeter.Builder(context).build())
            .build()
            .apply {
                // 設置記憶體優化的播放器監聽器
                addListener(createMemoryAwareListener(context))
                
                // 如果記憶體不足，強制垃圾回收
                if (isLowMemory) {
                    Log.w(TAG, "記憶體不足，執行強制垃圾回收")
                    MemoryOptimizer.forceGarbageCollection()
                }
            }
    }
    
    /**
     * 創建記憶體優化的 LoadControl
     */
    private fun createOptimizedLoadControl(isLowMemory: Boolean): LoadControl {
        return if (optimizedLoadControl == null) {
            val allocator = DefaultAllocator(true, if (isLowMemory) 1024 * 1024 else 2 * 1024 * 1024)
            
            DefaultLoadControl.Builder()
                .setAllocator(allocator)
                .setBufferDurationsMs(
                    if (isLowMemory) 5000 else 10000,  // 最小緩衝
                    if (isLowMemory) 10000 else 30000, // 最大緩衝
                    if (isLowMemory) 1000 else 2500,   // 播放緩衝
                    if (isLowMemory) 2000 else 5000    // 重新緩衝
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
                .also { optimizedLoadControl = it }
        } else {
            optimizedLoadControl!!
        }
    }
    
    /**
     * 創建記憶體感知的播放器監聽器
     */
    private fun createMemoryAwareListener(context: Context): com.google.android.exoplayer2.Player.Listener {
        return object : com.google.android.exoplayer2.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    com.google.android.exoplayer2.Player.STATE_READY -> {
                        Log.d(TAG, "ExoPlayer 準備就緒")
                        // 播放開始時檢查記憶體
                        checkMemoryAfterPlaybackStart(context)
                    }
                    com.google.android.exoplayer2.Player.STATE_ENDED -> {
                        Log.d(TAG, "ExoPlayer 播放結束")
                        // 播放結束時清理記憶體
                        cleanupAfterPlaybackEnd(context)
                    }
                    com.google.android.exoplayer2.Player.STATE_BUFFERING -> {
                        Log.d(TAG, "ExoPlayer 緩衝中")
                        // 緩衝時檢查記憶體壓力
                        checkMemoryDuringBuffering(context)
                    }
                }
            }
            
            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                Log.e(TAG, "ExoPlayer 錯誤: ${error.message}")
                if (error.cause is OutOfMemoryError) {
                    Log.e(TAG, "檢測到 ExoPlayer OOM，執行緊急記憶體清理")
                    emergencyMemoryCleanup(context)
                }
            }
        }
    }
    
    /**
     * 播放開始後的記憶體檢查
     */
    private fun checkMemoryAfterPlaybackStart(context: Context) {
        try {
            val memoryStatus = MemoryOptimizer.checkMemoryStatus(context)
            Log.d(TAG, "播放開始後記憶體狀態: ${memoryStatus.getFormattedStatus()}")
            
            if (MemoryOptimizer.isMemoryLow(context)) {
                Log.w(TAG, "播放開始後記憶體不足，執行預防性清理")
                MemoryOptimizer.cleanupMemory(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放開始後記憶體檢查失敗: ${e.message}")
        }
    }
    
    /**
     * 播放結束後的記憶體清理
     */
    private fun cleanupAfterPlaybackEnd(context: Context) {
        try {
            Log.d(TAG, "播放結束，執行記憶體清理")
            MemoryOptimizer.cleanupMemory(context)
            
            // 清理 ExoPlayer 相關資源
            clearExoPlayerCache()
            
            Log.d(TAG, "播放結束後記憶體清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "播放結束後記憶體清理失敗: ${e.message}")
        }
    }
    
    /**
     * 緩衝期間的記憶體檢查
     */
    private fun checkMemoryDuringBuffering(context: Context) {
        try {
            val memoryStatus = MemoryOptimizer.checkMemoryStatus(context)
            val usedPercent = (memoryStatus.usedMemory.toFloat() / memoryStatus.totalMemory) * 100
            
            if (usedPercent > 80) {
                Log.w(TAG, "緩衝期間記憶體使用率過高: ${String.format("%.1f", usedPercent)}%")
                // 執行輕量級清理
                MemoryOptimizer.forceGarbageCollection()
            }
        } catch (e: Exception) {
            Log.e(TAG, "緩衝期間記憶體檢查失敗: ${e.message}")
        }
    }
    
    /**
     * 緊急記憶體清理
     */
    private fun emergencyMemoryCleanup(context: Context) {
        try {
            Log.w(TAG, "執行緊急記憶體清理")
            
            // 強制垃圾回收
            System.gc()
            Thread.sleep(100)
            System.gc()
            
            // 清理 ExoPlayer 緩存
            clearExoPlayerCache()
            
            // 執行完整記憶體清理
            MemoryOptimizer.cleanupMemory(context)
            
            Log.d(TAG, "緊急記憶體清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "緊急記憶體清理失敗: ${e.message}")
        }
    }
    
    /**
     * 清理 ExoPlayer 緩存
     */
    private fun clearExoPlayerCache() {
        try {
            mediaCache?.let { cache ->
                cache.release()
                mediaCache = null
                Log.d(TAG, "ExoPlayer 緩存已清理")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理 ExoPlayer 緩存失敗: ${e.message}")
        }
    }
    
    /**
     * 釋放 ExoPlayer 資源
     */
    fun releaseExoPlayer(player: ExoPlayer?) {
        try {
            player?.let { exoPlayer ->
                Log.d(TAG, "釋放 ExoPlayer 資源")
                exoPlayer.release()
            }
            
            // 清理緩存
            clearExoPlayerCache()
            
            // 重置 LoadControl
            optimizedLoadControl = null
            
            Log.d(TAG, "ExoPlayer 資源釋放完成")
        } catch (e: Exception) {
            Log.e(TAG, "釋放 ExoPlayer 資源失敗: ${e.message}")
        }
    }
    
    /**
     * 獲取 ExoPlayer 記憶體使用統計
     */
    fun getExoPlayerMemoryStats(context: Context): String {
        return try {
            val memoryStatus = MemoryOptimizer.checkMemoryStatus(context)
            val isLow = MemoryOptimizer.isMemoryLow(context)
            
            """
            ExoPlayer 記憶體統計:
            ${memoryStatus.getFormattedStatus()}
            記憶體狀態: ${if (isLow) "🔴 不足" else "🟢 正常"}
            緩存狀態: ${if (mediaCache != null) "已啟用" else "未啟用"}
            LoadControl: ${if (optimizedLoadControl != null) "已優化" else "未優化"}
            """.trimIndent()
        } catch (e: Exception) {
            "ExoPlayer 記憶體統計獲取失敗: ${e.message}"
        }
    }
}
