package com.stillune.app.immersion

import kotlin.math.*

/** Time-based smoothing behaves consistently at different sensor and display rates. */
class LowPass(private val timeConstant: Float = .18f) {
    var value = 0f
        private set
    fun update(target: Float, seconds: Float): Float {
        if (!target.isFinite()) return value
        val alpha = 1f - exp(-seconds.coerceIn(0f, .25f) / timeConstant)
        value += alpha * (target - value)
        return value
    }
}

object WaveformEnergy {
    /** Android Visualizer waveforms are unsigned 8-bit PCM, centred at 128. */
    fun rms(waveform: ByteArray): Float {
        if (waveform.isEmpty()) return 0f
        var squares = 0.0
        for (sample in waveform) {
            val value = ((sample.toInt() and 255) - 128) / 128.0
            squares += value * value
        }
        return sqrt(squares / waveform.size).toFloat().coerceIn(0f, 1f)
    }
}

/** Three nearby taps, never three unrelated clicks over a long session. */
class TripleTapGate {
    private var previous = Long.MIN_VALUE
    private var count = 0
    fun tap(timeMs: Long): Boolean {
        count = if (previous != Long.MIN_VALUE && timeMs - previous in 0..360) count + 1 else 1
        previous = timeMs
        if (count < 3) return false
        count = 0
        previous = Long.MIN_VALUE
        return true
    }
}

enum class ImmersionStyle(val title: String) {
    PARALLAX("封面视差"), RIPPLES("节奏波纹"), PARTICLES("节奏粒子")
}

data class Tilt(val x: Float = 0f, val y: Float = 0f, val motion: Float = 0f, val available: Boolean = false,
    val shakeX: Float = 0f, val shakeY: Float = 0f)
data class AudioEnergy(val rms: Float = 0f, val available: Boolean = false, val message: String? = null)
