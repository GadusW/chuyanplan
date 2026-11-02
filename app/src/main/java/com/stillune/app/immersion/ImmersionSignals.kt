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
data class AudioEnergy(val rms: Float = 0f, val available: Boolean = false, val message: String? = null,
    val level: Float = 0f, val onset: Float = 0f, val beatId: Long = 0L, val capturedNanos: Long = 0L)

/** Sample-domain transient detector, not a BPM clock. Never invents beats between samples. */
class RhythmEnvelope {
    private var average = 0f
    private var previous = 0f
    private var lastBeat = -1f
    private var elapsed = 0f
    private var serial = 0L
    private var lastStrength = 0f
    private var peak = .01f
    private var initialized = false

    fun update(rms: Float, seconds: Float, capturedNanos: Long = 0L): AudioEnergy {
        val dt = seconds.coerceIn(.001f, .15f)
        val raw = if (rms.isFinite()) rms.coerceIn(0f, 1f) else 0f
        elapsed += dt
        peak = maxOf(raw, peak * exp(-dt / 2f), .002f)
        // A bounded adaptive reference retains contrast in both quiet and compressed music.
        val level = if (raw < .0003f) 0f else sqrt(raw / peak).coerceIn(0f, 1f)
        val contrast = ((raw - average) / maxOf(average, peak * .12f, .001f)).coerceAtLeast(0f)
        val rise = ((raw - previous) / maxOf(peak, .002f)).coerceAtLeast(0f)
        val impulse = (contrast * .48f + rise * 1.6f).coerceIn(0f, 1f)
        val hit = initialized && raw > .0008f && rise > .04f && impulse > .27f && elapsed - lastBeat > .16f
        if (hit) { serial++; lastBeat = elapsed; lastStrength = impulse }
        average += (1f - exp(-dt / .42f)) * (raw - average)
        previous = raw
        initialized = true
        return AudioEnergy(raw, true, level = level, onset = lastStrength,
            beatId = serial, capturedNanos = capturedNanos)
    }
}

/** Adaptive sensor filter: quiet hands stay steady, deliberate movement has less lag. */
class ResponsiveTilt {
    private var value = 0f
    fun update(target: Float, seconds: Float): Float {
        if (!target.isFinite()) return value
        val dt = seconds.coerceIn(.001f, .1f)
        val speed = abs(target - value) / dt
        val tau = (.065f / (1f + speed * .32f)).coerceAtLeast(.018f)
        value += (1f - exp(-dt / tau)) * (target - value)
        return value
    }
}

/** Express rotation in the entry pose's basis, avoiding Euler flips near upright holding. */
class PoseReference {
    private var origin: FloatArray? = null
    private val relative = FloatArray(9)
    fun rotation(matrix: FloatArray): FloatArray {
        val base = origin ?: matrix.copyOf().also { origin = it }
        for (row in 0..2) for (col in 0..2) {
            var sum = 0f
            for (k in 0..2) sum += base[k * 3 + row] * matrix[k * 3 + col]
            relative[row * 3 + col] = sum
        }
        return relative
    }
    fun reset() { origin = null }
}
