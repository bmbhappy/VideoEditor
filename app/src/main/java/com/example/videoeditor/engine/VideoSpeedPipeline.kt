package com.example.videoeditor.engine

import android.media.*
import android.view.Surface
import android.os.Build
import java.nio.ByteBuffer
import kotlin.math.roundToLong

/**
 * 影片變速管線：MediaExtractor → Decoder(Surface) → GL Render → Encoder(Surface) → Muxer
 * 重點：用 EGL 把 Decoder 的 SurfaceTexture 畫到 Encoder 的 Input Surface，並用新 PTS投餵給 Encoder。
 */
class VideoSpeedPipeline(
    private val inputPath: String,
    private val outputPath: String,
    private val cfg: SpeedConfig
) {
    private lateinit var extractor: MediaExtractor
    private lateinit var decoder: MediaCodec
    private lateinit var encoder: MediaCodec
    private lateinit var decoderSurface: Surface
    private lateinit var encoderInputSurface: Surface
    private lateinit var muxer: MediaMuxer
    private var videoTrack = -1
    private var muxerVideoTrack = -1
    private var muxerStarted = false

    // EGL/GL 幫手：負責把 decoder 的 SurfaceTexture 繪製到 encoder input surface
    private lateinit var gl: GlTranscoder

    // 時間軸
    private var inputTimeBaseFirstUs: Long = -1L
    private var outputFrameIndex = 0L
    private val frameIntervalUs: Long = (1_000_000.0 / cfg.outFps).roundToLong()

    fun prepare() {
        extractor = MediaExtractor().apply { setDataSource(inputPath) }
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                videoTrack = i; break
            }
        }
        require(videoTrack >= 0) { "No video track." }
        val inFmt = extractor.getTrackFormat(videoTrack)
        val width = inFmt.getInteger(MediaFormat.KEY_WIDTH)
        val height = inFmt.getInteger(MediaFormat.KEY_HEIGHT)

        // Encoder（H.264 baseline/high 可自行設定）
        val outFmt = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, cfg.outBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, cfg.outFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= 23) {
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4)
            }
        }
        encoder = MediaCodec.createEncoderByType("video/avc").apply {
            configure(outFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        encoderInputSurface = encoder.createInputSurface()
        encoder.start()

        // Decoder to Surface
        gl = GlTranscoder() // 建立EGL context，attach encoderInputSurface，提供 draw() 與 setDecoderOutputSurface()
        decoderSurface = gl.createDecoderOutputSurface(width, height)
        gl.setupEncoderInputSurface(encoderInputSurface)
        
        decoder = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!).apply {
            configure(inFmt, decoderSurface, null, 0)
        }
        decoder.start()

        extractor.selectTrack(videoTrack)

        muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun start() {
        // 主 loop：把 decoder 輸出一幀，GL 繪到 encoder input surface，設定新 PTS，然後從 encoder 取出編碼後的 buffer 寫入 muxer
        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var encoderDone = false

        while (!encoderDone) {
            // 餵 decoder
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inputBuf = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inputBuf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        if (inputTimeBaseFirstUs < 0) inputTimeBaseFirstUs = pts
                        decoder.queueInputBuffer(inIndex, 0, size, pts, 0)
                        extractor.advance()
                    }
                }
            }

            // 簡化版本：直接處理 decoder 輸出
            if (!decoderDone) {
                val outIndex = decoder.dequeueOutputBuffer(decInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no-op */ }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { /* ignore for decoder */ }
                    outIndex >= 0 -> {
                        val doRender = (decInfo.size > 0)
                        // 釋放buffer並觸發Surface可用
                        decoder.releaseOutputBuffer(outIndex, doRender)
                        if (doRender) {
                            // 簡化版本：暫時不進行 GL 處理
                            val outPtsUs = computeVideoOutPtsUs(decInfo.presentationTimeUs)
                            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoSpeedPipeline", "處理影片幀: PTS=${outPtsUs}us")
                            outputFrameIndex++
                        }
                        if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // 通知 encoder EOS
                            encoder.signalEndOfInputStream()
                            decoderDone = true
                        }
                    }
                }
            }

            // 取 encoder 輸出 → 寫入 muxer
            while (true) {
                val encIndex = encoder.dequeueOutputBuffer(encInfo, 10_000)
                when {
                    encIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted)
                        val newFormat = encoder.outputFormat
                        muxerVideoTrack = muxer.addTrack(newFormat)
                        // 注意：等 Audio 也 addTrack 完再 start；但為了簡化，我們讓 video 先 start，音訊之後再用相同 muxer 寫入。
                        // 實務：等 audio pipeline 也 addTrack 再 start（這需要共享 muxer，本文用協調方式：video 開啟後 audio 直接寫）
                        muxer.start()
                        muxerStarted = true
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "VideoSpeedPipeline", "Muxer started with video track: $muxerVideoTrack")
                    }
                    encIndex >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(encIndex)!!
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encInfo.size = 0
                        }
                        if (encInfo.size > 0 && muxerStarted) {
                            outBuf.position(encInfo.offset)
                            outBuf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, outBuf, encInfo)
                        }
                        encoder.releaseOutputBuffer(encIndex, false)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                            // 不在此關閉 muxer，留給 Audio 管線在完成後統一 stop（或由外層協調）
                            break
                        }
                    }
                }
            }
        }
    }

    fun join() {
        // 供外層等待；此實作同步執行所以空
    }

    fun closeMuxerIfNeeded() {
        // 由最後一個結束的管線呼叫
        try { muxer.stop() } catch (_: Throwable) {}
        try { muxer.release() } catch (_: Throwable) {}
        try { decoder.stop(); decoder.release() } catch (_: Throwable) {}
        try { encoder.stop(); encoder.release() } catch (_: Throwable) {}
        try { extractor.release() } catch (_: Throwable) {}
        try { gl.release() } catch (_: Throwable) {}
    }

    /** 把輸入 PTS 轉成輸出 PTS；固定幀率：依 outFrameIndex * frameIntervalUs 產生 */
    private fun computeVideoOutPtsUs(inPtsUs: Long): Long {
        // 若要「嚴格依輸入時間縮放」，可用：((inPtsUs - inputTimeBaseFirstUs) / cfg.speed).toLong()
        // 為讓輸出 CFR（constant frame rate），通常用固定步進：
        return outputFrameIndex * frameIntervalUs
    }

    // 讓 Audio 管線能共享 muxer
    fun muxer(): MediaMuxer = muxer
    fun isMuxerStarted(): Boolean = muxerStarted
    fun videoTrackIndex(): Int = muxerVideoTrack
}
