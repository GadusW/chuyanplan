package com.stillune.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class ArtworkColors(val primary: Color, val secondary: Color, val ground: Color)

/** Bounded 32x32 histogram; ignores transparent, near-black and near-white pixels. */
internal fun artworkColors(bitmap: Bitmap?): ArtworkColors {
    val fallback = ArtworkColors(Color(0xFF78C8FF), Color(0xFFC0A8F5), Night)
    if (bitmap == null || bitmap.isRecycled) return fallback
    return runCatching {
        val bins = IntArray(4096)
        val hsv = FloatArray(3)
        for (y in 0 until 32) for (x in 0 until 32) {
            val pixel = bitmap.getPixel(x * bitmap.width / 32, y * bitmap.height / 32)
            android.graphics.Color.colorToHSV(pixel, hsv)
            if (android.graphics.Color.alpha(pixel) < 128 || hsv[1] < .16f || hsv[2] !in .12f.. .96f) continue
            val bin = ((pixel shr 20 and 15) shl 8) or ((pixel shr 12 and 15) shl 4) or (pixel shr 4 and 15)
            bins[bin] += 1 + (hsv[1] * 3).toInt()
        }
        val sorted = bins.indices.filter { bins[it] > 0 }.sortedByDescending { bins[it] }
        if (sorted.isEmpty()) return@runCatching fallback
        fun color(bin: Int): Int = android.graphics.Color.rgb((bin shr 8 and 15) * 17, (bin shr 4 and 15) * 17, (bin and 15) * 17)
        android.graphics.Color.colorToHSV(color(sorted.first()), hsv)
        val hue = hsv[0]
        val primary = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, hsv[1].coerceIn(.48f, .85f), .98f)))
        val other = sorted.firstOrNull {
            android.graphics.Color.colorToHSV(color(it), hsv)
            val difference = kotlin.math.abs(hsv[0] - hue)
            minOf(difference, 360f-difference) > 28f
        }
        val secondary = if (other != null) {
            android.graphics.Color.colorToHSV(color(other), hsv)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], hsv[1].coerceIn(.4f,.8f), .98f)))
        } else lerp(primary, Moon, .55f)
        val ground = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, .6f, .13f)))
        ArtworkColors(primary, secondary, ground)
    }.getOrDefault(fallback)
}
