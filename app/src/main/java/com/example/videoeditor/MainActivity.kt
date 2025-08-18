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
    }
    
    private fun setupViewPager() {
        binding.viewPager.adapter = VideoEditorPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false // 禁用滑動手勢
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
    
    companion object {
        private const val TAG = "MainActivity"
    }
}
