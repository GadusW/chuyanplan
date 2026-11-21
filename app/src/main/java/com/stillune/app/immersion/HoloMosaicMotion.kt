package com.stillune.app.immersion

internal const val MOSAIC_WAVE_LIMIT = 4

internal data class MosaicWave(val x: Float, val y: Float, val age: Float, val strength: Float)

internal data class MosaicFrame(
    val time: Float = 0f,
    val entrance: Float = 3f,
    val energy: Float = 0f,
    val lightX: Float = 0f,
    val lightY: Float = 0f,
    val waves: List<MosaicWave> = emptyList()
)

internal class HoloMosaicMotion {
    private data class Wave(val x: Float, val y: Float, val started: Float, val strength: Float)

    private val waves = ArrayList<Wave>(MOSAIC_WAVE_LIMIT)
    private var elapsed = 0f
    private var time = 0f
    private var previousMotion = 0f
    private var lastShake = -1f

    val active: Boolean get() = elapsed < 2.4f || waves.isNotEmpty()

    fun advance(dt: Float, tilt: Tilt, level: Float, onset: Float): MosaicFrame {
        elapsed += dt
        time += dt * (level * 1.19f + tilt.motion * .38f)
        waves.removeAll { elapsed - it.started >= if (it.strength < 0f) 2.9f else 1.65f }
        val lightX = (tilt.x * .83f).coerceIn(-.66f, .66f)
        val lightY = (tilt.y * .83f).coerceIn(-.66f, .66f)
        if (onset > 0f) emit(lightX * .31f, lightY * .31f - .04f,
            (.22f + onset * .55f).coerceAtMost(.77f))
        if (tilt.available && tilt.motion > .23f && tilt.motion - previousMotion > .027f && elapsed - lastShake > .38f) {
            emit((tilt.shakeX * .32f).coerceIn(-.32f, .32f),
                (tilt.shakeY * .36f).coerceIn(-.36f, .36f),
                -(tilt.motion * 1.32f).coerceIn(.34f, .78f))
            lastShake = elapsed
        }
        previousMotion = tilt.motion
        return MosaicFrame(time, elapsed.coerceAtMost(3f), level, lightX, lightY,
            waves.map { MosaicWave(it.x, it.y, elapsed - it.started, it.strength) })
    }

    private fun emit(x: Float, y: Float, strength: Float) {
        if (waves.size == MOSAIC_WAVE_LIMIT) {
            val firstAudio = waves.indexOfFirst { it.strength > 0f }
            if (firstAudio < 0 && strength > 0f) return
            waves.removeAt(firstAudio.coerceAtLeast(0))
        }
        waves.add(Wave(x, y, elapsed, strength))
    }
}
