package com.example.videoeditor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.videoeditor.databinding.ActivityMainBinding
import com.example.videoeditor.fragments.*
import com.example.videoeditor.utils.PermissionHelper
import com.example.videoeditor.utils.VideoUtils
import com.example.videoeditor.utils.CrashReportManager
import com.example.videoeditor.utils.SimpleCrashReporter
import com.example.videoeditor.utils.UltraSimpleCrashReporter
import com.example.videoeditor.utils.UltraGuaranteedCrashReporter
import com.example.videoeditor.utils.CrashReportAnalyzer
import com.example.videoeditor.utils.MemoryOptimizer
import com.example.videoeditor.utils.ExoPlayerMemoryOptimizer
import com.example.test.TestRunner
import com.example.test.TestExample
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var currentVideoUri: Uri? = null
    private var currentVideoPath: String? = null
    
    private val permissionHelper = PermissionHelper(this)
    
    // 權限請求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "所有權限已授予")
            showFilePicker()
        } else {
            Log.e(TAG, "部分權限被拒絕")
            Toast.makeText(this, "需要儲存權限來存取影片檔案", Toast.LENGTH_LONG).show()
        }
    }
    
    // 檔案選擇
    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            currentVideoUri = it
            currentVideoPath = VideoUtils.getPathFromUri(this, it)
            Log.d(TAG, "選擇影片: $currentVideoPath")
            
            // 通知所有fragment影片已載入
            notifyVideoLoaded(it, currentVideoPath)
            
            // 不強制切換到裁剪fragment，保持在當前頁面
            Toast.makeText(this, "影片已載入", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 設置全局異常處理器
        setupGlobalExceptionHandler()
        
        // 檢查是否有未處理的崩潰報告
        checkForUnhandledCrashes()
        
        setupUI()
        setupViewPager()
        setupBottomNavigation()
        
        // 檢查權限並請求選擇影片
        if (permissionHelper.hasRequiredPermissions()) {
            showFilePicker()
        } else {
            requestPermissions()
        }
    }
    
    private fun setupUI() {
        // 設定狀態列顏色
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        
        // 設定工具列
        binding.toolbar.apply {
            setSupportActionBar(this)
            title = getString(R.string.app_name)
        }
        
        // 設定按鈕點擊事件
        binding.btnSelectVideo.setOnClickListener {
            if (permissionHelper.hasRequiredPermissions()) {
                showFilePicker()
            } else {
                requestPermissions()
            }
        }
        
        // 檔案管理器按鈕
        binding.btnFileManager.setOnClickListener {
            val intent = Intent(this, FileManagerActivity::class.java)
            startActivity(intent)
        }
        
        binding.btnLogDisplay.setOnClickListener {
            val intent = Intent(this, LogDisplayActivity::class.java)
            startActivity(intent)
        }
        
        // 添加崩潰報告按鈕（隱藏，長按啟用）
        binding.btnLogDisplay.setOnLongClickListener {
            showCrashReportMenu()
            true
        }
        
        // 添加測試按鈕（隱藏，長按啟用）
        binding.btnFileManager.setOnLongClickListener {
            showAudioTestMenu()
            true
        }
    }
    
    private fun setupViewPager() {
        binding.viewPager.adapter = VideoEditorPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false // 禁用滑動手勢
        
        // 強制創建所有Fragment以確保影片載入時能通知到所有Fragment
        binding.viewPager.offscreenPageLimit = 3 // 預載入所有頁面
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_trim -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_speed -> {
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_audio -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                R.id.nav_filter -> {
                    binding.viewPager.currentItem = 3
                    true
                }
                else -> false
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        
        requestPermissionLauncher.launch(permissions)
    }
    
    private fun showFilePicker() {
        pickVideoLauncher.launch("video/*")
    }
    
    private fun notifyVideoLoaded(uri: Uri, path: String?) {
        Log.d(TAG, "通知所有fragment影片已載入: $path")
        
        // 方法1：直接通知所有已創建的 fragments
        val fragments = supportFragmentManager.fragments
        fragments.forEach { fragment ->
            when (fragment) {
                is TrimFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知 TrimFragment")
                }
                is SpeedFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知 SpeedFragment")
                }
                is AudioFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知 AudioFragment")
                }
                is FilterFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知 FilterFragment")
                }
                else -> {
                    // 其他類型的 fragment，忽略
                    Log.d(TAG, "忽略其他類型的 fragment")
                }
            }
        }
        
        // 方法2：使用 ViewPager2 的 adapter 來獲取所有 fragments
        val adapter = binding.viewPager.adapter as? VideoEditorPagerAdapter
        if (adapter != null) {
            // 通知所有已創建的 fragments
            for (i in 0 until adapter.itemCount) {
                val fragment = supportFragmentManager.findFragmentByTag("f$i")
                when (fragment) {
                    is TrimFragment -> {
                        fragment.onVideoLoaded(uri, path)
                        Log.d(TAG, "已通知 TrimFragment (位置: $i)")
                    }
                    is SpeedFragment -> {
                        fragment.onVideoLoaded(uri, path)
                        Log.d(TAG, "已通知 SpeedFragment (位置: $i)")
                    }
                    is AudioFragment -> {
                        fragment.onVideoLoaded(uri, path)
                        Log.d(TAG, "已通知 AudioFragment (位置: $i)")
                    }
                    is FilterFragment -> {
                        fragment.onVideoLoaded(uri, path)
                        Log.d(TAG, "已通知 FilterFragment (位置: $i)")
                    }
                    else -> {
                        // 其他類型的 fragment，忽略
                        Log.d(TAG, "忽略其他類型的 fragment (位置: $i)")
                    }
                }
            }
        }
        
        // 方法3：也嘗試通知當前可見的 fragment
        val currentFragment = supportFragmentManager.fragments.find { it.isVisible }
        currentFragment?.let { fragment ->
            when (fragment) {
                is TrimFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知當前可見的 TrimFragment")
                }
                is SpeedFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知當前可見的 SpeedFragment")
                }
                is AudioFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知當前可見的 AudioFragment")
                }
                is FilterFragment -> {
                    fragment.onVideoLoaded(uri, path)
                    Log.d(TAG, "已通知當前可見的 FilterFragment")
                }
                else -> {
                    // 其他類型的 fragment，忽略
                    Log.d(TAG, "忽略其他類型的當前可見 fragment")
                }
            }
        }
        
        // 顯示成功訊息
        Toast.makeText(this, "影片已載入並應用到所有功能", Toast.LENGTH_SHORT).show()
    }
    
    inner class VideoEditorPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> TrimFragment()
                1 -> SpeedFragment()
                2 -> AudioFragment()
                3 -> FilterFragment()
                else -> TrimFragment()
            }
        }
    }
    
    // ==================== 音訊測試功能 ====================
    
    private fun showAudioTestMenu() {
        val options = arrayOf(
            "🚀 執行完整測試套件",
            "🎵 PCM 混音測試", 
            "🔊 音訊解碼測試",
            "📊 音訊品質檢查",
            "🏥 系統健康檢查",
            "⚡ 效能基準測試",
            "🎼 BGM 功能驗證測試"
        )
        
        android.app.AlertDialog.Builder(this)
            .setTitle("🧪 音訊測試系統")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> runFullAudioTest()
                    1 -> runPcmMixingTest()
                    2 -> runAudioDecodingTest()
                    3 -> runAudioQualityTest()
                    4 -> runHealthCheck()
                    5 -> runPerformanceBenchmark()
                    6 -> runBgmFunctionalityTest()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun runFullAudioTest() {
        Toast.makeText(this, "🚀 開始執行完整音訊測試套件...", Toast.LENGTH_SHORT).show()
        TestExample.runFullTestExample(this)
    }
    
    private fun runPcmMixingTest() {
        Toast.makeText(this, "🎵 開始 PCM 混音測試...", Toast.LENGTH_SHORT).show()
        val testRunner = TestRunner(this)
        testRunner.runSingleTest("PCM 混音測試", createTestCallback())
    }
    
    private fun runAudioDecodingTest() {
        Toast.makeText(this, "🔊 開始音訊解碼測試...", Toast.LENGTH_SHORT).show()
        val testRunner = TestRunner(this)
        testRunner.runSingleTest("音訊解碼測試", createTestCallback())
    }
    
    private fun runAudioQualityTest() {
        Toast.makeText(this, "📊 開始音訊品質檢查...", Toast.LENGTH_SHORT).show()
        val testRunner = TestRunner(this)
        testRunner.runSingleTest("音訊品質分析測試", createTestCallback())
    }
    
    private fun runHealthCheck() {
        Toast.makeText(this, "🏥 開始系統健康檢查...", Toast.LENGTH_SHORT).show()
        TestExample.audioHealthCheck(this)
    }
    
    private fun runPerformanceBenchmark() {
        Toast.makeText(this, "⚡ 開始效能基準測試...", Toast.LENGTH_SHORT).show()
        TestExample.performanceBenchmark(this)
    }
    
    private fun runBgmFunctionalityTest() {
        Toast.makeText(this, "🎼 開始 BGM 功能驗證測試...", Toast.LENGTH_SHORT).show()
        TestExample.bgmFunctionalityTest(this, createTestCallback())
    }
    
    private fun createTestCallback(): TestRunner.TestCallback {
        return object : TestRunner.TestCallback {
            override fun onTestStarted(testName: String) {
                Log.i(TAG, "🚀 測試開始: $testName")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "測試開始: $testName", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onTestCompleted(testName: String, success: Boolean, message: String) {
                val status = if (success) "✅ 成功" else "❌ 失敗"
                Log.i(TAG, "$status $testName: $message")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity, 
                        "$status $testName\n$message", 
                        if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                    ).show()
                }
            }
            
            override fun onTestSuiteCompleted(testSuite: com.example.test.AudioPipelineTester.TestSuite) {
                val successRate = String.format("%.1f", testSuite.successRate * 100)
                val summary = "📊 測試完成\n成功率: $successRate%\n成功: ${testSuite.successCount}/${testSuite.tests.size}\n耗時: ${testSuite.totalDuration}ms"
                Log.i(TAG, summary)
                
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("🎉 測試套件完成")
                        .setMessage(summary)
                        .setPositiveButton("查看詳細報告") { _, _ ->
                            showDetailedReport(testSuite)
                        }
                        .setNeutralButton("確定", null)
                        .show()
                }
            }
            
            override fun onError(error: String) {
                Log.e(TAG, "💥 測試錯誤: $error")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "測試錯誤: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showDetailedReport(testSuite: com.example.test.AudioPipelineTester.TestSuite) {
        val tester = com.example.test.AudioPipelineTester(this)
        val report = tester.generateTestReport(testSuite)
        
        android.app.AlertDialog.Builder(this)
            .setTitle("📋 詳細測試報告")
            .setMessage(report)
            .setPositiveButton("確定", null)
            .show()
    }
    
    // ==================== 崩潰報告功能 ====================
    
    /**
     * 設置全局異常處理器
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 1. 立即記錄到logcat（最可靠的方法）
                Log.e("ULTRA_CRASH_HANDLER", "=== 超強保證應用程式崩潰開始 ===")
                Log.e("GUARANTEED_CRASH_HANDLER", "時間: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                Log.e("GUARANTEED_CRASH_HANDLER", "異常類型: ${throwable.javaClass.simpleName}")
                Log.e("GUARANTEED_CRASH_HANDLER", "異常消息: ${throwable.message}")
                Log.e("GUARANTEED_CRASH_HANDLER", "堆疊追蹤:")
                throwable.printStackTrace()
                Log.e("ULTRA_CRASH_HANDLER", "=== 超強保證應用程式崩潰結束 ===")
                
                // 2. 立即寫入系統錯誤流（第二可靠的方法）
                System.err.println("=== ULTRA_CRASH_HANDLER_START ===")
                System.err.println("時間: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                System.err.println("異常類型: ${throwable.javaClass.simpleName}")
                System.err.println("異常消息: ${throwable.message}")
                System.err.println("堆疊追蹤:")
                throwable.printStackTrace(System.err)
                System.err.println("=== ULTRA_CRASH_HANDLER_END ===")
                System.err.flush()
                
                // 3. 使用超強保證崩潰報告器
                UltraGuaranteedCrashReporter.saveCrashReport(this@MainActivity, throwable)
                
                // 4. 強制刷新所有輸出流
                System.out.flush()
                System.err.flush()
                
                // 5. 等待更長時間確保文件寫入完成
                try {
                    Thread.sleep(3000) // 增加到3秒
                } catch (e: InterruptedException) {
                    // 忽略中斷異常
                }
                
            } catch (e: Exception) {
                Log.e("GUARANTEED_CRASH_HANDLER", "保存崩潰報告失敗", e)
                System.err.println("GUARANTEED_CRASH_HANDLER_FAILED: ${e.message}")
                System.err.flush()
                
                // 最後嘗試：直接寫入系統日誌
                try {
                    System.err.println("GUARANTEED_FINAL_CRASH_REPORT: ${throwable.javaClass.simpleName}: ${throwable.message}")
                    System.err.flush()
                } catch (finalEx: Exception) {
                    // 完全失敗
                }
            } finally {
                // 調用默認處理器
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
    
    /**
     * 保存崩潰報告（普通版本）
     */
    private fun saveCrashReport(title: String, throwable: Throwable) {
        try {
            CrashReportManager.saveCrashReport(this, title, throwable)
            Log.i(TAG, "崩潰報告已保存: $title")
        } catch (e: Exception) {
            Log.e(TAG, "保存崩潰報告失敗", e)
        }
    }
    
    /**
     * 立即保存崩潰報告（崩潰時使用，同步執行）
     */
    private fun saveCrashReportImmediately(title: String, throwable: Throwable) {
        try {
            // 直接使用最簡單的方式保存，確保在崩潰前完成
            val timestamp = System.currentTimeMillis()
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date(timestamp))
            
            // 生成簡單的崩潰報告
            val reportContent = """
                ========================================
                影片編輯器崩潰報告
                ========================================
                
                標題: $title
                時間: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}
                時間戳: $timestamp
                
                ========================================
                異常信息
                ========================================
                類型: ${throwable.javaClass.simpleName}
                消息: ${throwable.message ?: "無"}
                
                ========================================
                堆疊追蹤
                ========================================
                ${getStackTrace(throwable)}
                
                ========================================
                系統信息
                ========================================
                Android版本: ${android.os.Build.VERSION.RELEASE}
                API級別: ${android.os.Build.VERSION.SDK_INT}
                設備: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                
                ========================================
                報告結束
                ========================================
            """.trimIndent()
            
            // 使用最簡單的方式保存到內部存儲
            try {
                val crashDir = File(filesDir, "crash_reports")
                if (!crashDir.exists()) {
                    crashDir.mkdirs()
                }
                
                val file = File(crashDir, "crash_${dateStr}_${timestamp}.txt")
                file.writeText(reportContent)
                
                // 強制同步到磁盤
                try {
                    file.outputStream().use { it.fd.sync() }
                } catch (syncEx: Exception) {
                    Log.w(TAG, "同步失敗", syncEx)
                }
                
                Log.i(TAG, "崩潰報告已保存: ${file.absolutePath}")
                
                // 同時保存一個緊急版本
                val emergencyFile = File(filesDir, "emergency_crash_${timestamp}.txt")
                emergencyFile.writeText(reportContent)
                
                // 強制同步到磁盤
                try {
                    emergencyFile.outputStream().use { it.fd.sync() }
                } catch (syncEx: Exception) {
                    Log.w(TAG, "緊急同步失敗", syncEx)
                }
                
                Log.i(TAG, "緊急崩潰報告已保存: ${emergencyFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "保存到內部存儲失敗", e)
                
                // 最後嘗試：直接保存到filesDir根目錄
                try {
                    val lastResortFile = File(filesDir, "last_resort_crash_${timestamp}.txt")
                    lastResortFile.writeText("崩潰報告\n時間: ${java.util.Date()}\n異常: ${throwable.javaClass.simpleName}\n消息: ${throwable.message}")
                    
                    // 強制同步到磁盤
                    try {
                        lastResortFile.outputStream().use { it.fd.sync() }
                    } catch (syncEx: Exception) {
                        Log.w(TAG, "最後嘗試同步失敗", syncEx)
                    }
                    
                    Log.i(TAG, "最後嘗試保存成功: ${lastResortFile.absolutePath}")
                } catch (ex: Exception) {
                    Log.e(TAG, "最後嘗試也失敗", ex)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "緊急保存崩潰報告失敗", e)
        }
    }
    
    /**
     * 獲取堆疊追蹤
     */
    private fun getStackTrace(throwable: Throwable): String {
        val stringWriter = java.io.StringWriter()
        val printWriter = java.io.PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        return stringWriter.toString()
    }
    
    /**
     * 顯示崩潰報告菜單
     */
    private fun showCrashReportMenu() {
                    val options = arrayOf(
                "查看崩潰報告",
                "分析崩潰報告",
                "記憶體監控",
                "ExoPlayer 記憶體監控",
                "測試崩潰報告功能",
                "顯示調試信息",
                "模擬崩潰 (OOM)",
                "模擬崩潰 (NPE)",
                "模擬崩潰 (文件讀取錯誤)",
                "手動保存崩潰報告"
            )
        
        android.app.AlertDialog.Builder(this)
            .setTitle("🔧 崩潰報告功能")
            .setItems(options) { _, which ->
                                        when (which) {
                            0 -> openCrashReportActivity()
                            1 -> showCrashReportAnalysis()
                            2 -> showMemoryMonitor()
                            3 -> showExoPlayerMemoryMonitor()
                            4 -> testCrashReportFunctionality()
                            5 -> showCrashReportDebugInfo()
                            6 -> simulateOOMCrash()
                            7 -> simulateNPECrash()
                            8 -> simulateFileReadError()
                            9 -> manuallySaveCrashReport()
                        }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 打開崩潰報告Activity
     */
    private fun openCrashReportActivity() {
        val intent = Intent(this, CrashReportActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 測試崩潰報告功能
     */
    private fun testCrashReportFunctionality() {
        try {
            // 創建一個測試異常
            val testException = RuntimeException("這是一個保證成功的測試崩潰報告")
            
            // 使用超強保證崩潰報告器
            UltraGuaranteedCrashReporter.saveCrashReport(this, testException)
            
            Toast.makeText(this, "✅ 超強保證測試崩潰報告已保存", Toast.LENGTH_SHORT).show()
            
            // 顯示報告數量
            val reports = UltraGuaranteedCrashReporter.getAllCrashReports(this)
            Toast.makeText(this, "當前共有 ${reports.size} 個超強保證崩潰報告", Toast.LENGTH_SHORT).show()
            
            // 顯示調試信息
            showCrashReportDebugInfo()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ 測試失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 檢查是否有未處理的崩潰報告
     */
    private fun checkForUnhandledCrashes() {
        try {
            if (UltraGuaranteedCrashReporter.hasCrashReports(this)) {
                val reports = UltraGuaranteedCrashReporter.getAllCrashReports(this)
                Log.i(TAG, "發現 ${reports.size} 個超強保證崩潰報告")
                
                // 顯示通知
                Toast.makeText(this, "發現 ${reports.size} 個超強保證崩潰報告", Toast.LENGTH_LONG).show()
                
                // 自動顯示崩潰報告
                lifecycleScope.launch {
                    delay(1000) // 等待UI初始化
                    showCrashReportMenu()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "檢查崩潰報告失敗", e)
        }
    }
    
    /**
     * 顯示崩潰報告分析
     */
    private fun showCrashReportAnalysis() {
        try {
            val analysis = CrashReportAnalyzer.analyzeAllCrashReports(this)
            val statistics = CrashReportAnalyzer.getCrashStatistics(this)
            
            val fullAnalysis = """
$analysis

$statistics
            """.trimIndent()
            
            android.app.AlertDialog.Builder(this)
                .setTitle("🔍 崩潰報告分析")
                .setMessage(fullAnalysis)
                .setPositiveButton("複製到剪貼板") { _, _ ->
                    copyToClipboard(fullAnalysis)
                }
                .setNegativeButton("關閉", null)
                .show()
                
        } catch (e: Exception) {
            Toast.makeText(this, "分析失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 顯示記憶體監控
     */
        private fun showMemoryMonitor() {
        try {
            val status = MemoryOptimizer.checkMemoryStatus(this)
            val isLow = MemoryOptimizer.isMemoryLow(this)

            val memoryInfo = """
${status.getFormattedStatus()}

記憶體狀態: ${if (isLow) "🔴 不足" else "🟢 正常"}
建議操作: ${if (isLow) "立即清理記憶體" else "記憶體狀態良好"}
            """.trimIndent()

            android.app.AlertDialog.Builder(this)
                .setTitle("💾 記憶體監控")
                .setMessage(memoryInfo)
                .setPositiveButton("清理記憶體") { _, _ ->
                    MemoryOptimizer.cleanupMemory(this)
                    Toast.makeText(this, "記憶體清理完成", Toast.LENGTH_SHORT).show()
                    // 重新顯示記憶體狀態
                    lifecycleScope.launch {
                        delay(1000)
                        showMemoryMonitor()
                    }
                }
                .setNegativeButton("關閉", null)
                .setNeutralButton("複製信息") { _, _ ->
                    copyToClipboard(memoryInfo)
                }
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "記憶體監控失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showExoPlayerMemoryMonitor() {
        try {
            val exoPlayerStats = ExoPlayerMemoryOptimizer.getExoPlayerMemoryStats(this)

            android.app.AlertDialog.Builder(this)
                .setTitle("🎬 ExoPlayer 記憶體監控")
                .setMessage(exoPlayerStats)
                .setPositiveButton("清理 ExoPlayer 緩存") { _, _ ->
                    // 執行 ExoPlayer 相關清理
                    ExoPlayerMemoryOptimizer.releaseExoPlayer(null) // 清理緩存
                    MemoryOptimizer.cleanupMemory(this)
                    Toast.makeText(this, "ExoPlayer 緩存清理完成", Toast.LENGTH_SHORT).show()
                    // 重新顯示狀態
                    lifecycleScope.launch {
                        delay(1000)
                        showExoPlayerMemoryMonitor()
                    }
                }
                .setNegativeButton("關閉", null)
                .setNeutralButton("複製信息") { _, _ ->
                    copyToClipboard(exoPlayerStats)
                }
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "ExoPlayer 記憶體監控失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 複製文本到剪貼板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("崩潰報告分析", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已複製到剪貼板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "複製失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 顯示崩潰報告調試信息
     */
    private fun showCrashReportDebugInfo() {
        try {
            val reports = UltraGuaranteedCrashReporter.getAllCrashReports(this)
            val hasReports = UltraGuaranteedCrashReporter.hasCrashReports(this)
            
            val debugInfo = """
                超強保證崩潰報告調試信息:
                
                是否有崩潰報告: $hasReports
                崩潰報告數量: ${reports.size}
                
                崩潰報告列表:
                ${reports.joinToString("\n") { "  - ${it.name} (${it.length()} bytes) - ${it.absolutePath}" }}
                
                全局異常處理器狀態: 已設置
                啟動檢查狀態: 已啟用
                超強保證崩潰報告器: 已啟用
                
                檢查位置:
                - filesDir: ${filesDir.absolutePath}
                - filesDir/ultra_guaranteed_crash_reports: ${File(filesDir, "ultra_guaranteed_crash_reports").absolutePath}
                - getExternalFilesDir: ${getExternalFilesDir("ultra_guaranteed_crash_reports")?.absolutePath ?: "null"}
                - applicationInfo.dataDir: ${File(applicationInfo.dataDir, "ultra_guaranteed_crash_reports").absolutePath}
                - emergency_ultra_reports: ${File(filesDir, "emergency_ultra_reports").absolutePath}
                - last_resort_ultra_reports: ${File(filesDir, "last_resort_ultra_reports").absolutePath}
                - final_ultra_reports: ${File(filesDir, "final_ultra_reports").absolutePath}
                - ultra_crash_reports: ${File(getExternalFilesDir(null), "ultra_crash_reports").absolutePath}
            """.trimIndent()
            
            android.app.AlertDialog.Builder(this)
                .setTitle("🔍 超強保證崩潰報告調試信息")
                .setMessage(debugInfo)
                .setPositiveButton("確定", null)
                .show()
                
        } catch (e: Exception) {
            Toast.makeText(this, "調試信息顯示失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 模擬OutOfMemoryError
     */
    private fun simulateOOMCrash() {
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠️ 警告")
            .setMessage("這將模擬OutOfMemoryError崩潰，應用程式會重啟。確定要繼續嗎？")
            .setPositiveButton("確定") { _, _ ->
                try {
                    // 嘗試分配大量記憶體來觸發OOM
                    val list = mutableListOf<ByteArray>()
                    while (true) {
                        list.add(ByteArray(1024 * 1024)) // 1MB
                    }
                } catch (e: OutOfMemoryError) {
                    // 這個異常會被全局處理器捕獲
                    throw e
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 模擬NullPointerException
     */
    private fun simulateNPECrash() {
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠️ 警告")
            .setMessage("這將模擬NullPointerException崩潰，應用程式會重啟。確定要繼續嗎？")
            .setPositiveButton("確定") { _, _ ->
                val nullString: String? = null
                nullString!!.length // 這會觸發NPE
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 模擬文件讀取錯誤
     */
    private fun simulateFileReadError() {
        android.app.AlertDialog.Builder(this)
            .setTitle("⚠️ 警告")
            .setMessage("這將模擬文件讀取錯誤崩潰，應用程式會重啟。確定要繼續嗎？")
            .setPositiveButton("確定") { _, _ ->
                val nonExistentFile = File("/non/existent/file.txt")
                nonExistentFile.readText() // 這會觸發FileNotFoundException
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 手動保存崩潰報告
     */
    private fun manuallySaveCrashReport() {
        val testException = Exception("手動保存的測試崩潰報告")
        saveCrashReport("手動測試崩潰", testException)
        Toast.makeText(this, "✅ 手動崩潰報告已保存", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 檢查是否是異常退出
        if (!isFinishing && !isChangingConfigurations) {
            // 可能是崩潰導致的退出
            try {
                val crashFile = File(filesDir, "suspicious_exit_${System.currentTimeMillis()}.txt")
                crashFile.writeText("""
                    可疑的應用程式退出
                    時間: ${java.util.Date()}
                    是否正常結束: false
                    是否配置變更: $isChangingConfigurations
                    是否正在結束: $isFinishing
                """.trimIndent())
                Log.i(TAG, "記錄可疑退出: ${crashFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "記錄可疑退出失敗", e)
            }
        }
        
        // 清理資源
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}
