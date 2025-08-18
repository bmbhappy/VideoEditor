package com.example.videoeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoeditor.adapters.FileAdapter
import com.example.videoeditor.databinding.ActivityFileManagerBinding
import com.example.videoeditor.models.VideoFile
import com.example.videoeditor.utils.VideoUtils
import java.io.File

class FileManagerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFileManagerBinding
    private var fileAdapter: FileAdapter? = null
    private var videoFiles: List<VideoFile> = emptyList()
    
    companion object {
        private const val TAG = "FileManagerActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
        loadVideoFiles()
    }
    
    private fun setupUI() {
        // 設定工具列
        binding.toolbar.apply {
            setSupportActionBar(this)
            title = "檔案管理"
        }
        
        // 設定返回按鈕
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 設定按鈕
        binding.btnRefresh.setOnClickListener {
            loadVideoFiles()
        }
    }
    
    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            onFileClick = { videoFile ->
                playVideo(videoFile)
            },
            onShareClick = { videoFile ->
                shareVideo(videoFile)
            },
            onDeleteClick = { videoFile ->
                showDeleteDialog(videoFile)
            },
            onSaveToGalleryClick = { videoFile ->
                saveToGallery(videoFile)
            }
        )
        
        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(this@FileManagerActivity)
            adapter = fileAdapter
        }
    }
    
    private fun loadVideoFiles() {
        try {
            val files = VideoUtils.listVideoFiles(this)
            videoFiles = files.map { file ->
                VideoFile(
                    name = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            }
            
            Log.d(TAG, "載入 ${videoFiles.size} 個影片檔案")
            
            if (videoFiles.isEmpty()) {
                binding.tvNoFiles.visibility = android.view.View.VISIBLE
                binding.recyclerViewFiles.visibility = android.view.View.GONE
            } else {
                binding.tvNoFiles.visibility = android.view.View.GONE
                binding.recyclerViewFiles.visibility = android.view.View.VISIBLE
                fileAdapter?.updateFiles(videoFiles)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "載入檔案失敗: ${e.message}")
            Toast.makeText(this, "載入檔案失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun playVideo(videoFile: VideoFile) {
        try {
            val file = File(videoFile.path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                // 嘗試使用系統默認播放器
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 檢查是否有可用的播放器
                val resolveInfo = packageManager.resolveActivity(intent, 0)
                if (resolveInfo != null) {
                    startActivity(intent)
                    Log.d(TAG, "開始播放影片: ${videoFile.name}")
                } else {
                    // 如果沒有默認播放器，嘗試使用其他可用的播放器
                    val chooserIntent = Intent.createChooser(intent, "選擇播放器")
                    if (chooserIntent.resolveActivity(packageManager) != null) {
                        startActivity(chooserIntent)
                        Log.d(TAG, "顯示播放器選擇器")
                    } else {
                        Toast.makeText(this, "沒有可用的影片播放器", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "沒有可用的影片播放器")
                    }
                }
            } else {
                Toast.makeText(this, "檔案不存在: ${videoFile.name}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "檔案不存在: ${videoFile.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放影片失敗: ${e.message}")
            Toast.makeText(this, "播放失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun shareVideo(videoFile: VideoFile) {
        try {
            val file = File(videoFile.path)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                // 創建分享意圖
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "分享影片: ${videoFile.name}")
                    putExtra(Intent.EXTRA_TEXT, "分享影片: ${videoFile.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 檢查是否有可用的分享應用
                val resolveInfo = packageManager.resolveActivity(shareIntent, 0)
                if (resolveInfo != null) {
                    startActivity(Intent.createChooser(shareIntent, "分享影片"))
                    Log.d(TAG, "開始分享影片: ${videoFile.name}")
                } else {
                    Toast.makeText(this, "沒有可用的分享應用", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "沒有可用的分享應用")
                }
            } else {
                Toast.makeText(this, "檔案不存在: ${videoFile.name}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "檔案不存在: ${videoFile.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "分享影片失敗: ${e.message}")
            Toast.makeText(this, "分享失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showDeleteDialog(videoFile: VideoFile) {
        AlertDialog.Builder(this)
            .setTitle("刪除檔案")
            .setMessage("確定要刪除 ${videoFile.name} 嗎？")
            .setPositiveButton("刪除") { _, _ ->
                deleteVideo(videoFile)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun deleteVideo(videoFile: VideoFile) {
        try {
            val file = File(videoFile.path)
            if (file.exists()) {
                val deleted = VideoUtils.deleteFile(file)
                if (deleted) {
                    Toast.makeText(this, "檔案已刪除", Toast.LENGTH_SHORT).show()
                    loadVideoFiles() // 重新載入檔案列表
                } else {
                    Toast.makeText(this, "刪除失敗", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "檔案不存在", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "刪除檔案失敗: ${e.message}")
            Toast.makeText(this, "刪除失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun saveToGallery(videoFile: VideoFile) {
        try {
            val file = File(videoFile.path)
            if (file.exists()) {
                val success = com.example.videoeditor.utils.GalleryUtils.saveVideoToGallery(
                    this,
                    videoFile.path,
                    videoFile.name
                )
                
                if (success) {
                    Toast.makeText(this, "影片已保存到相簿", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "影片已保存到相簿: ${videoFile.name}")
                } else {
                    Toast.makeText(this, "保存到相簿失敗", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "檔案不存在: ${videoFile.name}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "檔案不存在: ${videoFile.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存到相簿失敗: ${e.message}")
            Toast.makeText(this, "保存失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
