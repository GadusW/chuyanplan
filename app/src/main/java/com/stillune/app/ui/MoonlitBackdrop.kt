package com.stillune.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun MoonlitBackdrop(artwork: Bitmap?, modifier: Modifier = Modifier) {
    val blurred by produceState<Bitmap?>(null, artwork) {
        value = artwork?.let { withContext(Dispatchers.Default) { blurArtwork(it) } }
    }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFFDFBFA), GlassGround, Color(0xFFDCEAF5))))) {
        blurred?.let {
            Image(it.asImageBitmap(), null, Modifier.matchParentSize().alpha(.30f), contentScale = ContentScale.Crop)
        }
        Canvas(Modifier.matchParentSize()) {
            val highlight = Offset(size.width * .12f, size.height * .13f)
            val coolEdge = Offset(size.width, size.height * .68f)
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .67f), Color.Transparent),
                center = highlight, radius = size.height * .58f))
            drawRect(Brush.radialGradient(listOf(Color(0xFFD8EFFC).copy(alpha = .50f), Color.Transparent),
                center = coolEdge, radius = size.height * .41f))
        }
    }
}

/** Blur a bounded image off the UI thread, including on Android versions without RenderEffect. */
private fun blurArtwork(source: Bitmap): Bitmap {
    val side = 24
    val sample = Bitmap.createScaledBitmap(source, side, side, true)
    val pixels = IntArray(side * side)
    sample.getPixels(pixels, 0, side, 0, 0, side, side)
    val output = IntArray(pixels.size)
    for (y in 0 until side) for (x in 0 until side) {
        var red = 0
        var green = 0
        var blue = 0
        var count = 0
        for (row in maxOf(0, y - 4)..minOf(side - 1, y + 4)) {
            for (col in maxOf(0, x - 4)..minOf(side - 1, x + 4)) {
                val color = pixels[row * side + col]
                val alpha = color ushr 24
                red += 255 - (255 - (color shr 16 and 255)) * alpha / 255
                green += 255 - (255 - (color shr 8 and 255)) * alpha / 255
                blue += 255 - (255 - (color and 255)) * alpha / 255
                count++
            }
        }
        output[y * side + x] = android.graphics.Color.rgb(red / count, green / count, blue / count)
    }
    return Bitmap.createBitmap(output, side, side, Bitmap.Config.ARGB_8888)
}
