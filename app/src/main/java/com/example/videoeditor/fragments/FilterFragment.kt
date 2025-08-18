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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoeditor.R
import com.example.videoeditor.databinding.FragmentFilterBinding
import com.example.videoeditor.engine.VideoProcessor
import com.example.videoeditor.utils.VideoUtils
import com.example.videoeditor.adapters.FilterAdapter
import com.example.videoeditor.models.FilterOption
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.launch
import java.io.File

class FilterFragment : Fragment() {
    
    private var _binding: FragmentFilterBinding? = null
    private val binding get() = _binding!!
    
    private var videoUri: Uri? = null
    private var videoPath: String? = null
    
    private var player: ExoPlayer? = null
    private var videoProcessor: VideoProcessor? = null
    private var filterAdapter: FilterAdapter? = null
    
    // 濾鏡選項
    private val filterOptions = listOf(
        FilterOption("原圖", "original", R.drawable.ic_filter_original),
        FilterOption("復古", "vintage", R.drawable.ic_filter_vintage),
        FilterOption("黑白", "bw", R.drawable.ic_filter_bw),
        FilterOption("暖色", "warm", R.drawable.ic_filter_warm),
        FilterOption("冷色", "cool", R.drawable.ic_filter_cool),
        FilterOption("高對比", "contrast", R.drawable.ic_filter_contrast),
        FilterOption("模糊", "blur", R.drawable.ic_filter_blur),
        FilterOption("銳化", "sharpen", R.drawable.ic_filter_sharpen)
    )
    
    private var selectedFilter: String = "original"
    
    companion object {
        private const val TAG = "FilterFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupPlayer()
        setupFilterRecyclerView()
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
    

    
    private fun setupFilterRecyclerView() {
        filterAdapter = FilterAdapter(filterOptions) { filter ->
            selectedFilter = filter.id
            Log.d(TAG, "選擇濾鏡: ${filter.name}")
            
            // 這裡可以即時預覽濾鏡效果
            applyFilterPreview(filter.id)
        }
        
        binding.recyclerViewFilters.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = filterAdapter
        }
    }
    
    private fun setupButtons() {
        // 設定重置和確定按鈕
        binding.btnReset.setOnClickListener {
            // 重置濾鏡
            selectedFilter = "original"
            filterAdapter?.setSelectedFilter("original")
            applyFilterPreview("original")
        }
        
        binding.btnApply.setOnClickListener {
            if (videoUri != null) {
                performFilterApplication()
            } else {
                Toast.makeText(context, "請先選擇影片", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun applyFilterPreview(filterId: String) {
        // 這裡可以即時應用濾鏡效果到播放器
        // 由於ExoPlayer的限制，這裡只是記錄選擇的濾鏡
        Log.d(TAG, "應用濾鏡預覽: $filterId")
        
        // 可以通過自定義的SurfaceView或TextureView來實現即時濾鏡效果
        // 這裡簡化處理，只更新UI狀態
        updateFilterDisplay(filterId)
    }
    
    private fun updateFilterDisplay(filterId: String) {
        val filter = filterOptions.find { it.id == filterId }
        filter?.let {
            binding.tvCurrentFilter.text = "當前濾鏡: ${it.name}"
        }
    }
    
    private fun performFilterApplication() {
        if (selectedFilter == "original") {
            Toast.makeText(context, "請選擇一個濾鏡", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (videoUri == null) {
            Toast.makeText(context, "請先選擇影片", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "開始應用濾鏡: $selectedFilter")
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnApply.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val videoProcessor = VideoProcessor(requireContext())
                videoProcessor.applyFilter(videoUri!!, selectedFilter, object : VideoProcessor.ProcessingCallback {
                    override fun onProgress(progress: Float) {
                        requireActivity().runOnUiThread {
                            binding.progressBar.progress = progress.toInt()
                        }
                    }
                    
                    override fun onSuccess(outputPath: String) {
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            
                            // 顯示成功訊息並提供選項
                            val options = arrayOf("查看檔案", "分享檔案", "保存到相簿", "確定")
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("濾鏡應用完成")
                                .setMessage("濾鏡應用成功！\n檔案已保存到應用程式內部儲存空間。")
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
                        requireActivity().runOnUiThread {
                            binding.progressBar.visibility = View.GONE
                            binding.btnApply.isEnabled = true
                            Toast.makeText(context, "濾鏡應用失敗: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "濾鏡應用失敗: ${e.message}")
                requireActivity().runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.btnApply.isEnabled = true
                    Toast.makeText(context, "濾鏡應用失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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
        
        // 初始化濾鏡顯示
        updateFilterDisplay(selectedFilter)
        
        // 確保播放器控制列可用
        binding.playerView.useController = true
        Log.d(TAG, "播放器控制列已啟用")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        _binding = null
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
}
