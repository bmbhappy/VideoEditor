package com.example.videoeditor.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

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
}
