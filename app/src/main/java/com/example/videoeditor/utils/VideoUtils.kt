package com.example.videoeditor.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import android.webkit.MimeTypeMap
import android.media.MediaExtractor
import android.media.MediaFormat

object VideoUtils {
    
    private const val TAG = "VideoUtils"
    
    fun getPathFromUri(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    val cursor: Cursor? = context.contentResolver.query(
                        uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                            it.getString(columnIndex)
                        } else {
                            null
                        }
                    }
                }
                ContentResolver.SCHEME_FILE -> uri.path
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting path from URI: ${e.message}")
            null
        }
    }
    
    fun copyFileToInternalStorage(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputFile = File(context.filesDir, fileName)
            
            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.d(TAG, "File copied to: ${outputFile.absolutePath}")
            outputFile
        } catch (e: IOException) {
            Log.e(TAG, "Error copying file: ${e.message}")
            null
        }
    }

    /**
     * 將任意 content:// 或 file:// 的 Uri 解析為本機可用的檔案路徑。
     * 策略：
     * - 若為 file:// 直接回傳路徑
     * - 若為 content://，就以 ContentResolver 讀取並複製到 App 私有目錄（外部或內部），回傳複製後路徑
     */
    fun resolveToLocalFilePath(
        context: Context,
        uri: Uri,
        defaultNamePrefix: String = "import",
        fallbackExt: String = "dat"
    ): String? {
        return try {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> uri.path
                ContentResolver.SCHEME_CONTENT -> {
                    val resolver = context.contentResolver
                    var displayName: String? = null
                    var ext: String? = null

                    // 嘗試讀取檔名與 MIME
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) displayName = c.getString(idx)
                        }
                    }
                    val mime = resolver.getType(uri)
                    if (!mime.isNullOrEmpty()) {
                        ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    }

                    if (displayName.isNullOrEmpty()) {
                        val ts = System.currentTimeMillis()
                        val suffix = (ext ?: fallbackExt)
                        displayName = "${defaultNamePrefix}_${ts}.${suffix}"
                    } else if (!displayName!!.contains('.')) {
                        // 若沒有副檔名則補上
                        val suffix = (ext ?: fallbackExt)
                        displayName = "$displayName.$suffix"
                    }

                    val targetDir = getAppFilesDirectory(context)
                    val outFile = File(targetDir, displayName!!)
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "URI resolved to local file: ${outFile.absolutePath}")
                    outFile.absolutePath
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve uri to local file: ${e.message}")
            null
        }
    }
    
    fun getVideoDuration(context: Context, uri: Uri): Long {
        return try {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.DURATION),
                null,
                null,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    it.getLong(columnIndex)
                } else {
                    0L
                }
            } ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video duration: ${e.message}")
            0L
        }
    }
    
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    fun createOutputFileName(prefix: String, extension: String = "mp4"): String {
        val timestamp = System.currentTimeMillis()
        return "${prefix}_${timestamp}.$extension"
    }
    
    fun getAppFilesDirectory(context: Context): File {
        // 優先使用外部儲存目錄，與處理輸出一致；若不可用則回退到內部目錄
        return context.getExternalFilesDir(null) ?: context.filesDir
    }
    
    fun getAppCacheDirectory(context: Context): File {
        return context.cacheDir
    }
    
    fun listVideoFiles(context: Context): List<File> {
        val primaryDir = getAppFilesDirectory(context)
        val internalDir = context.filesDir
        val all = mutableListOf<File>()
        val filter: (File) -> Boolean = { file ->
            file.isFile && file.extension.lowercase() in listOf("mp4", "mov", "avi", "mkv")
        }
        primaryDir.listFiles(filter)?.let { all.addAll(it) }
        if (primaryDir.absolutePath != internalDir.absolutePath) {
            internalDir.listFiles(filter)?.let { all.addAll(it) }
        }
        return all.sortedByDescending { it.lastModified() }
    }
    
    fun deleteFile(file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "File deleted: ${file.absolutePath}")
            } else {
                Log.w(TAG, "Failed to delete file: ${file.absolutePath}")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${e.message}")
            false
        }
    }
    
    /**
     * 獲取影片時長 (毫秒)
     */
    fun getVideoDuration(videoPath: String): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(videoPath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    extractor.release()
                    return durationUs / 1000 // 轉換為毫秒
                }
            }
            
            extractor.release()
            0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting video duration: ${e.message}")
            0L
        }
    }
    
    /**
     * 獲取音訊時長 (毫秒)
     */
    fun getAudioDuration(audioPath: String): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(audioPath)
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    extractor.release()
                    return durationUs / 1000 // 轉換為毫秒
                }
            }
            
            extractor.release()
            0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting audio duration: ${e.message}")
            0L
        }
    }
    
    /**
     * 獲取媒體檔案時長 (毫秒) - 自動判斷音訊或影片
     */
    fun getMediaDuration(mediaPath: String): Long {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(mediaPath)
            
            var longestDuration = 0L
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    longestDuration = maxOf(longestDuration, durationUs)
                }
            }
            
            extractor.release()
            longestDuration / 1000 // 轉換為毫秒
        } catch (e: Exception) {
            Log.e(TAG, "Error getting media duration: ${e.message}")
            0L
        }
    }
}
