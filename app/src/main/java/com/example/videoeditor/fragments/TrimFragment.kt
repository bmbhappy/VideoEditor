package com.example.videoeditor.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.videoeditor.R
import com.example.videoeditor.databinding.FragmentTrimBinding
import com.example.videoeditor.engine.VideoProcessor
import com.example.videoeditor.utils.VideoUtils
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.material.slider.RangeSlider
import kotlinx.coroutines.launch
import java.io.File

class TrimFragment : Fragment() {
    
    private var _binding: FragmentTrimBinding? = null
    private val binding get() = _binding!!
    
    private var videoUri: Uri? = null
    private var videoPath: String? = null
    private var videoDuration: Long = 0L
    
    private var player: ExoPlayer? = null
    private var videoProcessor: VideoProcessor? = null
    
    // 裁剪參數
    private var startTimeMs: Long = 0L
    private var endTimeMs: Long = 0L
    
    // 防止重複更新
    private var isUpdatingTrimBar = false
    
    companion object {
        private const val TAG = "TrimFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrimBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupPlayer()
        setupTrimBar()
        setupButtons()
        
        videoProcessor = VideoProcessor(requireContext())
    }
    
    private fun setupUI() {
        // 移除播放按鈕相關設置
    }
    
    private fun setupPlayer() {
        player = ExoPlayer.Builder(requireContext()).build().apply {
            binding.playerView.player = this
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d(TAG, "播放器準備就緒")
                            if (videoDuration == 0L) {
                                updateTrimBar()
                            }

                        }
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "播放結束")
                        }
                    }
                }
            })
        }
    }
    
    private fun setupTrimBar() {
        // 設定拖拉事件
        binding.trimBar.addOnChangeListener { slider, value, fromUser ->
            if (fromUser && videoDuration > 0 && !isUpdatingTrimBar) {
                val values = slider.values
                if (values.size >= 2) {
                    val leftPercent = values[0] / 100f
                    val rightPercent = values[1] / 100f
                    
                    startTimeMs = (videoDuration * leftPercent).toLong()
                    endTimeMs = (videoDuration * rightPercent).toLong()
                    
                    updateTimeDisplay()
                    seekToTime(startTimeMs)
                    
                    Log.d(TAG, "裁剪範圍: ${VideoUtils.formatDuration(startTimeMs)} - ${VideoUtils.formatDuration(endTimeMs)}")
                    

                }
            }
        }
        

        
        // 防止與 ViewPager2 手勢衝突
        binding.trimBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 阻止父容器處理觸摸事件
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    // 向上傳遞到 ViewPager2 的父容器
                    v.parent?.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    // 持續阻止父容器處理觸摸事件
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    v.parent?.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢復父容器的觸摸事件處理
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    v.parent?.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false // 讓 RangeSlider 自己處理觸摸事件
        }
        
        // 設定觸摸區域
        binding.trimBar.setOnClickListener {
            // 防止點擊事件觸發 ViewPager2
            it.parent?.requestDisallowInterceptTouchEvent(true)
        }
    }
    
    private fun setupButtons() {
        // 設定重置和確定按鈕
        binding.btnReset.setOnClickListener {
            // 重置裁剪範圍
            resetTrimRange()
        }
        
        binding.btnApply.setOnClickListener {
            if (videoUri != null) {
                performTrim()
            } else {
                Toast.makeText(context, "請先選擇影片", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun resetTrimRange() {
        startTimeMs = 0L
        endTimeMs = videoDuration
        updateTrimBar()
        updateTimeDisplay()
    }
    
    private fun updateTrimBar() {
        if (videoDuration > 0) {
            isUpdatingTrimBar = true
            binding.trimBar.setValues(0f, 100f)
            startTimeMs = 0L
            endTimeMs = videoDuration
            updateTimeDisplay()
            isUpdatingTrimBar = false
        }
    }
    
    private fun updateTimeDisplay() {
        binding.tvStartTime.text = VideoUtils.formatDuration(startTimeMs)
        binding.tvEndTime.text = VideoUtils.formatDuration(endTimeMs)
        binding.tvDuration.text = VideoUtils.formatDuration(endTimeMs - startTimeMs)
    }
    
    private fun seekToTime(timeMs: Long) {
        player?.seekTo(timeMs)
    }
    

    
    private fun performTrim() {
        if (startTimeMs >= endTimeMs) {
            Toast.makeText(context, "開始時間必須小於結束時間", Toast.LENGTH_SHORT).show()
            return
        }
        
        val trimDuration = endTimeMs - startTimeMs
        if (trimDuration < 1000) { // 至少1秒
            Toast.makeText(context, "裁剪片段至少需要1秒", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "開始裁剪: ${VideoUtils.formatDuration(startTimeMs)} - ${VideoUtils.formatDuration(endTimeMs)}")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnApply.isEnabled = false
        
        lifecycleScope.launch {
            videoProcessor?.trimVideo(
                videoUri!!,
                startTimeMs,
                endTimeMs,
                object : VideoProcessor.ProcessingCallback {
                    override fun onProgress(progress: Float) {
                        Log.d(TAG, "裁剪進度: $progress%")
                    }
                    
                    override fun onSuccess(outputPath: String) {
                        Log.d(TAG, "裁剪成功: $outputPath")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            
                            // 顯示成功訊息並提供選項
                            val options = arrayOf("查看檔案", "分享檔案", "保存到相簿", "確定")
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("裁剪完成")
                                .setMessage("影片裁剪成功！\n檔案已保存到應用程式內部儲存空間。")
                                .setItems(options) { _, which ->
                                    when (which) {
                                        0 -> {
                                            // 跳轉到檔案管理器
                                            val intent = Intent(requireContext(), com.example.videoeditor.FileManagerActivity::class.java)
                                            startActivity(intent)
                                        }
                                        1 -> {
                                            // 分享檔案
                                            shareFile(outputPath)
                                        }
                                        2 -> {
                                            // 保存到相簿
                                            saveToGallery(outputPath)
                                        }
                                        3 -> {
                                            // 關閉對話框
                                        }
                                    }
                                }
                                .show()
                        }
                    }
                    
                    override fun onError(error: String) {
                        Log.e(TAG, "裁剪失敗: $error")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            Toast.makeText(context, "裁剪失敗: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
    
    private fun shareFile(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    "com.example.videoeditor.fileprovider",
                    file
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "分享影片")
                    putExtra(Intent.EXTRA_TEXT, "分享影片")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 檢查是否有可用的分享應用
                val resolveInfo = requireContext().packageManager.resolveActivity(shareIntent, 0)
                if (resolveInfo != null) {
                    startActivity(Intent.createChooser(shareIntent, "分享影片"))
                    Log.d(TAG, "開始分享影片")
                } else {
                    Toast.makeText(context, "沒有可用的分享應用", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "檔案不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享檔案失敗: ${e.message}")
            Toast.makeText(context, "分享失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveToGallery(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val success = com.example.videoeditor.utils.GalleryUtils.saveVideoToGallery(
                    requireContext(),
                    filePath,
                    file.name
                )
                
                if (success) {
                    Toast.makeText(context, "影片已保存到相簿", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "影片已保存到相簿: $filePath")
                } else {
                    Toast.makeText(context, "保存到相簿失敗", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "檔案不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存到相簿失敗: ${e.message}")
            Toast.makeText(context, "保存失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
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
        
        // 更新UI
        updateTrimBar()
        
        // 確保播放器控制列可用
        binding.playerView.useController = true
        Log.d(TAG, "播放器控制列已啟用")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        _binding = null
    }
}
