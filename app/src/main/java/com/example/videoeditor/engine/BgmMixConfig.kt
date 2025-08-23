package com.example.videoeditor.engine

/**
 * 背景音樂混音配置
 * 支援原影片無損拷貝、PCM混音、BGM循環、音量控制和Ducking功能
 */
data class BgmMixConfig(
    val mainVolume: Float = 1.0f,         // 原影片音量比例 0.0~1.0
    val bgmVolume: Float = 0.4f,          // BGM 音量比例 0.0~1.0
    val bgmStartOffsetUs: Long = 0L,      // BGM 開始時間偏移 (us)
    val bgmEndOffsetUs: Long = 0L,        // BGM 結束時間偏移 (us) - 0表示不裁剪
    val loopBgm: Boolean = true,          // BGM 是否循環
    val lengthAdjustMode: String = "LOOP", // 長度調整模式: LOOP, TRIM, STRETCH, FADE_OUT
    val enableDucking: Boolean = false,   // 開啟 Ducking
    val duckingThreshold: Float = 0.08f,  // 主音 RMS 門檻
    val duckingAttenuation: Float = 0.4f, // Ducking 時 BGM 衰減倍率
    val outSampleRate: Int = 48000,
    val outChannelCount: Int = 2,
    val outAacBitrate: Int = 128_000
)
