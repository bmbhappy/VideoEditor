package com.example.videoeditor.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

object GalleryUtils {
    
    private const val TAG = "GalleryUtils"
    
    /**
     * 將影片檔案保存到系統相簿
     */
    fun saveVideoToGallery(context: Context, videoFilePath: String, fileName: String): Boolean {
        return try {
            val videoFile = File(videoFilePath)
            if (!videoFile.exists()) {
                Log.e(TAG, "影片檔案不存在: $videoFilePath")
                return false
            }
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoEditor")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            
            val uri = context.contentResolver.insert(collection, contentValues)
            uri?.let { videoUri ->
                context.contentResolver.openOutputStream(videoUri)?.use { outputStream ->
                    FileInputStream(videoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(videoUri, contentValues, null, null)
                }
                
                Log.d(TAG, "影片已保存到相簿: $videoUri")
                return true
            }
            
            Log.e(TAG, "無法創建相簿條目")
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "保存影片到相簿失敗: ${e.message}")
            false
        }
    }
    
    /**
     * 檢查是否有權限保存到相簿
     */
    fun hasGalleryPermission(context: Context): Boolean {
        return try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            
            context.contentResolver.query(collection, null, null, null, null)?.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "沒有相簿權限: ${e.message}")
            false
        }
    }
}
