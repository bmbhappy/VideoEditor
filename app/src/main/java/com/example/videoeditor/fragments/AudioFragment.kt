package com.example.videoeditor.fragments

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import com.example.videoeditor.engine.SimpleBgmMixer
import com.example.videoeditor.engine.BgmMixConfig
import com.example.videoeditor.utils.VideoUtils
import com.example.videoeditor.utils.LogDisplayManager
import com.example.videoeditor.utils.MemoryProtectionUtils
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
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
    
    // BGM 調整相關變數
    private var bgmDurationMs: Long = 0L
    private var videoDurationMs: Long = 0L
    private var selectedBgmPath: String? = null
    private var selectedVideoPath: String? = null
    
    // BGM 調整模式枚舉
    enum class LengthAdjustMode {
        LOOP,      // 循環播放
        TRIM,      // 裁剪到指定長度
        STRETCH,   // 拉伸/壓縮時間
        FADE_OUT   // 淡出結束
    }
    
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
        
        // 調試：確保 UI 元素可見
        Log.d(TAG, "onViewCreated: 設置 UI 可見性")
        binding.audioControls.visibility = View.VISIBLE
        binding.switchRemoveBackground.visibility = View.VISIBLE
        binding.switchAddBackgroundMusic.visibility = View.VISIBLE
        
        // 確保音量控制可見
        binding.sliderVolume.visibility = View.VISIBLE
        binding.tvVolumeValue.visibility = View.VISIBLE
        
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
                
                // 顯示 BGM 調整控制區域
                binding.layoutBgmControls.visibility = View.VISIBLE
            } else {
                // 隱藏 BGM 調整控制區域
                binding.layoutBgmControls.visibility = View.GONE
                binding.layoutTimeControls.visibility = View.GONE
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
        
        // 設置 BGM 調整控制
        setupBgmControls()
    }
    
    private fun setupBgmControls() {
        // 長度調整模式選擇
        binding.rgLengthMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbLoop -> {
                    binding.layoutTimeControls.visibility = View.GONE
                    LogDisplayManager.addLog("D", "AudioFragment", "選擇循環播放模式")
                }
                R.id.rbTrim -> {
                    binding.layoutTimeControls.visibility = View.VISIBLE
                    LogDisplayManager.addLog("D", "AudioFragment", "選擇裁剪模式")
                }
                R.id.rbStretch -> {
                    binding.layoutTimeControls.visibility = View.GONE
                    LogDisplayManager.addLog("D", "AudioFragment", "選擇拉伸模式")
                }
                R.id.rbFadeOut -> {
                    binding.layoutTimeControls.visibility = View.VISIBLE
                    LogDisplayManager.addLog("D", "AudioFragment", "選擇淡出模式")
                }
            }
        }
        
        // 確保音量控制始終可見
        binding.sliderVolume.visibility = View.VISIBLE
        binding.tvVolumeValue.visibility = View.VISIBLE
        
        // 開始時間滑塊
        binding.sliderStartTime.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val timeMs = (value / 100f * bgmDurationMs).toLong()
                binding.tvStartTimeValue.text = formatDuration(timeMs)
                LogDisplayManager.addLog("D", "AudioFragment", "開始時間: ${value}% (${timeMs}ms)")
            }
        }
        
        // 結束時間滑塊
        binding.sliderEndTime.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val timeMs = (value / 100f * bgmDurationMs).toLong()
                binding.tvEndTimeValue.text = formatDuration(timeMs)
                LogDisplayManager.addLog("D", "AudioFragment", "結束時間: ${value}% (${timeMs}ms)")
            }
        }
        
        // 音量滑塊
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val percentage = (value * 100).toInt()
                binding.tvVolumeValue.text = "${percentage}%"
                LogDisplayManager.addLog("D", "AudioFragment", "音量: ${percentage}%")
            }
        }
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
    

    
    private fun setupButtons() {
        // 設定BGM時間控制預覽和確定按鈕
        binding.btnReset.setOnClickListener {
            if (musicPlayer?.isPlaying == true) {
                // 如果正在播放，則停止預覽
                stopMusicPreview()
                Toast.makeText(context, "停止預覽", Toast.LENGTH_SHORT).show()
            } else {
                // 如果沒有播放，則開始預覽
                previewBgmTimeControl()
            }
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
        
        if (selectedVideoPath == null || selectedBgmPath == null) {
            Toast.makeText(context, "請先選擇影片和背景音樂", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 使用記憶體保護工具類進行檢查
        val files = listOf(
            selectedVideoPath!! to MemoryProtectionUtils.FileType.VIDEO,
            selectedBgmPath!! to MemoryProtectionUtils.FileType.AUDIO
        )
        
        if (!MemoryProtectionUtils.checkMultipleFilesSuitability(requireContext(), files)) {
            return
        }
        
        if (!MemoryProtectionUtils.checkDurationLimit(requireContext(), videoDurationMs, MemoryProtectionUtils.MediaType.VIDEO)) {
            return
        }
        
        if (!MemoryProtectionUtils.checkDurationLimit(requireContext(), bgmDurationMs, MemoryProtectionUtils.MediaType.AUDIO)) {
            return
        }
        
        if (!MemoryProtectionUtils.checkMemoryUsage(requireContext())) {
            return
        }
        
        Log.d(TAG, "記憶體保護檢查通過: ${MemoryProtectionUtils.getMemoryInfo()}")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnApply.isEnabled = false
        
        lifecycleScope.launch {
            try {
                // 創建 BGM 配置（使用用戶設定）
                val config = createBgmConfig()
                
                val outputPath = generateOutputPath()
                
                Log.d(TAG, "開始處理前記憶體狀態: ${MemoryProtectionUtils.getMemoryInfo()}")
                
                withContext(Dispatchers.IO) {
                    try {
                        // 定期檢查記憶體使用情況
                        val memoryMonitorJob = launch {
                            while (isActive) {
                                val memoryInfo = MemoryProtectionUtils.getMemoryInfo()
                                Log.d(TAG, "處理中記憶體狀態: $memoryInfo")
                                
                                // 如果記憶體使用過高，強制垃圾回收
                                val runtime = Runtime.getRuntime()
                                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                                val maxMemory = runtime.maxMemory()
                                val memoryUsage = usedMemory.toDouble() / maxMemory.toDouble()
                                
                                if (memoryUsage > 0.9) { // 如果記憶體使用超過90%
                                    Log.w(TAG, "記憶體使用過高，執行垃圾回收")
                                    MemoryProtectionUtils.forceGarbageCollection()
                                    delay(1000) // 等待1秒
                                }
                                
                                delay(3000) // 每3秒檢查一次
                            }
                        }
                        
                        SimpleBgmMixer.mixVideoWithBgm(
                            context = requireContext(),
                            inputVideoPath = selectedVideoPath!!,
                            inputBgmPath = selectedBgmPath!!,
                            outputPath = outputPath,
                            config = config
                        )
                        
                        // 取消記憶體監控
                        memoryMonitorJob.cancel()
                        
                        // 處理完成後強制垃圾回收
                        MemoryProtectionUtils.forceGarbageCollection()
                        
                        Log.d(TAG, "處理完成後記憶體狀態: ${MemoryProtectionUtils.getMemoryInfo()}")
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "處理過程中發生錯誤: ${e.message}")
                        throw e
                    }
                }
                
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
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", "AudioFragment", "添加背景音樂失敗: ${e.message}")
                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnApply.isEnabled = true
                    Toast.makeText(context, "添加背景音樂失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun createBgmConfig(): BgmMixConfig {
        val selectedMode = when (binding.rgLengthMode.checkedRadioButtonId) {
            R.id.rbLoop -> LengthAdjustMode.LOOP
            R.id.rbTrim -> LengthAdjustMode.TRIM
            R.id.rbStretch -> LengthAdjustMode.STRETCH
            R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
            else -> LengthAdjustMode.LOOP
        }
        
        val volume = binding.sliderVolume.value
        val startPercent = binding.sliderStartTime.value / 100f
        val endPercent = binding.sliderEndTime.value / 100f
        
        // 計算時間偏移（微秒）
        val startOffsetUs = (startPercent * bgmDurationMs * 1000).toLong()
        val endOffsetUs = if (endPercent < 1.0f) {
            (endPercent * bgmDurationMs * 1000).toLong()
        } else 0L
        
        LogDisplayManager.addLog("D", "AudioFragment", "BGM 配置: 模式=$selectedMode, 開始=${startPercent*100}%, 結束=${endPercent*100}%, 開始偏移=${startOffsetUs}us, 結束偏移=${endOffsetUs}us, 音量=$volume")
        
        return BgmMixConfig(
            bgmVolume = volume,
            loopBgm = selectedMode == LengthAdjustMode.LOOP,
            bgmStartOffsetUs = startOffsetUs,
            bgmEndOffsetUs = endOffsetUs,
            lengthAdjustMode = selectedMode.name
        )
    }
    
    private fun generateOutputPath(): String {
        val outputDir = VideoUtils.getAppFilesDirectory(requireContext())
        return File(outputDir, "audio_bgm_${System.currentTimeMillis()}.mp4").absolutePath
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
        
        // 獲取檔案名稱
        val fileName = getFileNameFromUri(uri) ?: "未知檔案"
        
        Log.d(TAG, "選擇背景音樂: $fileName")
        binding.tvSelectedMusic.text = "已選擇: $fileName"
        binding.btnSelectMusic.text = "重新選擇"
        binding.btnPreviewMusic.isEnabled = true
        binding.btnPreviewMusic.text = "預覽音樂"
        
        // 保存 BGM 路徑和獲取時長
        val bgmPath = VideoUtils.resolveToLocalFilePath(requireContext(), uri, "bgm", "mp3")
        selectedBgmPath = bgmPath
        if (bgmPath != null) {
            bgmDurationMs = VideoUtils.getAudioDuration(bgmPath)
            LogDisplayManager.addLog("D", "AudioFragment", "BGM 時長: ${bgmDurationMs}ms")
            
            // 更新時間顯示
            binding.tvStartTimeValue.text = formatDuration(0)
            binding.tvEndTimeValue.text = formatDuration(bgmDurationMs)
        }
        
        // 停止之前的音樂預覽
        stopMusicPreview()
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    val cursor = requireContext().contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (columnIndex >= 0) {
                                it.getString(columnIndex)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                }
                ContentResolver.SCHEME_FILE -> {
                    File(uri.path ?: "").name
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file name from URI: ${e.message}")
            null
        }
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
            
                    // 應用音量設定（用戶設定）
        musicPlayer?.volume = binding.sliderVolume.value
            
            // 開始播放
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
            
            // 10秒後自動停止預覽
            lifecycleScope.launch {
                kotlinx.coroutines.delay(10000)
                if (musicPlayer?.isPlaying == true) {
                    stopMusicPreview()
                    Toast.makeText(context, "預覽結束", Toast.LENGTH_SHORT).show()
                }
            }
            
            Toast.makeText(context, "開始預覽背景音樂（10秒）", Toast.LENGTH_SHORT).show()
            
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
            binding.btnReset.text = "BGM預覽"
        } catch (e: Exception) {
            Log.e(TAG, "停止音樂預覽失敗: ${e.message}")
        }
    }
    
    private fun previewBgmTimeControl() {
        if (musicUri == null) {
            Toast.makeText(context, "請先選擇背景音樂", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedBgmPath == null) {
            Toast.makeText(context, "背景音樂檔案路徑無效", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // 停止之前的預覽
            stopMusicPreview()
            
            // 創建新的播放器
            musicPlayer = ExoPlayer.Builder(requireContext()).build()
            
            val mediaItem = MediaItem.fromUri(musicUri!!)
            musicPlayer?.setMediaItem(mediaItem)
            musicPlayer?.prepare()
            
            // 獲取當前選擇的模式
            val selectedMode = when (binding.rgLengthMode.checkedRadioButtonId) {
                R.id.rbLoop -> LengthAdjustMode.LOOP
                R.id.rbTrim -> LengthAdjustMode.TRIM
                R.id.rbStretch -> LengthAdjustMode.STRETCH
                R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
                else -> LengthAdjustMode.LOOP
            }
            
            // 應用音量設定
            musicPlayer?.volume = binding.sliderVolume.value
            
            // 根據模式設置預覽行為
            when (selectedMode) {
                LengthAdjustMode.TRIM, LengthAdjustMode.FADE_OUT -> {
                    // 計算開始和結束時間
                    val startPercent = binding.sliderStartTime.value / 100f
                    val endPercent = binding.sliderEndTime.value / 100f
                    val startTimeMs = (bgmDurationMs * startPercent).toLong()
                    val endTimeMs = (bgmDurationMs * endPercent).toLong()
                    
                    // 跳轉到開始時間
                    musicPlayer?.seekTo(startTimeMs)
                    
                    // 開始播放
                    musicPlayer?.play()
                    
                    // 監聽播放位置，到達結束時間時停止
                    lifecycleScope.launch {
                        while (musicPlayer?.isPlaying == true) {
                            val currentPosition = musicPlayer?.currentPosition ?: 0L
                            if (currentPosition >= endTimeMs) {
                                stopMusicPreview()
                                break
                            }
                            kotlinx.coroutines.delay(100) // 每100ms檢查一次
                        }
                    }
                    
                    Toast.makeText(context, "預覽時間控制: ${formatDuration(startTimeMs)} - ${formatDuration(endTimeMs)}", Toast.LENGTH_SHORT).show()
                }
                
                LengthAdjustMode.LOOP -> {
                    // 循環播放預覽
                    musicPlayer?.play()
                    
                    // 監聽播放結束，重新開始
                    musicPlayer?.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_ENDED -> {
                                    musicPlayer?.seekTo(0)
                                    musicPlayer?.play()
                                }
                            }
                        }
                    })
                    
                    Toast.makeText(context, "預覽循環播放模式", Toast.LENGTH_SHORT).show()
                }
                
                LengthAdjustMode.STRETCH -> {
                    // 拉伸模式預覽（簡化版本）
                    musicPlayer?.play()
                    Toast.makeText(context, "預覽拉伸模式", Toast.LENGTH_SHORT).show()
                }
            }
            
            binding.btnReset.text = "停止預覽"
            
        } catch (e: Exception) {
            Log.e(TAG, "BGM時間控制預覽失敗: ${e.message}")
            Toast.makeText(context, "預覽失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.btnReset.text = "BGM預覽"
        }
    }
    
    fun onVideoLoaded(uri: Uri, path: String?) {
        Log.d(TAG, "影片載入: $path")
        
        videoUri = uri
        videoPath = path
        selectedVideoPath = path
        
        // 獲取影片時長
        if (path != null) {
            videoDurationMs = VideoUtils.getVideoDuration(requireContext(), uri)
            LogDisplayManager.addLog("D", "AudioFragment", "影片時長: ${videoDurationMs}ms")
        }
        
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
