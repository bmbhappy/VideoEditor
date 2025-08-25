package com.example.videoeditor.utils

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * 記憶體優化器
 * 專門解決OutOfMemoryError問題
 */
object MemoryOptimizer {
    
    private const val TAG = "MemoryOptimizer"
    
    // 追蹤大型對象
    private val largeObjects = ConcurrentHashMap<String, WeakReference<Any>>()
    
    /**
     * 檢查記憶體狀態
     */
    fun checkMemoryStatus(context: Context): MemoryStatus {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        return MemoryStatus(
            totalMemory = maxMemory,
            usedMemory = usedMemory,
            availableMemory = availableMemory,
            memoryUsagePercent = (usedMemory * 100.0 / maxMemory).toInt(),
            isLowMemory = memoryInfo.lowMemory,
            threshold = memoryInfo.threshold
        )
    }
    
    /**
     * 強制垃圾回收
     */
    fun forceGarbageCollection() {
        try {
            Log.i(TAG, "開始強制垃圾回收...")
            System.gc()
            Thread.sleep(100) // 給GC一些時間
            System.gc() // 再次GC確保清理
            Log.i(TAG, "垃圾回收完成")
        } catch (e: Exception) {
            Log.e(TAG, "垃圾回收失敗", e)
        }
    }
    
    /**
     * 檢查是否記憶體不足
     */
    fun isMemoryLow(context: Context): Boolean {
        val status = checkMemoryStatus(context)
        return status.memoryUsagePercent > 85 || status.isLowMemory
    }
    
    /**
     * 清理記憶體
     */
    fun cleanupMemory(context: Context) {
        Log.i(TAG, "開始清理記憶體...")
        
        // 1. 強制垃圾回收
        forceGarbageCollection()
        
        // 2. 清理大型對象
        cleanupLargeObjects()
        
        // 3. 檢查清理結果
        val status = checkMemoryStatus(context)
        Log.i(TAG, "記憶體清理完成: 使用率 ${status.memoryUsagePercent}%, 可用 ${status.availableMemory / 1024 / 1024}MB")
    }
    
    /**
     * 註冊大型對象
     */
    fun registerLargeObject(key: String, obj: Any) {
        largeObjects[key] = WeakReference(obj)
        Log.d(TAG, "註冊大型對象: $key")
    }
    
    /**
     * 清理大型對象
     */
    private fun cleanupLargeObjects() {
        val iterator = largeObjects.iterator()
        var cleanedCount = 0
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val weakRef = entry.value
            
            if (weakRef.get() == null) {
                iterator.remove()
                cleanedCount++
                Log.d(TAG, "清理已回收的大型對象: ${entry.key}")
            }
        }
        
        Log.i(TAG, "清理了 $cleanedCount 個已回收的大型對象")
    }
    
    /**
     * 安全的Bitmap創建
     */
    fun createSafeBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.RGB_565): Bitmap? {
        return try {
            // 檢查記憶體是否足夠
            val requiredMemory = width * height * when (config) {
                Bitmap.Config.ARGB_8888 -> 4
                Bitmap.Config.RGB_565 -> 2
                Bitmap.Config.ARGB_4444 -> 2
                Bitmap.Config.ALPHA_8 -> 1
                else -> 4
            }
            
            Log.d(TAG, "嘗試創建Bitmap: ${width}x${height}, 需要記憶體: ${requiredMemory / 1024}KB")
            
            // 如果記憶體不足，先清理
            if (requiredMemory > 1024 * 1024) { // 大於1MB
                Log.w(TAG, "檢測到大Bitmap創建，先清理記憶體")
                forceGarbageCollection()
            }
            
            Bitmap.createBitmap(width, height, config)
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "創建Bitmap失敗，記憶體不足", e)
            
            // 嘗試清理記憶體後重試
            try {
                forceGarbageCollection()
                Thread.sleep(200)
                Bitmap.createBitmap(width, height, config)
            } catch (e2: OutOfMemoryError) {
                Log.e(TAG, "重試創建Bitmap仍然失敗", e2)
                null
            }
        }
    }
    
    /**
     * 安全的Bitmap載入
     */
    fun loadSafeBitmap(path: String, maxWidth: Int = 1024, maxHeight: Int = 1024): Bitmap? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            android.graphics.BitmapFactory.decodeFile(path, options)
            
            // 計算縮放比例
            var inSampleSize = 1
            while (options.outWidth / inSampleSize > maxWidth || options.outHeight / inSampleSize > maxHeight) {
                inSampleSize *= 2
            }
            
            // 重新載入縮放後的圖片
            val loadOptions = android.graphics.BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 使用較少的記憶體
            }
            
            Log.d(TAG, "載入Bitmap: $path, 縮放比例: $inSampleSize")
            android.graphics.BitmapFactory.decodeFile(path, loadOptions)
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "載入Bitmap失敗: $path", e)
            
            // 嘗試更激進的縮放
            try {
                val aggressiveOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 8 // 更激進的縮放
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                android.graphics.BitmapFactory.decodeFile(path, aggressiveOptions)
            } catch (e2: OutOfMemoryError) {
                Log.e(TAG, "激進縮放後仍然無法載入: $path", e2)
                null
            }
        }
    }
    
    /**
     * 記憶體狀態數據類
     */
    data class MemoryStatus(
        val totalMemory: Long,
        val usedMemory: Long,
        val availableMemory: Long,
        val memoryUsagePercent: Int,
        val isLowMemory: Boolean,
        val threshold: Long
    ) {
        fun getFormattedStatus(): String {
            return """
                記憶體狀態:
                總記憶體: ${totalMemory / 1024 / 1024}MB
                已使用: ${usedMemory / 1024 / 1024}MB
                可用: ${availableMemory / 1024 / 1024}MB
                使用率: ${memoryUsagePercent}%
                低記憶體警告: $isLowMemory
                閾值: ${threshold / 1024 / 1024}MB
            """.trimIndent()
        }
    }
    
    /**
     * 記憶體監控器
     */
    class MemoryMonitor(private val context: Context) {
        private var lastCheckTime = 0L
        private val checkInterval = 5000L // 5秒檢查一次
        
        fun shouldCheckMemory(): Boolean {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCheckTime > checkInterval) {
                lastCheckTime = currentTime
                return true
            }
            return false
        }
        
        fun checkAndCleanupIfNeeded() {
            if (shouldCheckMemory() && isMemoryLow(context)) {
                Log.w(TAG, "檢測到記憶體不足，開始清理...")
                cleanupMemory(context)
            }
        }
    }
}
