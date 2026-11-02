package com.stillune.app.immersion

import android.media.audiofx.Visualizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.pow
import java.util.concurrent.atomic.AtomicBoolean

/** Only attaches to Stillune's generated session. Never falls back to global session 0. */
fun liveAudioEnergy(sessionId: Int) = callbackFlow {
    if (sessionId <= 0) {
        trySend(AudioEnergy(message = "等待播放会话"))
        close()
        return@callbackFlow
    }
    var effect: Visualizer? = null
    val closed = AtomicBoolean(false)
    val lock = Any()
    fun dispose() = synchronized(lock) {
        if (closed.compareAndSet(false, true)) {
            effect?.let { target ->
                runCatching { target.setDataCaptureListener(null, 0, false, false) }
                runCatching { target.enabled = false }
                runCatching { target.release() }
            }
            effect = null
        }
    }
    try {
        val created = Visualizer(sessionId)
        effect = created
        val rhythm = RhythmEnvelope()
        var lastCapture = 0L
        created.apply {
            captureSize = 512.coerceIn(Visualizer.getCaptureSizeRange()[0], Visualizer.getCaptureSizeRange()[1])
            scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            val measured = Visualizer.MeasurementPeakRms()
            val measurementEnabled = setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS) == Visualizer.SUCCESS
            val status = setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    synchronized(lock) {
                    if (closed.get() || waveform == null) return
                    try {
                    val waveformRms = WaveformEnergy.rms(waveform)
                    // The 8-bit visualization waveform rounds very quiet audio to silence.
                    // Prefer the effect's PCM-domain RMS when supported; this is a real
                    // measurement in millibels, converted back to a linear amplitude.
                    val measuredRms = if (measurementEnabled && visualizer?.getMeasurementPeakRms(measured) == Visualizer.SUCCESS && measured.mRms > -9600)
                        10.0.pow(measured.mRms / 2000.0).toFloat().coerceIn(0f, 1f) else 0f
                    // Peak/RMS measurement can average over a long OEM window, obscuring
                    // drum attacks. Prefer each fresh waveform block, retain quiet fallback.
                    val rms = if (waveformRms > .0003f) waveformRms else measuredRms
                    val now = System.nanoTime()
                    val dt = if (lastCapture == 0L) .025f else (now - lastCapture) / 1e9f
                    lastCapture = now
                    trySend(rhythm.update(rms, dt, now))
                    } catch (_: RuntimeException) {
                        // OEM callbacks may already be queued when an effect is disabled.
                        // A released native handle must never escape onto the UI looper.
                        if (!closed.get()) trySend(AudioEnergy(message = "播放波形暂不可用，运动效果仍可使用"))
                    }
                    }
                }
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) = Unit
            }, minOf(50_000, Visualizer.getMaxCaptureRate()), true, false)
            check(status == Visualizer.SUCCESS)
            check(setEnabled(true) == Visualizer.SUCCESS)
        }
    } catch (_: Exception) {
        dispose()
        trySend(AudioEnergy(message = "此设备暂不支持播放波形，倾斜效果仍可使用"))
    }
    awaitClose { dispose() }
}.conflate()
