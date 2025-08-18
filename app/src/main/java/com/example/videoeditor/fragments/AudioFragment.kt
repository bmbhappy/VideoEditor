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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.videoeditor.R
import com.example.videoeditor.databinding.FragmentAudioBinding
import com.example.videoeditor.engine.VideoProcessor
import com.example.videoeditor.utils.VideoUtils
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.launch
import java.io.File

class AudioFragment : Fragment() {
    
    private var _binding: FragmentAudioBinding? = null
    private val binding get() = _binding!!
    
    private var videoUri: Uri? = null
    private var videoPath: String? = null
    private var musicUri: Uri? = null
    
    private var player: ExoPlayer? = null
    private var videoProcessor: VideoProcessor? = null
    private var musicPlayer: ExoPlayer? = null
    
    // 音訊處理選項
    private var removeBackgroundAudio: Boolean = false
    private var addBackgroundMusic: Boolean = false
    
    // 檔案選擇器
    private val pickMusicLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            onMusicSelected(it)
        }
    }
    
    companion object {
        private const val TAG = "AudioFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupPlayer()
        setupAudioControls()
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

                        }
                        Player.STATE_ENDED -> {
                            Log.d(TAG, "播放結束")
                        }
                    }
                }
            })
        }
    }
    

    
    private fun setupAudioControls() {
        // 去除背景聲音開關
        binding.switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            removeBackgroundAudio = isChecked
            Log.d(TAG, "去除背景聲音: $isChecked")
            
            if (isChecked) {
                addBackgroundMusic = false
                binding.switchAddBackgroundMusic.isChecked = false
            }
        }
        
        // 添加背景音樂開關
        binding.switchAddBackgroundMusic.setOnCheckedChangeListener { _, isChecked ->
            addBackgroundMusic = isChecked
            Log.d(TAG, "添加背景音樂: $isChecked")
            
            if (isChecked) {
                removeBackgroundAudio = false
                binding.switchRemoveBackground.isChecked = false
            }
        }
        
        // 選擇背景音樂按鈕
        binding.btnSelectMusic.setOnClickListener {
            pickMusicLauncher.launch("audio/*")
        }
        
        // 預覽背景音樂按鈕
        binding.btnPreviewMusic.setOnClickListener {
            if (musicUri != null) {
                if (musicPlayer?.isPlaying == true) {
                    stopMusicPreview()
                    Toast.makeText(context, "停止預覽背景音樂", Toast.LENGTH_SHORT).show()
                } else {
                    previewBackgroundMusic()
                }
            } else {
                Toast.makeText(context, "請先選擇背景音樂", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupButtons() {
        // 設定重置和確定按鈕
        binding.btnReset.setOnClickListener {
            // 重置選項
            removeBackgroundAudio = false
            addBackgroundMusic = false
            musicUri = null
            
            binding.switchRemoveBackground.isChecked = false
            binding.switchAddBackgroundMusic.isChecked = false
            binding.tvSelectedMusic.text = "未選擇背景音樂"
            binding.btnSelectMusic.text = "選擇背景音樂"
            binding.btnPreviewMusic.isEnabled = false
            binding.btnPreviewMusic.text = "預覽音樂"
            
            // 停止音樂預覽
            stopMusicPreview()
        }
        
        binding.btnApply.setOnClickListener {
            if (videoUri != null) {
                performAudioProcessing()
            } else {
                Toast.makeText(context, "請先選擇影片", Toast.LENGTH_SHORT).show()
            }
        }
        

    }
    
    private fun performAudioProcessing() {
        when {
            removeBackgroundAudio -> {
                performRemoveBackgroundAudio()
            }
            addBackgroundMusic -> {
                if (musicUri != null) {
                    performAddBackgroundMusic()
                } else {
                    Toast.makeText(context, "請先選擇背景音樂", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(context, "請選擇音訊處理選項", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun performRemoveBackgroundAudio() {
        Log.d(TAG, "開始移除背景聲音")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnApply.isEnabled = false
        
        lifecycleScope.launch {
            videoProcessor?.removeAudio(
                videoUri!!,
                object : VideoProcessor.ProcessingCallback {
                    override fun onProgress(progress: Float) {
                        Log.d(TAG, "移除音訊進度: $progress%")
                    }
                    
                    override fun onSuccess(outputPath: String) {
                        Log.d(TAG, "移除音訊成功: $outputPath")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            
                            // 顯示成功訊息並提供選項
                            val options = arrayOf("查看檔案", "分享檔案", "保存到相簿", "確定")
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("移除背景聲音完成")
                                .setMessage("背景聲音移除成功！\n檔案已保存到應用程式內部儲存空間。")
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
                        Log.e(TAG, "移除音訊失敗: $error")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            Toast.makeText(context, "移除背景聲音失敗: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
    
    private fun performAddBackgroundMusic() {
        Log.d(TAG, "開始添加背景音樂")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnApply.isEnabled = false
        
        lifecycleScope.launch {
            videoProcessor?.addBackgroundMusic(
                videoUri!!,
                musicUri!!,
                object : VideoProcessor.ProcessingCallback {
                    override fun onProgress(progress: Float) {
                        Log.d(TAG, "添加背景音樂進度: $progress%")
                    }
                    
                    override fun onSuccess(outputPath: String) {
                        Log.d(TAG, "添加背景音樂成功: $outputPath")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            
                            // 顯示成功訊息並提供選項
                            val options = arrayOf("查看檔案", "分享檔案", "保存到相簿", "確定")
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("添加背景音樂完成")
                                .setMessage("背景音樂添加成功！\n檔案已保存到應用程式內部儲存空間。")
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
                        Log.e(TAG, "添加背景音樂失敗: $error")
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            Toast.makeText(context, "添加背景音樂失敗: $error", Toast.LENGTH_LONG).show()
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
    
    private fun onMusicSelected(uri: Uri) {
        musicUri = uri
        val fileName = VideoUtils.getPathFromUri(requireContext(), uri)?.let { path ->
            File(path).name
        } ?: "未知檔案"
        
        Log.d(TAG, "選擇背景音樂: $fileName")
        binding.tvSelectedMusic.text = "已選擇: $fileName"
        binding.btnSelectMusic.text = "重新選擇"
        binding.btnPreviewMusic.isEnabled = true
        binding.btnPreviewMusic.text = "預覽音樂"
        
        // 停止之前的音樂預覽
        stopMusicPreview()
    }
    
    private fun previewBackgroundMusic() {
        if (musicUri == null) return
        
        try {
            if (musicPlayer == null) {
                musicPlayer = ExoPlayer.Builder(requireContext()).build()
            }
            
            val mediaItem = MediaItem.fromUri(musicUri!!)
            musicPlayer?.setMediaItem(mediaItem)
            musicPlayer?.prepare()
            musicPlayer?.play()
            
            binding.btnPreviewMusic.text = "停止預覽"
            
            // 監聽播放狀態
            musicPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            binding.btnPreviewMusic.text = "預覽音樂"
                        }
                    }
                }
            })
            
            Toast.makeText(context, "開始預覽背景音樂", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "預覽背景音樂失敗: ${e.message}")
            Toast.makeText(context, "預覽背景音樂失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopMusicPreview() {
        try {
            musicPlayer?.stop()
            musicPlayer?.release()
            musicPlayer = null
            binding.btnPreviewMusic.text = "預覽音樂"
        } catch (e: Exception) {
            Log.e(TAG, "停止音樂預覽失敗: ${e.message}")
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
        
        Log.d(TAG, "影片時長: ${VideoUtils.formatDuration(VideoUtils.getVideoDuration(requireContext(), uri))}")
        
        // 確保播放器控制列可用
        binding.playerView.useController = true
        Log.d(TAG, "播放器控制列已啟用")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        stopMusicPreview()
        _binding = null
    }
}
