package com.example.videoeditor.engine

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 大影片檔案處理器 (最佳化 + Coroutine + Cancel + Pause/Resume + 狀態回報 支援版)
 * 使用 MediaExtractor + MediaMuxer 串流方式處理大檔案
 * 支援 300MB~500MB 甚至更大的影片檔案，降低 OOM 風險
 * 並在 IO 執行緒執行，避免阻塞 UI，可中途取消/暫停/繼續，支援狀態追蹤
 */
class LargeVideoProcessor {
    companion object {
        private const val TAG = "LargeVideoProcessor"
        private const val BUFFER_SIZE = 256 * 1024 // 256KB 緩衝區，避免 OOM
    }

    enum class State {
        IDLE, PROCESSING, PAUSED, COMPLETED, CANCELLED, ERROR
    }

    private val isCancelled = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    @Volatile private var currentState: State = State.IDLE

    private var stateCallback: ((State) -> Unit)? = null

    fun setStateCallback(callback: (State) -> Unit) {
        stateCallback = callback
    }

    private fun updateState(newState: State) {
        currentState = newState
        Log.i(TAG, "狀態變更為: $newState")
        stateCallback?.invoke(newState)
    }

    fun getState(): State = currentState

    /**
     * 取消處理
     */
    fun cancelProcessing() {
        isCancelled.set(true)
        updateState(State.CANCELLED)
        Log.w(TAG, "已請求取消影片處理")
    }

    /**
     * 暫停處理
     */
    fun pauseProcessing() {
        isPaused.set(true)
        updateState(State.PAUSED)
        Log.w(TAG, "已暫停影片處理")
    }

    /**
     * 繼續處理
     */
    fun resumeProcessing() {
        isPaused.set(false)
        updateState(State.PROCESSING)
        Log.i(TAG, "已恢復影片處理")
    }

    /**
     * 使用協程處理大影片檔案 (非阻塞 UI，可取消/暫停/繼續)
     */
    suspend fun processLargeVideo(
        inputPath: String,
        outputPath: String,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            isCancelled.set(false)
            isPaused.set(false)
            updateState(State.PROCESSING)

            Log.d(TAG, "開始處理大影片檔案: $inputPath")
            Log.d(TAG, "檔案大小: ${File(inputPath).length() / (1024 * 1024)}MB")

            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            Log.d(TAG, "軌道數量: ${extractor.trackCount}")

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                Log.d(TAG, "軌道 $i: $mime")

                when {
                    mime?.startsWith("video/") == true -> {
                        videoTrackIndex = i
                        videoFormat = format
                        Log.d(TAG, "找到影片軌道: $i")
                    }
                    mime?.startsWith("audio/") == true -> {
                        audioTrackIndex = i
                        audioFormat = format
                        Log.d(TAG, "找到音訊軌道: $i")
                    }
                }
            }

            require(videoTrackIndex >= 0) { "找不到影片軌道" }

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val outVideoTrack = muxer.addTrack(videoFormat!!)
            Log.d(TAG, "添加影片軌道到輸出: $outVideoTrack")

            var outAudioTrack = -1
            if (audioTrackIndex >= 0) {
                outAudioTrack = muxer.addTrack(audioFormat!!)
                Log.d(TAG, "添加音訊軌道到輸出: $outAudioTrack")
            }

            muxer.start()
            Log.d(TAG, "MediaMuxer 已開始")

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()

            val totalDurationUs = videoFormat.getLong(MediaFormat.KEY_DURATION)
            Log.d(TAG, "總時長: ${totalDurationUs / 1000000}秒")

            var processedSamples = 0L
            var lastProgressTime = 0L

            while (true) {
                if (isCancelled.get()) {
                    Log.w(TAG, "處理被取消")
                    updateState(State.CANCELLED)
                    return@withContext false
                }

                while (isPaused.get()) {
                    Thread.sleep(200)
                }

                buffer.clear()
                info.offset = 0

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    Log.d(TAG, "已讀取完所有樣本")
                    break
                }

                if (size > buffer.capacity()) {
                    Log.w(TAG, "樣本大小超過緩衝區，跳過該幀: $size bytes")
                    extractor.advance()
                    continue
                }

                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags

                val trackIndex = extractor.sampleTrackIndex

                when (trackIndex) {
                    videoTrackIndex -> {
                        muxer.writeSampleData(outVideoTrack, buffer, info)
                        processedSamples++

                        val currentTime = info.presentationTimeUs
                        if (currentTime - lastProgressTime > 5_000_000) {
                            val progress = (currentTime.toFloat() / totalDurationUs) * 100
                            progressCallback?.invoke(progress.coerceIn(0f, 100f))
                            lastProgressTime = currentTime
                            Log.d(TAG, "處理進度: ${String.format("%.1f", progress)}%")
                        }
                    }
                    audioTrackIndex -> {
                        if (outAudioTrack >= 0) {
                            muxer.writeSampleData(outAudioTrack, buffer, info)
                        }
                    }
                }

                extractor.advance()
            }

            Log.d(TAG, "處理完成，總共處理 $processedSamples 個樣本")
            progressCallback?.invoke(100f)
            updateState(State.COMPLETED)

            true

        } catch (e: Exception) {
            Log.e(TAG, "處理大影片檔案時發生錯誤", e)
            updateState(State.ERROR)
            false

        } finally {
            try {
                muxer?.stop()
                muxer?.release()
                extractor?.release()
                Log.d(TAG, "資源清理完成")
                if (currentState != State.CANCELLED && currentState != State.ERROR && currentState != State.COMPLETED) {
                    updateState(State.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理資源時發生錯誤", e)
            }
        }
    }

    /**
     * 裁剪大影片檔案
     */
    suspend fun trimLargeVideo(
        inputPath: String,
        outputPath: String,
        startTimeMs: Long,
        endTimeMs: Long,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            isCancelled.set(false)
            isPaused.set(false)
            updateState(State.PROCESSING)

            Log.d(TAG, "開始裁剪大影片檔案: $inputPath")
            Log.d(TAG, "裁剪時間: ${startTimeMs}ms - ${endTimeMs}ms")

            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            // 檢查影片總長度
            val totalDurationUs = extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION)
            val totalDurationMs = totalDurationUs / 1000

            if (endTimeMs > totalDurationMs) {
                Log.e(TAG, "結束時間超過影片總長度")
                updateState(State.ERROR)
                return@withContext false
            }

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                when {
                    mime?.startsWith("video/") == true -> {
                        videoTrackIndex = i
                        videoFormat = format
                    }
                    mime?.startsWith("audio/") == true -> {
                        audioTrackIndex = i
                        audioFormat = format
                    }
                }
            }

            require(videoTrackIndex >= 0) { "找不到影片軌道" }

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val outVideoTrack = muxer.addTrack(videoFormat!!)
            var outAudioTrack = -1
            if (audioTrackIndex >= 0) {
                outAudioTrack = muxer.addTrack(audioFormat!!)
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()

            // 定位到開始時間
            extractor.seekTo(startTimeMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val targetEndTimeUs = endTimeMs * 1000
            var processedSamples = 0L

            while (true) {
                if (isCancelled.get()) {
                    Log.w(TAG, "裁剪被取消")
                    updateState(State.CANCELLED)
                    return@withContext false
                }

                while (isPaused.get()) {
                    Thread.sleep(200)
                }

                buffer.clear()
                info.offset = 0

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    Log.d(TAG, "已讀取完所有樣本")
                    break
                }

                val currentTimeUs = extractor.sampleTime
                if (currentTimeUs >= targetEndTimeUs) {
                    Log.d(TAG, "已達到目標結束時間")
                    break
                }

                info.size = size
                val adjustedTimeUs = currentTimeUs - (startTimeMs * 1000)
                info.presentationTimeUs = if (adjustedTimeUs < 0) 0 else adjustedTimeUs
                info.flags = extractor.sampleFlags

                val trackIndex = extractor.sampleTrackIndex

                when (trackIndex) {
                    videoTrackIndex -> {
                        muxer.writeSampleData(outVideoTrack, buffer, info)
                        processedSamples++

                        val progress = ((currentTimeUs - startTimeMs * 1000).toFloat() / (targetEndTimeUs - startTimeMs * 1000)) * 100
                        progressCallback?.invoke(progress.coerceIn(0f, 100f))
                    }
                    audioTrackIndex -> {
                        if (outAudioTrack >= 0) {
                            muxer.writeSampleData(outAudioTrack, buffer, info)
                        }
                    }
                }

                extractor.advance()
            }

            Log.d(TAG, "裁剪完成，總共處理 $processedSamples 個樣本")
            progressCallback?.invoke(100f)
            updateState(State.COMPLETED)

            true

        } catch (e: Exception) {
            Log.e(TAG, "裁剪大影片檔案時發生錯誤", e)
            updateState(State.ERROR)
            false

        } finally {
            try {
                muxer?.stop()
                muxer?.release()
                extractor?.release()
                Log.d(TAG, "資源清理完成")
                if (currentState != State.CANCELLED && currentState != State.ERROR && currentState != State.COMPLETED) {
                    updateState(State.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理資源時發生錯誤", e)
            }
        }
    }

    /**
     * 變更大影片檔案速度
     */
    suspend fun changeLargeVideoSpeed(
        inputPath: String,
        outputPath: String,
        speedFactor: Float,
        progressCallback: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null

        try {
            isCancelled.set(false)
            isPaused.set(false)
            updateState(State.PROCESSING)

            Log.d(TAG, "開始變更大影片檔案速度: $inputPath")
            Log.d(TAG, "速度因子: $speedFactor")

            extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                when {
                    mime?.startsWith("video/") == true -> {
                        videoTrackIndex = i
                        videoFormat = format
                    }
                    mime?.startsWith("audio/") == true -> {
                        audioTrackIndex = i
                        audioFormat = format
                    }
                }
            }

            require(videoTrackIndex >= 0) { "找不到影片軌道" }

            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val outVideoTrack = muxer.addTrack(videoFormat!!)
            var outAudioTrack = -1
            if (audioTrackIndex >= 0) {
                outAudioTrack = muxer.addTrack(audioFormat!!)
            }

            muxer.start()

            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()

            val totalDurationUs = videoFormat.getLong(MediaFormat.KEY_DURATION)
            var processedSamples = 0L
            var lastProgressTime = 0L

            while (true) {
                if (isCancelled.get()) {
                    Log.w(TAG, "速度變更被取消")
                    updateState(State.CANCELLED)
                    return@withContext false
                }

                while (isPaused.get()) {
                    Thread.sleep(200)
                }

                buffer.clear()
                info.offset = 0

                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    Log.d(TAG, "已讀取完所有樣本")
                    break
                }

                info.size = size
                // 調整 PTS 來改變速度
                info.presentationTimeUs = (extractor.sampleTime / speedFactor).toLong()
                info.flags = extractor.sampleFlags

                val trackIndex = extractor.sampleTrackIndex

                when (trackIndex) {
                    videoTrackIndex -> {
                        muxer.writeSampleData(outVideoTrack, buffer, info)
                        processedSamples++

                        val currentTime = info.presentationTimeUs
                        if (currentTime - lastProgressTime > 5_000_000) {
                            val progress = (currentTime.toFloat() / (totalDurationUs / speedFactor)) * 100
                            progressCallback?.invoke(progress.coerceIn(0f, 100f))
                            lastProgressTime = currentTime
                        }
                    }
                    audioTrackIndex -> {
                        if (outAudioTrack >= 0) {
                            muxer.writeSampleData(outAudioTrack, buffer, info)
                        }
                    }
                }

                extractor.advance()
            }

            Log.d(TAG, "速度變更完成，總共處理 $processedSamples 個樣本")
            progressCallback?.invoke(100f)
            updateState(State.COMPLETED)

            true

        } catch (e: Exception) {
            Log.e(TAG, "變更大影片檔案速度時發生錯誤", e)
            updateState(State.ERROR)
            false

        } finally {
            try {
                muxer?.stop()
                muxer?.release()
                extractor?.release()
                Log.d(TAG, "資源清理完成")
                if (currentState != State.CANCELLED && currentState != State.ERROR && currentState != State.COMPLETED) {
                    updateState(State.IDLE)
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理資源時發生錯誤", e)
            }
        }
    }

    /**
     * 檢查是否適合大檔案處理
     */
    fun isSuitableForLargeFileProcessing(filePath: String): Boolean {
        val file = File(filePath)
        val fileSizeMB = file.length() / (1024 * 1024)
        return fileSizeMB > 100
    }

    /**
     * 獲取檔案信息
     */
    fun getFileInfo(filePath: String): FileInfo? {
        var extractor: MediaExtractor? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(filePath)

            val file = File(filePath)
            val fileSizeMB = file.length() / (1024 * 1024)

            var videoDuration = 0L
            var hasAudio = false

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)

                when {
                    mime?.startsWith("video/") == true -> {
                        videoDuration = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    mime?.startsWith("audio/") == true -> {
                        hasAudio = true
                    }
                }
            }

            return FileInfo(
                sizeMB = fileSizeMB,
                durationSeconds = videoDuration / 1000000,
                hasAudio = hasAudio,
                trackCount = extractor.trackCount
            )

        } catch (e: Exception) {
            Log.e(TAG, "獲取檔案信息時發生錯誤", e)
            return null

        } finally {
            extractor?.release()
        }
    }

    data class FileInfo(
        val sizeMB: Long,
        val durationSeconds: Long,
        val hasAudio: Boolean,
        val trackCount: Int
    )
}
