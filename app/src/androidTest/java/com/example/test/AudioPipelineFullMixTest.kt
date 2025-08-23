package com.example.test

import android.content.Context
import android.media.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import com.example.videoeditor.R

@RunWith(AndroidJUnit4::class)
class AudioPipelineFullMixTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    
    @Test
    fun testVideoWithBgmMixPipeline() {
        val videoFd = context.resources.openRawResourceFd(R.raw.sample_video)
        val bgmFd = context.resources.openRawResourceFd(R.raw.sample_bgm)

        val outputFile = File(context.cacheDir, "pipeline_mix_test.mp4")
        if (outputFile.exists()) outputFile.delete()

        // --- Step 1: 提取 video + audio track ---
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFd.fileDescriptor, videoFd.startOffset, videoFd.length)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("video/")) videoTrackIndex = i
            if (mime.startsWith("audio/")) audioTrackIndex = i
        }
        assertTrue("必須有 video track", videoTrackIndex >= 0)
        assertTrue("必須有 audio track", audioTrackIndex >= 0)

        // --- Step 2: 解碼原音軌 + BGM 成 PCM ---
        val origPcm = decodeToPcm(videoExtractor, audioTrackIndex)
        val bgmExtractor = MediaExtractor()
        bgmExtractor.setDataSource(bgmFd.fileDescriptor, bgmFd.startOffset, bgmFd.length)
        val bgmTrackIndex = (0 until bgmExtractor.trackCount).first {
            bgmExtractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")
        }
        val bgmPcm = decodeToPcm(bgmExtractor, bgmTrackIndex)

        // --- Step 3: 混音 ---
        val mixedPcm = AudioMixUtils.mixPcm(origPcm, bgmPcm)

        // --- Step 4: 重新編碼 PCM → AAC ---
        val aacFile = File(context.cacheDir, "temp_audio.aac")
        encodePcmToAac(mixedPcm, aacFile)

        // --- Step 5: Mux video + AAC ---
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
        val muxVideoTrack = muxer.addTrack(videoFormat)

        val aacExtractor = MediaExtractor()
        aacExtractor.setDataSource(aacFile.absolutePath)
        val aacFormat = aacExtractor.getTrackFormat(0)
        val muxAudioTrack = muxer.addTrack(aacFormat)

        muxer.start()

        // Copy video
        val buffer = ByteBuffer.allocate(512 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        videoExtractor.selectTrack(videoTrackIndex)
        while (true) {
            bufferInfo.size = videoExtractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = videoExtractor.sampleTime
            bufferInfo.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
            videoExtractor.advance()
        }

        // Copy audio
        aacExtractor.selectTrack(0)
        while (true) {
            bufferInfo.size = aacExtractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            bufferInfo.presentationTimeUs = aacExtractor.sampleTime
            bufferInfo.flags = aacExtractor.sampleFlags
            muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
            aacExtractor.advance()
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        bgmExtractor.release()
        aacExtractor.release()

        // --- Step 6: 驗證輸出 MP4 ---
        val hasAudio = AudioPipelineTester.hasAudioTrack(outputFile.absolutePath)
        assertTrue("輸出 MP4 應包含混音後的音訊軌", hasAudio)

        val nonSilent = AudioPipelineTester.hasNonSilentAudio(outputFile.absolutePath)
        assertTrue("輸出 MP4 音軌不應該是靜音", nonSilent)

        // --- Step 7: 輸出測試報表 ---
        val report = AudioPipelineTester.exportAudioReport(context, outputFile.absolutePath)
        println("Audio mix test report at: ${report.absolutePath}")

        // --- Step 8: 比較原始 vs 輸出 ---
        // 首先複製原始影片到可存取的位置
        val originalVideoFile = File(context.cacheDir, "original_video.mp4")
        context.resources.openRawResource(R.raw.sample_video).use { input ->
            originalVideoFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        val compareReport = AudioPipelineTester.compareAudioRms(
            context,
            originalVideoFile.absolutePath,
            outputFile.absolutePath
        )
        println("Audio RMS comparison report at: ${compareReport.absolutePath}")
    }
    
    // 解碼某個 track → PCM short array
    private fun decodeToPcm(extractor: MediaExtractor, trackIndex: Int): ShortArray {
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmBuffer = ArrayList<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        val maxSamples = 48000 * 5 // 限制為 5 秒的音訊，避免記憶體問題
        var sampleCount = 0

        while (sampleCount < maxSamples) {
            val inIndex = codec.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val inputBuf = codec.getInputBuffer(inIndex)!!
                val size = extractor.readSampleData(inputBuf, 0)
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                    extractor.advance()
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)!!
                val shorts = ShortArray(bufferInfo.size / 2)
                outBuf.asShortBuffer().get(shorts)
                pcmBuffer.addAll(shorts.toList())
                sampleCount += shorts.size
                codec.releaseOutputBuffer(outIndex, false)
                
                if (sampleCount >= maxSamples) break
            }
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }

        codec.stop()
        codec.release()
        return pcmBuffer.take(maxSamples).toShortArray()
    }
    
    // 簡單 PCM → AAC 編碼
    private fun encodePcmToAac(pcm: ShortArray, outFile: File) {
        val sampleRate = 44100
        val channelCount = 1
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxerBuffer = ByteBuffer.allocate(512 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        val outStream = outFile.outputStream()

        var inputIndex = 0
        while (inputIndex < pcm.size) {
            val inIndex = codec.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val buf = codec.getInputBuffer(inIndex)!!
                buf.clear()
                val chunkSize = minOf(buf.capacity() / 2, pcm.size - inputIndex)
                for (i in 0 until chunkSize) buf.putShort(pcm[inputIndex++])
                codec.queueInputBuffer(inIndex, 0, chunkSize * 2, inputIndex.toLong() * 1_000_000 / sampleRate, 0)
            }

            var outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            while (outIndex >= 0) {
                val outBuf = codec.getOutputBuffer(outIndex)!!
                val data = ByteArray(bufferInfo.size)
                outBuf.get(data)
                outStream.write(data)
                codec.releaseOutputBuffer(outIndex, false)
                outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        codec.stop()
        codec.release()
        outStream.close()
    }
}
