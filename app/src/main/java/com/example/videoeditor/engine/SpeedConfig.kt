package com.example.videoeditor.engine

/**
 * 變速配置
 * 設計目標：任何播放器都一致（因為是「實際重取樣後重新編碼」）
 */
data class SpeedConfig(
    val speed: Float,              // 0.25 ~ 4.0
    val keepAudioPitch: Boolean = false,   // true=用DSP保持音高
    val outFps: Int = 30,          // 目標固定幀率
    val outBitrate: Int = 8_000_000, // H.264 bitrate
    val outAacBitrate: Int = 128_000,
    val outSampleRate: Int = 48000,
    val outChannelCount: Int = 2
)
