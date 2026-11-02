package com.stillune.app.analysis

import kotlin.math.abs

enum class SpectrumSource(val label: String) {
    STEREO("双声道能量"), LEFT("左声道 L"), RIGHT("右声道 R"), MID("中置 M · (L+R)/2"), SIDE("侧向 S · (L−R)/2")
}

data class RealtimeOptions(
    val fftSize: Int = 4096, val averageMs: Int = 300, val slopeDbOct: Int = 0,
    val source: SpectrumSource = SpectrumSource.STEREO, val historySeconds: Int = 12,
) {
    init { require(fftSize in listOf(2048, 4096, 8192)); require(averageMs in listOf(0, 150, 300, 600))
        require(slopeDbOct in listOf(0, 3, 6)); require(historySeconds in listOf(6, 12, 24)) }
}

/** Reference comparisons, not certification or playback normalization. */
enum class LoudnessTarget(val label: String, val lufs: Double?, val tolerance: Double, val peakCeiling: Double?, val note: String) {
    OFF("不启用目标", null, 0.0, null, "只显示测量值，不做目标比较。"),
    EBU("EBU R128", -23.0, .2, -1.0, "节目目标 −23 LUFS ±0.2 LU，真峰值 ≤−1 dBTP。仅比较本次连续测量片段。"),
    ATSC("ATSC A/85 · 短节目", -24.0, 2.0, -2.0, "2026 短节目参考：−24 LKFS（数值等同 LUFS），约 ±2 dB，真峰值 ≤−2 dBTP。不实现长节目对白门控。"),
    SPOTIFY("Spotify · Normal 参考", -14.0, 1.0, -1.0, "播放归一化参考 −14 LUFS；界面 ±1 LU 为本软件比较范围，不是平台交付门槛。高于 −14 LUFS 时采用 ≤−2 dBTP 建议。"),
    SPOTIFY_QUIET("Spotify · Quiet 参考", -19.0, 1.0, -1.0, "安静档播放参考 −19 LUFS，界面 ±1 LU 为本软件比较范围；不改变播放音量。"),
    CUSTOM("自定义参考", -16.0, 1.0, -1.0, "自定的目标与容差，仅供本次片段比较。"),
    ;
    fun compare(value: Double?): Int = when {
        lufs == null || value == null || !value.isFinite() -> 0
        abs(value - lufs) <= tolerance + 1e-8 -> 1
        else -> -1
    }
    fun peakLimit(integrated: Double?): Double? = if (this == SPOTIFY && integrated != null && integrated > -14) -2.0 else peakCeiling
}

data class LiveLevels(
    val peakL: Double? = null, val peakR: Double? = null, val rmsL: Double? = null, val rmsR: Double? = null,
    val holdL: Double? = null, val holdR: Double? = null,
)

data class RealtimeFrame(
    val status: String = "播放后开始测量", val error: Boolean = false, val running: Boolean = false,
    val startMs: Long = 0, val positionMs: Long = 0, val measuredMs: Long = 0,
    val sourceChannels: Int = 0, val momentary: Double? = null, val momentaryMax: Double? = null,
    val shortTerm: Double? = null, val integrated: Double? = null, val lra: Double? = null,
    val truePeak: Double? = null, val levels: LiveLevels = LiveLevels(),
    val spectrum: FloatArray = floatArrayOf(), val history: List<FloatArray> = emptyList(),
    val scatter: FloatArray = floatArrayOf(), val polar: FloatArray = FloatArray(48),
    val correlation: Double? = null, val balance: Double? = null,
    val source: SpectrumSource = SpectrumSource.STEREO,
)
