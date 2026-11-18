package com.stillune.app.ui

import android.os.Build
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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

/** Refract the recorded environment at the bevel, then draw the controls over clear glass. */
internal fun Modifier.glassPanel(radius: Dp = 20.dp, strength: Float = 1f): Modifier =
    composed {
        val backdrop = LocalGlassBackdrop.current
        val lens = if (Build.VERSION.SDK_INT >= 33 && backdrop != null) rememberGraphicsLayer() else null
        var origin by remember { mutableStateOf(Offset.Zero) }
        clip(SquircleShape(radius))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithCache {
                if (size.isEmpty()) return@drawWithCache onDrawBehind { }
                val corner = (radius.toPx() * 1.35f).coerceAtMost(size.minDimension / 2f)
                val bevelWidth = (6.dp.toPx() * strength).coerceIn(3.dp.toPx(), 7.dp.toPx())
                    .coerceAtMost(size.minDimension / 5f)
                val refraction = bevelWidth * .62f
                val outline = squirclePath(size, radius.toPx())
                val innerEdge = insetGlassOutline(size, corner, bevelWidth)
                val bevel = Path.combine(PathOperation.Difference, outline, innerEdge)
                val white = Color.White
                val body = Brush.verticalGradient(
                    0f to white.copy(alpha = (.39f * strength).coerceAtMost(.57f)),
                    .50f to white.copy(alpha = .25f * strength),
                    1f to white.copy(alpha = .39f * strength),
                    endY = size.height,
                )
                val bevelFace = Brush.verticalGradient(
                    0f to InkBlue.copy(alpha = .22f),
                    .15f to white.copy(alpha = .38f),
                    .58f to InkBlue.copy(alpha = .06f),
                    .90f to InkBlue.copy(alpha = .21f),
                    1f to white.copy(alpha = .48f),
                    endY = size.height,
                )
                val edgeColors = Brush.linearGradient(
                    0f to white.copy(alpha = .10f),
                    .28f to Color(0xFF8BAAEA).copy(alpha = .18f),
                    .55f to Color(0xFFA89BD9).copy(alpha = .16f),
                    .77f to white.copy(alpha = .06f),
                    1f to Color(0xFFF1C88F).copy(alpha = .32f),
                    end = Offset(size.width, size.height * 1.35f),
                )
                val specular = Brush.linearGradient(
                    0f to white.copy(alpha = .98f),
                    .25f to white.copy(alpha = .73f),
                    .57f to white.copy(alpha = .17f),
                    1f to white.copy(alpha = .92f),
                    end = Offset(size.width * .45f, size.height),
                )
                val rimStroke = Stroke(1.dp.toPx())
                if (Build.VERSION.SDK_INT >= 33 && lens != null) {
                    lens.renderEffect = glassRefraction(size, corner, bevelWidth, refraction)
                }
                onDrawBehind {
                    backdrop?.let { scene ->
                        val source = scene.origin - origin
                        if (lens != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                            lens.record {
                                drawRect(GlassGround)
                                translate(source.x, source.y) { drawLayer(scene.layer) }
                            }
                            drawLayer(lens)
                        } else {
                            translate(source.x, source.y) { drawLayer(scene.layer) }
                            clipPath(bevel) {
                                scale(1f + 2f * refraction / size.width, 1f + 2f * refraction / size.height) {
                                    translate(source.x, source.y) { drawLayer(scene.layer) }
                                }
                            }
                        }
                    }
                    drawPath(outline, body)
                    drawPath(bevel, bevelFace)
                    drawPath(bevel, edgeColors)
                    drawPath(innerEdge, specular, style = rimStroke)
                }
            }
    }

private fun insetGlassOutline(size: Size, corner: Float, inset: Float): Path {
    val innerSize = Size(size.width - 2f * inset, size.height - 2f * inset)
    return squirclePath(innerSize, (corner - inset).coerceAtLeast(0f) / 1.35f).apply {
        translate(Offset(inset, inset))
    }
}

@RequiresApi(33)
private fun glassRefraction(size: Size, corner: Float, bevel: Float, displacement: Float): RenderEffect {
    val shader = RuntimeShader(GLASS_REFRACTION).apply {
        setFloatUniform("bounds", size.width, size.height)
        setFloatUniform("corner", corner)
        setFloatUniform("bevel", bevel)
        setFloatUniform("displacement", displacement)
    }
    return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "backdrop").asComposeRenderEffect()
}

private const val GLASS_REFRACTION = """
    uniform shader backdrop;
    uniform float2 bounds;
    uniform float corner;
    uniform float bevel;
    uniform float displacement;

    half4 main(float2 point) {
        float2 centered = point - bounds * 0.5;
        float2 nearCorner = abs(centered) - (bounds * 0.5 - corner);
        float2 curved = max(nearCorner, float2(0.0));
        // A cubic superellipse closely follows the panel's continuous corner.
        float curvedDistance = pow(dot(curved * curved, curved), 1.0 / 3.0);
        float depth = corner - curvedDistance - min(max(nearCorner.x, nearCorner.y), 0.0);
        if (depth >= bevel) return backdrop.eval(point);

        float2 normal = curved * curved;
        if (min(nearCorner.x, nearCorner.y) <= 0.0) {
            normal = nearCorner.x > nearCorner.y ? float2(1.0, 0.0) : float2(0.0, 1.0);
        }
        normal = normalize(normal) * sign(centered);
        float bend = 1.0 - smoothstep(0.0, bevel, depth);
        float2 samplePoint = point - normal * displacement * bend;
        return backdrop.eval(clamp(samplePoint, float2(0.5), bounds - float2(0.5)));
    }
"""

/** A destination needs opaque pixels inside the reveal, including between controls. */
@Composable
internal fun Modifier.pageReveal(origin: Offset?, pageOrigin: () -> Offset, progress: () -> Float): Modifier {
    val backdrop = LocalGlassBackdrop.current
    val aperture = remember { Path() }
    return drawWithContent {
        val fraction = progress()
        if (origin == null || fraction >= 1f) {
            drawContent()
        } else if (fraction > 0f) {
            val topLeft = pageOrigin()
            val center = origin - topLeft
            val radius = hypot(maxOf(center.x, size.width - center.x), maxOf(center.y, size.height - center.y)) * fraction
            aperture.rewind()
            aperture.addOval(Rect(center, radius))
            clipPath(aperture) {
                drawRect(GlassGround)
                backdrop?.let { scene ->
                    val offset = scene.origin - topLeft
                    translate(offset.x, offset.y) { drawLayer(scene.layer) }
                }
                this@drawWithContent.drawContent()
            }
        }
    }
}

/** Blurs actual scrolling pixels, never a painted gradient pretending to be blur. */
@Composable
internal fun Modifier.progressiveLibraryBlur(bottomInset: Dp): Modifier {
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < 31) return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val zone = with(density) { bottomInset.toPx().coerceAtLeast(1f) }
        val mask = Brush.verticalGradient(listOf(Color.White, Color.Transparent), startY = size.height - zone, endY = size.height)
        onDrawWithContent { drawContent(); drawRect(mask, blendMode = BlendMode.DstIn) }
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val zone = with(density) { bottomInset.toPx().coerceAtLeast(1f) }
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
        0f, height - zone, 0f, height - zone * .46f, if (reverse) intArrayOf(-1, 0x00FFFFFF) else intArrayOf(0x00FFFFFF, -1),
        null, android.graphics.Shader.TileMode.CLAMP))
    val sharp = android.graphics.RenderEffect.createBlendModeEffect(mask(true),
        android.graphics.RenderEffect.createOffsetEffect(0f, 0f), android.graphics.BlendMode.SRC_IN)
    val blurred = android.graphics.RenderEffect.createBlendModeEffect(mask(false),
        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP), android.graphics.BlendMode.SRC_IN)
    return android.graphics.RenderEffect.createBlendModeEffect(sharp, blurred, android.graphics.BlendMode.PLUS).asComposeRenderEffect()
}
