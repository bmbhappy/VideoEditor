package com.example.videoeditor.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.videoeditor.R
import com.example.videoeditor.engine.BgmMixConfig
import com.example.videoeditor.engine.BgmPreviewEngine
import com.example.videoeditor.engine.SimpleBgmMixer
import com.example.videoeditor.utils.LogDisplayManager
import com.example.videoeditor.utils.VideoUtils
import com.google.android.material.slider.Slider
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * BGM 長度調整介面
 * 提供多種背景音樂長度調整選項
 */
class BgmAdjustFragment : Fragment() {

    private lateinit var btnSelectBgm: Button
    private lateinit var btnSelectVideo: Button
    private lateinit var btnPreview: Button
    private lateinit var btnApply: Button
    
    private lateinit var tvVideoPath: TextView
    private lateinit var tvBgmPath: TextView
    private lateinit var tvVideoLength: TextView
    private lateinit var tvBgmLength: TextView
    
    private lateinit var rgLengthMode: RadioGroup
    private lateinit var rbLoop: RadioButton
    private lateinit var rbTrim: RadioButton
    private lateinit var rbStretch: RadioButton
    private lateinit var rbFadeOut: RadioButton
    
    private lateinit var sliderVolume: Slider
    private lateinit var sliderStartTime: Slider
    private lateinit var sliderEndTime: Slider
    
    private lateinit var tvVolumeValue: TextView
    private lateinit var tvStartTimeValue: TextView
    private lateinit var tvEndTimeValue: TextView
    
    private var selectedVideoPath: String? = null
    private var selectedBgmPath: String? = null
    private var videoDurationMs: Long = 0
    private var bgmDurationMs: Long = 0
    
    // 預覽引擎
    private var previewEngine: BgmPreviewEngine? = null
    private var isPreviewPlaying = false
    
    // 簡單的音樂預覽（ExoPlayer）
    private var musicPlayer: ExoPlayer? = null
    private var useSimplePreview = true // 使用簡單預覽還是高級預覽

    companion object {
        private const val REQUEST_VIDEO_PICK = 1001
        private const val REQUEST_BGM_PICK = 1002
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bgm_adjust, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupListeners()
        initPreviewEngine()
    }

    private fun initViews(view: View) {
        // 檔案選擇按鈕
        btnSelectVideo = view.findViewById(R.id.btnSelectVideo)
        btnSelectBgm = view.findViewById(R.id.btnSelectBgm)
        btnPreview = view.findViewById(R.id.btnPreview)
        btnApply = view.findViewById(R.id.btnApply)
        
        // 路徑顯示
        tvVideoPath = view.findViewById(R.id.tvVideoPath)
        tvBgmPath = view.findViewById(R.id.tvBgmPath)
        tvVideoLength = view.findViewById(R.id.tvVideoLength)
        tvBgmLength = view.findViewById(R.id.tvBgmLength)
        
        // 長度調整模式
        rgLengthMode = view.findViewById(R.id.rgLengthMode)
        rbLoop = view.findViewById(R.id.rbLoop)
        rbTrim = view.findViewById(R.id.rbTrim)
        rbStretch = view.findViewById(R.id.rbStretch)
        rbFadeOut = view.findViewById(R.id.rbFadeOut)
        
        // 調整滑桿
        sliderVolume = view.findViewById(R.id.sliderVolume)
        sliderStartTime = view.findViewById(R.id.sliderStartTime)
        sliderEndTime = view.findViewById(R.id.sliderEndTime)
        
        // 數值顯示
        tvVolumeValue = view.findViewById(R.id.tvVolumeValue)
        tvStartTimeValue = view.findViewById(R.id.tvStartTimeValue)
        tvEndTimeValue = view.findViewById(R.id.tvEndTimeValue)
        
        // 初始狀態
        btnPreview.isEnabled = false
        btnApply.isEnabled = false
        rbLoop.isChecked = true
        
        // 設定滑桿初始值
        sliderVolume.value = 0.4f
        sliderStartTime.value = 0f
        sliderEndTime.value = 100f
        
        updateVolumeValue()
        updateTimeValues()
    }

    private fun setupListeners() {
        btnSelectVideo.setOnClickListener { selectVideo() }
        btnSelectBgm.setOnClickListener { selectBgm() }
        btnPreview.setOnClickListener { previewBgm() }
        btnApply.setOnClickListener { applyBgm() }
        
        // 長度模式變更
        rgLengthMode.setOnCheckedChangeListener { _, checkedId ->
            updateControlsVisibility(checkedId)
        }
        
        // 音量滑桿
        sliderVolume.addOnChangeListener { _, value, _ ->
            updateVolumeValue()
        }
        
        // 時間滑桿
        sliderStartTime.addOnChangeListener { _, value, _ ->
            if (value >= sliderEndTime.value) {
                sliderStartTime.value = sliderEndTime.value - 1
            }
            updateTimeValues()
        }
        
        sliderEndTime.addOnChangeListener { _, value, _ ->
            if (value <= sliderStartTime.value) {
                sliderEndTime.value = sliderStartTime.value + 1
            }
            updateTimeValues()
        }
    }

    private fun selectVideo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_VIDEO_PICK)
    }

    private fun selectBgm() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_BGM_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK && data?.data != null) {
            when (requestCode) {
                REQUEST_VIDEO_PICK -> handleVideoSelected(data.data!!)
                REQUEST_BGM_PICK -> handleBgmSelected(data.data!!)
            }
        }
    }

    private fun handleVideoSelected(uri: Uri) {
        lifecycleScope.launch {
            try {
                val videoPath = withContext(Dispatchers.IO) {
                    VideoUtils.resolveToLocalFilePath(requireContext(), uri)
                }
                
                if (videoPath != null) {
                    selectedVideoPath = videoPath
                    videoDurationMs = VideoUtils.getVideoDuration(videoPath)
                    
                    tvVideoPath.text = "影片: ${File(videoPath).name}"
                    tvVideoLength.text = "影片長度: ${formatDuration(videoDurationMs)}"
                    
                    updateTimeSliders()
                    checkReadyState()
                } else {
                    showToast("無法讀取影片檔案")
                }
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", "BgmAdjust", "選擇影片失敗: ${e.message}")
                showToast("選擇影片失敗")
            }
        }
    }

    private fun handleBgmSelected(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bgmPath = withContext(Dispatchers.IO) {
                    VideoUtils.resolveToLocalFilePath(requireContext(), uri)
                }
                
                if (bgmPath != null) {
                    selectedBgmPath = bgmPath
                    bgmDurationMs = VideoUtils.getAudioDuration(bgmPath)
                    
                    tvBgmPath.text = "BGM: ${File(bgmPath).name}"
                    tvBgmLength.text = "BGM長度: ${formatDuration(bgmDurationMs)}"
                    
                    updateTimeSliders()
                    checkReadyState()
                    suggestBestMode()
                } else {
                    showToast("無法讀取音樂檔案")
                }
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", "BgmAdjust", "選擇BGM失敗: ${e.message}")
                showToast("選擇BGM失敗")
            }
        }
    }

    private fun updateTimeSliders() {
        if (bgmDurationMs > 0) {
            // 根據 BGM 長度設定時間滑桿的範圍
            sliderStartTime.valueTo = 100f
            sliderEndTime.valueTo = 100f
            updateTimeValues()
        }
    }

    private fun suggestBestMode() {
        if (videoDurationMs > 0 && bgmDurationMs > 0) {
            val ratio = bgmDurationMs.toFloat() / videoDurationMs.toFloat()
            
            when {
                ratio < 0.5f -> {
                    rbLoop.isChecked = true
                    showToast("建議使用循環模式：BGM較短")
                }
                ratio > 2.0f -> {
                    rbTrim.isChecked = true
                    showToast("建議使用裁剪模式：BGM較長")
                }
                ratio in 0.8f..1.2f -> {
                    rbFadeOut.isChecked = true
                    showToast("建議使用淡出模式：長度相近")
                }
                else -> {
                    rbStretch.isChecked = true
                    showToast("建議使用拉伸模式：調整速度")
                }
            }
        }
    }

    private fun checkReadyState() {
        val ready = selectedVideoPath != null && selectedBgmPath != null
        btnPreview.isEnabled = ready
        btnApply.isEnabled = ready
    }

    private fun updateControlsVisibility(checkedId: Int) {
        val showTimeControls = checkedId == R.id.rbTrim || checkedId == R.id.rbFadeOut
        
        findViewById(R.id.layoutTimeControls)?.visibility = 
            if (showTimeControls) View.VISIBLE else View.GONE
    }

    private fun updateVolumeValue() {
        val volume = (sliderVolume.value * 100).toInt()
        tvVolumeValue.text = "${volume}%"
    }

    private fun updateTimeValues() {
        if (bgmDurationMs > 0) {
            val startMs = (sliderStartTime.value / 100f * bgmDurationMs).toLong()
            val endMs = (sliderEndTime.value / 100f * bgmDurationMs).toLong()
            
            tvStartTimeValue.text = formatDuration(startMs)
            tvEndTimeValue.text = formatDuration(endMs)
        }
    }

    private fun previewBgm() {
        val bgmPath = selectedBgmPath
        if (bgmPath == null) {
            showToast("請先選擇背景音樂")
            return
        }

        if (videoDurationMs == 0L || bgmDurationMs == 0L) {
            showToast("請先選擇影片和背景音樂")
            return
        }

        if (isPreviewPlaying) {
            stopPreview()
        } else {
            if (useSimplePreview) {
                startSimplePreview(bgmPath)
            } else {
                startAdvancedPreview(bgmPath)
            }
        }
    }

    /**
     * 開始簡單預覽（使用 ExoPlayer）
     */
    private fun startSimplePreview(bgmPath: String) {
        try {
            // 停止之前的預覽
            stopSimplePreview()
            
            // 創建新的播放器
            musicPlayer = ExoPlayer.Builder(requireContext()).build()
            
            // 從檔案路徑創建 MediaItem
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(bgmPath)))
            musicPlayer?.setMediaItem(mediaItem)
            musicPlayer?.prepare()
            
            // 應用音量設定
            musicPlayer?.volume = sliderVolume.value
            
            // 應用時間控制設定
            val startPercent = sliderStartTime.value / 100f
            val endPercent = sliderEndTime.value / 100f
            val selectedMode = when (rgLengthMode.checkedRadioButtonId) {
                R.id.rbLoop -> LengthAdjustMode.LOOP
                R.id.rbTrim -> LengthAdjustMode.TRIM
                R.id.rbStretch -> LengthAdjustMode.STRETCH
                R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
                else -> LengthAdjustMode.LOOP
            }
            
            // 根據模式設定播放位置
            when (selectedMode) {
                LengthAdjustMode.TRIM -> {
                    // 設定開始位置
                    val startTimeMs = (startPercent * bgmDurationMs).toLong()
                    musicPlayer?.seekTo(startTimeMs)
                    
                    // 計算結束時間
                    val endTimeMs = if (endPercent < 1.0f) {
                        (endPercent * bgmDurationMs).toLong()
                    } else {
                        bgmDurationMs
                    }
                    
                    // 設定播放範圍（通過監聽器實現）
                    val playDuration = endTimeMs - startTimeMs
                    LogDisplayManager.addLog("D", "BgmAdjust", "預覽時間控制: 開始=${startTimeMs}ms, 結束=${endTimeMs}ms, 播放時長=${playDuration}ms")
                }
                else -> {
                    // 其他模式從開始播放
                    musicPlayer?.seekTo(0)
                }
            }
            
            // 開始播放
            musicPlayer?.play()
            
            // 監聽播放狀態
            musicPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_ENDED -> {
                            isPreviewPlaying = false
                            updatePreviewButton()
                            showToast("預覽結束")
                        }
                    }
                }
                
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // 處理播放位置變化
                }
            })
            
            // 添加位置監聽器來處理時間控制
            val previewEndPercent = sliderEndTime.value / 100f
            val previewSelectedMode = when (rgLengthMode.checkedRadioButtonId) {
                R.id.rbLoop -> LengthAdjustMode.LOOP
                R.id.rbTrim -> LengthAdjustMode.TRIM
                R.id.rbStretch -> LengthAdjustMode.STRETCH
                R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
                else -> LengthAdjustMode.LOOP
            }
            
            if (previewSelectedMode == LengthAdjustMode.TRIM && previewEndPercent < 1.0f) {
                val endTimeMs = (previewEndPercent * bgmDurationMs).toLong()
                
                // 定期檢查播放位置
                lifecycleScope.launch {
                    while (isPreviewPlaying) {
                        val currentPosition = musicPlayer?.currentPosition ?: 0L
                        if (currentPosition >= endTimeMs) {
                            stopPreview()
                            showToast("預覽時間範圍結束")
                            break
                        }
                        kotlinx.coroutines.delay(100) // 每100ms檢查一次
                    }
                }
            }
            
            isPreviewPlaying = true
            updatePreviewButton()
            showToast("開始簡單預覽...")
            
            // 10秒後自動停止預覽
            lifecycleScope.launch {
                kotlinx.coroutines.delay(10000)
                if (isPreviewPlaying) {
                    stopPreview()
                }
            }
            
        } catch (e: Exception) {
            LogDisplayManager.addLog("E", "BgmAdjust", "簡單預覽失敗: ${e.message}")
            showToast("預覽失敗: ${e.message}")
            stopPreview()
        }
    }

    /**
     * 開始高級預覽（使用 BgmPreviewEngine）
     */
    private fun startAdvancedPreview(bgmPath: String) {
        lifecycleScope.launch {
            try {
                val previewEngine = previewEngine ?: return@launch
                
                // 設定預覽配置
                previewEngine.setPreviewConfig(
                    videoDurationMs = videoDurationMs,
                    bgmDurationMs = bgmDurationMs,
                    startOffsetPercent = sliderStartTime.value,
                    endOffsetPercent = sliderEndTime.value
                )
                
                // 獲取當前選擇的模式
                val previewMode = when (rgLengthMode.checkedRadioButtonId) {
                    R.id.rbLoop -> BgmPreviewEngine.PreviewMode.LOOP
                    R.id.rbTrim -> BgmPreviewEngine.PreviewMode.TRIM
                    R.id.rbStretch -> BgmPreviewEngine.PreviewMode.STRETCH
                    R.id.rbFadeOut -> BgmPreviewEngine.PreviewMode.FADE_OUT
                    else -> BgmPreviewEngine.PreviewMode.LOOP
                }
                
                // 開始預覽
                previewEngine.startPreview(
                    bgmPath = bgmPath,
                    mode = previewMode,
                    volume = sliderVolume.value
                )
                
                isPreviewPlaying = true
                updatePreviewButton()
                
                showToast("開始預覽...")
                
                // 10秒後自動停止預覽
                kotlinx.coroutines.delay(10000)
                if (isPreviewPlaying) {
                    stopPreview()
                }
                
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", "BgmAdjust", "預覽失敗: ${e.message}")
                showToast("預覽失敗: ${e.message}")
                stopPreview()
            }
        }
    }

    /**
     * 停止預覽
     */
    private fun stopPreview() {
        if (useSimplePreview) {
            stopSimplePreview()
        } else {
            stopAdvancedPreview()
        }
        isPreviewPlaying = false
        updatePreviewButton()
        showToast("預覽已停止")
    }

    /**
     * 停止簡單預覽
     */
    private fun stopSimplePreview() {
        try {
            musicPlayer?.stop()
            musicPlayer?.release()
            musicPlayer = null
        } catch (e: Exception) {
            LogDisplayManager.addLog("W", "BgmAdjust", "停止簡單預覽時發生錯誤: ${e.message}")
        }
    }

    /**
     * 停止高級預覽
     */
    private fun stopAdvancedPreview() {
        previewEngine?.stopPreview()
    }

    /**
     * 更新預覽按鈕狀態
     */
    private fun updatePreviewButton() {
        if (isPreviewPlaying) {
            btnPreview.text = "停止預覽"
            btnPreview.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.accent_red)
        } else {
            btnPreview.text = "預覽"
            btnPreview.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.accent_color)
        }
    }

    /**
     * 初始化預覽引擎
     */
    private fun initPreviewEngine() {
        previewEngine = BgmPreviewEngine().apply {
            initialize(requireContext())
        }
    }

    private fun applyBgm() {
        val videoPath = selectedVideoPath ?: return
        val bgmPath = selectedBgmPath ?: return
        
        lifecycleScope.launch {
            try {
                btnApply.isEnabled = false
                btnApply.text = "處理中..."
                
                val config = createBgmConfig()
                val outputPath = generateOutputPath()
                
                withContext(Dispatchers.IO) {
                    SimpleBgmMixer.mixVideoWithBgm(
                        context = requireContext(),
                        inputVideoPath = videoPath,
                        inputBgmPath = bgmPath,
                        outputPath = outputPath,
                        config = config
                    )
                }
                
                showToast("BGM 添加成功！")
                LogDisplayManager.addLog("I", "BgmAdjust", "BGM 混音完成: $outputPath")
                
            } catch (e: Exception) {
                LogDisplayManager.addLog("E", "BgmAdjust", "BGM 混音失敗: ${e.message}")
                showToast("BGM 添加失敗: ${e.message}")
            } finally {
                btnApply.isEnabled = true
                btnApply.text = "套用 BGM"
            }
        }
    }

    private fun createBgmConfig(): BgmMixConfig {
        val selectedMode = when (rgLengthMode.checkedRadioButtonId) {
            R.id.rbLoop -> LengthAdjustMode.LOOP
            R.id.rbTrim -> LengthAdjustMode.TRIM
            R.id.rbStretch -> LengthAdjustMode.STRETCH
            R.id.rbFadeOut -> LengthAdjustMode.FADE_OUT
            else -> LengthAdjustMode.LOOP
        }
        
        val volume = sliderVolume.value
        val startPercent = sliderStartTime.value / 100f
        val endPercent = sliderEndTime.value / 100f
        
        // 計算時間偏移（微秒）
        val startOffsetUs = (startPercent * bgmDurationMs * 1000).toLong()
        val endOffsetUs = if (endPercent < 1.0f) {
            (endPercent * bgmDurationMs * 1000).toLong()
        } else 0L
        
        LogDisplayManager.addLog("D", "BgmAdjust", "時間控制: 模式=$selectedMode, 開始=${startPercent*100}%, 結束=${endPercent*100}%, 開始偏移=${startOffsetUs}us, 結束偏移=${endOffsetUs}us")
        
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
        return File(outputDir, "bgm_adjusted_${System.currentTimeMillis()}.mp4").absolutePath
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun findViewById(id: Int): View? {
        return view?.findViewById<View>(id)
    }

    override fun onPause() {
        super.onPause()
        stopPreview()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPreview()
        previewEngine?.release()
        previewEngine = null
        musicPlayer?.release()
        musicPlayer = null
    }

    enum class LengthAdjustMode {
        LOOP,      // 循環播放
        TRIM,      // 裁剪到指定長度
        STRETCH,   // 拉伸/壓縮時間
        FADE_OUT   // 淡出結束
    }
}
