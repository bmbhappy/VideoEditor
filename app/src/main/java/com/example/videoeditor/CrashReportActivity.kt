package com.example.videoeditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.videoeditor.databinding.ActivityCrashReportBinding
import com.example.videoeditor.utils.UltraGuaranteedCrashReporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CrashReportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCrashReportBinding
    private lateinit var adapter: CrashReportAdapter
    private val crashReports = mutableListOf<CrashReport>()
    
    /**
     * 崩潰報告數據類
     */
    data class CrashReport(
        val file: File,
        val content: String,
        val timestamp: Long,
        val exceptionType: String,
        val exceptionMessage: String
    ) {
        val shortTitle: String
            get() = "崩潰報告 ${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))}"
        
        val formattedDate: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
    
    companion object {
        private const val TAG = "CrashReportActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        loadCrashReports()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "崩潰報告"
        }
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = CrashReportAdapter(crashReports) { report ->
            showCrashReportDetail(report)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@CrashReportActivity)
            adapter = this@CrashReportActivity.adapter
        }
    }
    
    private fun loadCrashReports() {
        try {
            crashReports.clear()
            
            // 使用超強保證崩潰報告器獲取所有報告
            val reportFiles = UltraGuaranteedCrashReporter.getAllCrashReports(this)
            
            for (file in reportFiles) {
                try {
                    val content = file.readText()
                    val timestamp = file.lastModified()
                    
                    // 解析崩潰報告內容
                    val exceptionType = extractExceptionType(content)
                    val exceptionMessage = extractExceptionMessage(content)
                    
                    val report = CrashReport(
                        file = file,
                        content = content,
                        timestamp = timestamp,
                        exceptionType = exceptionType,
                        exceptionMessage = exceptionMessage
                    )
                    
                    crashReports.add(report)
                    
                } catch (e: Exception) {
                    Log.w(TAG, "解析崩潰報告失敗: ${file.absolutePath}", e)
                }
            }
            
            // 按時間排序，最新的在前
            crashReports.sortByDescending { it.timestamp }
            
            if (crashReports.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            } else {
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                adapter.notifyDataSetChanged()
            }
            
            Log.d(TAG, "載入了 ${crashReports.size} 個超強保證崩潰報告")
            
        } catch (e: Exception) {
            Log.e(TAG, "載入崩潰報告失敗", e)
            Toast.makeText(this, "載入崩潰報告失敗: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 從報告內容中提取異常類型
     */
    private fun extractExceptionType(content: String): String {
        val lines = content.lines()
        for (line in lines) {
            if (line.contains("異常類型:")) {
                return line.substringAfter("異常類型:").trim()
            }
        }
        return "未知異常"
    }
    
    /**
     * 從報告內容中提取異常消息
     */
    private fun extractExceptionMessage(content: String): String {
        val lines = content.lines()
        for (line in lines) {
            if (line.contains("異常消息:")) {
                val message = line.substringAfter("異常消息:").trim()
                return if (message == "無") "無異常消息" else message
            }
        }
        return "無異常消息"
    }
    
    private fun showCrashReportDetail(report: CrashReport) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_crash_report_detail, null)
        val textView = dialogView.findViewById<TextView>(R.id.textViewReportDetail)
        
        textView.text = report.content
        
        AlertDialog.Builder(this)
            .setTitle("崩潰報告詳情")
            .setView(dialogView)
            .setPositiveButton("複製") { _, _ ->
                copyToClipboard(report.content)
            }
            .setNegativeButton("刪除") { _, _ ->
                deleteCrashReport(report)
            }
            .setNeutralButton("關閉", null)
            .show()
    }
    
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("崩潰報告", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "已複製到剪貼板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "複製到剪貼板失敗", e)
            Toast.makeText(this, "複製失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteCrashReport(report: CrashReport) {
        AlertDialog.Builder(this)
            .setTitle("確認刪除")
            .setMessage("確定要刪除這個崩潰報告嗎？")
            .setPositiveButton("刪除") { _, _ ->
                try {
                    if (report.file.delete()) {
                        crashReports.remove(report)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "已刪除崩潰報告", Toast.LENGTH_SHORT).show()
                        
                        if (crashReports.isEmpty()) {
                            binding.emptyView.visibility = View.VISIBLE
                            binding.recyclerView.visibility = View.GONE
                        }
                    } else {
                        Toast.makeText(this, "刪除失敗", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "刪除崩潰報告失敗", e)
                    Toast.makeText(this, "刪除失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    fun onClearAllReports(view: View) {
        AlertDialog.Builder(this)
            .setTitle("確認清空")
            .setMessage("確定要清空所有崩潰報告嗎？此操作無法撤銷。")
            .setPositiveButton("清空") { _, _ ->
                try {
                    UltraGuaranteedCrashReporter.clearAllCrashReports(this)
                    crashReports.clear()
                    adapter.notifyDataSetChanged()
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    Toast.makeText(this, "已清空所有超強保證崩潰報告", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "清空崩潰報告失敗", e)
                    Toast.makeText(this, "清空失敗: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    fun onRefreshReports(view: View) {
        loadCrashReports()
        Toast.makeText(this, "已重新載入崩潰報告", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 崩潰報告適配器
     */
    private class CrashReportAdapter(
        private val reports: List<CrashReport>,
        private val onItemClick: (CrashReport) -> Unit
    ) : RecyclerView.Adapter<CrashReportAdapter.ViewHolder>() {
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val titleText: TextView = view.findViewById(R.id.textViewTitle)
            val dateText: TextView = view.findViewById(R.id.textViewDate)
            val exceptionText: TextView = view.findViewById(R.id.textViewException)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_crash_report, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            
            holder.titleText.text = report.shortTitle
            holder.dateText.text = report.formattedDate
            holder.exceptionText.text = "${report.exceptionType}: ${report.exceptionMessage}"
            
            holder.itemView.setOnClickListener {
                onItemClick(report)
            }
        }
        
        override fun getItemCount() = reports.size
    }
}
