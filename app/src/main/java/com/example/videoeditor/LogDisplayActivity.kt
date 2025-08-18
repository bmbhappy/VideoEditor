package com.example.videoeditor

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoeditor.adapters.LogAdapter
import com.example.videoeditor.databinding.ActivityLogDisplayBinding
import com.example.videoeditor.utils.LogDisplayManager

class LogDisplayActivity : AppCompatActivity(), LogDisplayManager.LogUpdateListener {
    
    private lateinit var binding: ActivityLogDisplayBinding
    private lateinit var logAdapter: LogAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
        LogDisplayManager.addListener(this)
    }
    
    private fun setupUI() {
        // 設定工具列
        binding.toolbar.apply {
            setSupportActionBar(this)
            title = "執行日誌"
        }
        
        // 設定返回按鈕
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 設定按鈕
        binding.btnClear.setOnClickListener {
            LogDisplayManager.clearLogs()
        }
        
        binding.btnCopy.setOnClickListener {
            copyLogsToClipboard()
        }
    }
    
    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.recyclerViewLogs.apply {
            layoutManager = LinearLayoutManager(this@LogDisplayActivity)
            adapter = logAdapter
        }
    }
    
    private fun copyLogsToClipboard() {
        val logs = LogDisplayManager.getLogs()
        val logText = logs.joinToString("\n") { "${it.timestamp} ${it.level}/${it.tag}: ${it.message}" }
        
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("執行日誌", logText)
        clipboard.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(this, "日誌已複製到剪貼簿", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    override fun onLogUpdated(logs: List<LogDisplayManager.LogEntry>) {
        runOnUiThread {
            logAdapter.updateLogs(logs)
            // 自動滾動到底部
            binding.recyclerViewLogs.smoothScrollToPosition(logs.size)
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
    
    override fun onDestroy() {
        super.onDestroy()
        LogDisplayManager.removeListener(this)
    }
}
