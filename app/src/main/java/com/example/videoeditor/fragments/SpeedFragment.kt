package com.example.videoeditor.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.videoeditor.R
import com.example.videoeditor.databinding.FragmentSpeedBinding
import com.example.videoeditor.engine.VideoProcessor
import com.example.videoeditor.utils.VideoUtils
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.launch
import java.io.File

class SpeedFragment : Fragment() {
    
    private var _binding: FragmentSpeedBinding? = null
    private val binding get() = _binding!!
    
    private var videoUri: Uri? = null
    private var videoPath: String? = null
    private var videoDuration: Long = 0L
    
    private var player: ExoPlayer? = null
    private var videoProcessor: VideoProcessor? = null
    
    // 變速參數
    private var currentSpeedFactor: Float = 1.0f
    
    companion object {
        private const val TAG = "SpeedFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupPlayer()
        setupSpeedControls()
        setupButtons()
        
        videoProcessor = VideoProcessor(requireContext())
    }
    
    private fun setupUI() {
        // 已移除播放按鈕
    }
    
    private fun setupPlayer() {
        player = ExoPlayer.Builder(requireContext()).build().apply {
            binding.playerView.player = this
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d(TAG, "播放器準備就緒")
                            updateSpeedDisplay()

                        }
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "播放結束")
                        }
                    }
                }
            })
        }
    }
    

    
    private fun setupSpeedControls() {
        // 設定速度選項
        binding.btnSpeedSlow.setOnClickListener { setSpeed(0.5f) }
        binding.btnSpeedNormal.setOnClickListener { setSpeed(1.0f) }
        binding.btnSpeedFast.setOnClickListener { setSpeed(2.0f) }
        
        // 設定自定義速度滑桿
        binding.speedSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                setSpeed(value)
            }
        }
    }
    
    private fun setupButtons() {
        // 設定重置和確定按鈕
        binding.btnReset.setOnClickListener {
            // 重置速度
            setSpeed(1.0f)
        }
        
        binding.btnApply.setOnClickListener {
            if (videoUri != null) {
                performSpeedChange()
            } else {
                Toast.makeText(context, "請先選擇影片", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setSpeed(speedFactor: Float) {
        currentSpeedFactor = speedFactor
        
        // 更新播放器速度
        player?.playbackParameters = PlaybackParameters(speedFactor)
        
        // 更新UI
        updateSpeedDisplay()
        updateSpeedButtons(speedFactor)
        
        Log.d(TAG, "設定播放速度: ${speedFactor}x")
    }
    
    private fun updateSpeedDisplay() {
        binding.tvCurrentSpeed.text = "${currentSpeedFactor}x"
        
        if (videoDuration > 0) {
            val newDuration = (videoDuration / currentSpeedFactor).toLong()
            binding.tvNewDuration.text = VideoUtils.formatDuration(newDuration)
        }
    }
    
    private fun updateSpeedButtons(speedFactor: Float) {
        binding.btnSpeedSlow.isSelected = speedFactor == 0.5f
        binding.btnSpeedNormal.isSelected = speedFactor == 1.0f
        binding.btnSpeedFast.isSelected = speedFactor == 2.0f
        
        binding.speedSlider.value = speedFactor
    }
    
    private fun performSpeedChange() {
        if (currentSpeedFactor == 1.0f) {
            Toast.makeText(context, "速度已經是正常速度", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "開始變速處理: ${currentSpeedFactor}x")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnApply.isEnabled = false
        
        lifecycleScope.launch {
            videoProcessor?.changeSpeed(
                videoUri!!,
                currentSpeedFactor,
                object : VideoProcessor.ProcessingCallback {
                    override fun onProgress(progress: Float) {
                        Log.d(TAG, "變速進度: $progress%")
                    }
                    
                    override fun onSuccess(outputPath: String) {
                        Log.d(TAG, "變速成功: $outputPath")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            
                            // 顯示成功訊息並提供選項
                            val options = arrayOf("查看檔案", "分享檔案", "保存到相簿", "確定")
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("變速完成")
                                .setMessage("影片變速成功！\n檔案已保存到應用程式內部儲存空間。")
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
                        Log.e(TAG, "變速失敗: $error")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            Toast.makeText(context, "變速失敗: $error", Toast.LENGTH_LONG).show()
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
        updateSpeedDisplay()
        
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
