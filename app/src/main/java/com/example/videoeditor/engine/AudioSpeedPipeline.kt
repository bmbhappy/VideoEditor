package com.example.videoeditor.engine

import android.media.*
import java.nio.ByteBuffer

/**
 * 音訊變速管線：MediaExtractor → Decoder(PCM) → [可選：Sonic DSP] → Encoder(AAC) → Muxer
 */
class AudioSpeedPipeline(
    private val inputPath: String,
    private val outputPath: String,
    private val cfg: SpeedConfig,
    private val video: VideoSpeedPipeline
) {
    private lateinit var extractor: MediaExtractor
    private var audioTrack = -1

    private lateinit var decoder: MediaCodec
    private lateinit var encoder: MediaCodec
    private var muxerAudioTrack = -1

    private var muxer: MediaMuxer? = null

    fun prepare() {
        extractor = MediaExtractor().apply { setDataSource(inputPath) }
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrack = i; break
            }
        }
        if (audioTrack < 0) {
            com.example.videoeditor.utils.LogDisplayManager.addLog("W", "AudioSpeedPipeline", "No audio track found")
            return // 無音訊也可
        }

        val inFmt = extractor.getTrackFormat(audioTrack)
        val inMime = inFmt.getString(MediaFormat.KEY_MIME)!!
        extractor.selectTrack(audioTrack)

        decoder = MediaCodec.createDecoderByType(inMime).apply {
            configure(inFmt, null, null, 0); start()
        }

        // AAC Encoder
        val outFmt = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            cfg.outSampleRate, cfg.outChannelCount
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, cfg.outAacBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(outFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start()
        }

        muxer = video.muxer() // 共用同一個 muxer
        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "AudioSpeedPipeline", "Audio pipeline prepared")
    }

    fun start() {
        if (audioTrack < 0) {
            // 無音訊：直接關閉 muxer
            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "AudioSpeedPipeline", "No audio track, closing muxer")
            video.closeMuxerIfNeeded()
            return
        }

        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var decoderDone = false
        var encoderDone = false

        // 可選 DSP：保留音高（目前不使用 Sonic，因為需要額外依賴）
        val sonic: Any? = null // if (cfg.keepAudioPitch) { /* Sonic implementation */ } else null

        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "AudioSpeedPipeline", "Starting audio processing")

        while (!encoderDone) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inputBuf = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(inputBuf, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        decoder.queueInputBuffer(inIndex, 0, size, pts, 0)
                        extractor.advance()
                    }
                }
            }

            if (!decoderDone) {
                val outIndex = decoder.dequeueOutputBuffer(decInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> { /* no-op */ }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {}
                    outIndex >= 0 -> {
                        val outBuf = decoder.getOutputBuffer(outIndex)!!
                        if (decInfo.size > 0) {
                            outBuf.position(decInfo.offset)
                            outBuf.limit(decInfo.offset + decInfo.size)

                            val pcm = ByteArray(decInfo.size)
                            outBuf.get(pcm)

                            val processedPcm: ByteArray = if (sonic != null) {
                                // Sonic DSP 處理（保留音高）
                                pcm // 暫時直接使用原始 PCM
                            } else {
                                // 不保留音高：直接縮放 PTS（最簡單）
                                pcm
                            }

                            // 餵進 AAC encoder
                            var offset = 0
                            while (offset < processedPcm.size) {
                                val inEncIdx = encoder.dequeueInputBuffer(10_000)
                                if (inEncIdx >= 0) {
                                    val inEncBuf = encoder.getInputBuffer(inEncIdx)!!
                                    inEncBuf.clear()
                                    val toCopy = minOf(inEncBuf.remaining(), processedPcm.size - offset)
                                    inEncBuf.put(processedPcm, offset, toCopy)
                                    offset += toCopy

                                    val outPtsUs = scaleAudioPts(decInfo.presentationTimeUs, cfg.speed, cfg.keepAudioPitch)
                                    encoder.queueInputBuffer(
                                        inEncIdx, 0, toCopy, outPtsUs, 0
                                    )
                                } else break
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // flush sonic 殘留（如果使用）
                            if (sonic != null) {
                                // Sonic flush 處理
                            }
                            // 送 EOS
                            val inEncIdx = encoder.dequeueInputBuffer(10_000)
                            if (inEncIdx >= 0) {
                                encoder.queueInputBuffer(inEncIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            decoderDone = true
                        }
                    }
                }
            }

            // 取出 AAC，寫進 muxer
            while (true) {
                val encIndex = encoder.dequeueOutputBuffer(encInfo, 10_000)
                when {
                    encIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                    encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val fmt = encoder.outputFormat
                        muxerAudioTrack = video.muxer().addTrack(fmt)
                        com.example.videoeditor.utils.LogDisplayManager.addLog("D", "AudioSpeedPipeline", "Added audio track to muxer: $muxerAudioTrack")
                        // 若 video 還沒 start，就等；本文 video 先 start，這裡可直接寫
                    }
                    encIndex >= 0 -> {
                        val buf = encoder.getOutputBuffer(encIndex)!!
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            encInfo.size = 0
                        }
                        if (encInfo.size > 0 && video.isMuxerStarted()) {
                            buf.position(encInfo.offset)
                            buf.limit(encInfo.offset + encInfo.size)
                            video.muxer().writeSampleData(muxerAudioTrack, buf, encInfo)
                        }
                        encoder.releaseOutputBuffer(encIndex, false)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                            // 兩邊都結束 → 關閉 muxer
                            com.example.videoeditor.utils.LogDisplayManager.addLog("D", "AudioSpeedPipeline", "Audio processing completed, closing muxer")
                            video.closeMuxerIfNeeded()
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
}

/** 音訊 PTS 縮放：保留音高時，音訊內容被 time-stretch，PTS 仍需按 speed 縮放以匹配新時長 */
private fun scaleAudioPts(inPtsUs: Long, speed: Float, keepPitch: Boolean): Long {
    // 一般做法：輸出時長 = 輸入時長 / speed（2x更短），PTS 亦縮放
    return (inPtsUs / speed).toLong()
}
