package com.stillune.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/** A single material: translucent body, top-facing reflection and a fine refractive rim. */
internal fun Modifier.glassPanel(radius: Dp = 20.dp, strength: Float = 1f): Modifier =
    clip(androidx.compose.foundation.shape.RoundedCornerShape(radius)).drawWithCache {
        val r = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
        val body = Brush.linearGradient(listOf(
            Color.White.copy(alpha = .17f * strength), Mist.copy(alpha = .055f * strength),
            Color.White.copy(alpha = .09f * strength)), end = Offset(size.width, size.height))
        val rim = Brush.linearGradient(listOf(Color.White.copy(alpha = .48f * strength),
            Color.White.copy(alpha = .04f), Mist.copy(alpha = .22f * strength)),
            end = Offset(size.width * .6f, size.height))
        onDrawBehind {
            drawRoundRect(body, cornerRadius = r)
            drawRoundRect(rim, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
    }

internal class RevealCircle(private val center: Offset, private val progress: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val farX = maxOf(center.x, size.width - center.x)
        val farY = maxOf(center.y, size.height - center.y)
        val radius = hypot(farX, farY) * progress.coerceIn(0f, 1f)
        return Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
    }
}

/** Blurs actual scrolling pixels, never a painted gradient pretending to be blur. */
@Composable
internal fun Modifier.progressiveLibraryBlur(bottomInset: Dp): Modifier {
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < 31) return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val zone = with(density) { (bottomInset + 48.dp).toPx() }
        val mask = Brush.verticalGradient(listOf(Color.White, Color.Transparent), startY = size.height - zone, endY = size.height)
        onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val zone = with(density) { (bottomInset + 48.dp).toPx() }
    val radius = with(density) { 12.dp.toPx() }
    val effect = remember(size, zone, radius) {
        if (size.height == 0) null else maskedBlur(size.height.toFloat(), zone, radius)
    }
    return onSizeChanged { size = it }.graphicsLayer { renderEffect = effect }.drawWithCache {
        // Keep text passing behind the transparent controls from competing with their labels.
        val fade = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = .08f)),
            startY = this.size.height - zone, endY = this.size.height)
        onDrawWithContent { drawContent(); drawRect(fade, blendMode = BlendMode.DstIn) }
    }
}

@RequiresApi(31)
private fun maskedBlur(height: Float, zone: Float, radius: Float): RenderEffect {
    // Native Gaussian convolution avoids the multiple text contours of sparse shader sampling.
    fun mask(reverse: Boolean) = android.graphics.RenderEffect.createShaderEffect(android.graphics.LinearGradient(
        0f, height - zone, 0f, height - zone * .72f, if (reverse) intArrayOf(-1, 0x00FFFFFF) else intArrayOf(0x00FFFFFF, -1),
        null, android.graphics.Shader.TileMode.CLAMP))
    val sharp = android.graphics.RenderEffect.createBlendModeEffect(mask(true),
        android.graphics.RenderEffect.createOffsetEffect(0f, 0f), android.graphics.BlendMode.SRC_IN)
    val blurred = android.graphics.RenderEffect.createBlendModeEffect(mask(false),
        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP), android.graphics.BlendMode.SRC_IN)
    return android.graphics.RenderEffect.createBlendModeEffect(sharp, blurred, android.graphics.BlendMode.PLUS).asComposeRenderEffect()
}
