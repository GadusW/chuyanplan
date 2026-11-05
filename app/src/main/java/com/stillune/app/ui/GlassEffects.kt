package com.stillune.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

private class GlassBackdrop(val layer: GraphicsLayer) { var origin = Offset.Zero }
private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Record only the environment, never controls or glass itself, so sampling cannot recurse. */
@Composable
internal fun GlassScene(artwork: android.graphics.Bitmap?, content: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { GlassBackdrop(layer) }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { backdrop.origin = it.positionInRoot() }) {
            MoonlitBackdrop(artwork, Modifier.matchParentSize().drawWithCache {
                onDrawWithContent { layer.record { this@onDrawWithContent.drawContent() }; drawLayer(layer) }
            })
            content()
        }
    }
}

/** A lens samples the real environment with a small displacement, beneath fine reflected light. */
internal fun Modifier.glassPanel(radius: Dp = 20.dp, strength: Float = 1f): Modifier =
    composed {
    val backdrop = LocalGlassBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
    .onGloballyPositioned { origin = it.positionInRoot() }
    .drawWithCache {
        val r = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
        val displacement = 2.dp.toPx()
        val body = Brush.linearGradient(listOf(
            Color.White.copy(alpha = .13f * strength), Mist.copy(alpha = .025f * strength),
            Color.White.copy(alpha = .07f * strength)), end = Offset(size.width, size.height))
        val rim = Brush.linearGradient(0f to Color.White.copy(alpha = .75f * strength),
            .3f to Color.White.copy(alpha = .07f), .62f to Color.White.copy(alpha = .015f),
            1f to Mist.copy(alpha = .55f * strength), end = Offset(size.width * .7f, size.height))
        onDrawBehind {
            backdrop?.let { scene ->
                val local = origin - scene.origin
                withTransform({
                    scale(1.055f, 1.055f, pivot = center)
                    translate(-local.x + displacement, -local.y - displacement)
                }) { drawLayer(scene.layer) }
            }
            drawRoundRect(body, cornerRadius = r)
            drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .09f), Color.Transparent),
                center = Offset(size.width * .15f, -size.height * .6f), radius = size.width * .7f), cornerRadius = r)
            drawRoundRect(rim, cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(.7.dp.toPx()))
            drawRoundRect(Brush.linearGradient(listOf(Color.Transparent, Mist.copy(alpha = .12f), Color.Transparent),
                start = Offset(0f, size.height * .7f), end = Offset(size.width, size.height)),
                topLeft = Offset(1.8.dp.toPx(), 1.8.dp.toPx()),
                size = androidx.compose.ui.geometry.Size((size.width - 3.6.dp.toPx()).coerceAtLeast(0f), (size.height - 3.6.dp.toPx()).coerceAtLeast(0f)),
                cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
        }
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
