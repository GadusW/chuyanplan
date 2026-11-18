package com.stillune.app.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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

private val IndigoGlass = Color(0xFF183CA3)
private val SkyReflection = Color(0xFF7CEAFF)
private val PearlReflection = Color(0xFFEFF8FF)

/** Draw a curved, transmissive glass face behind ordinary Compose controls. */
internal fun Modifier.glassPanel(radius: Dp = 20.dp, tinted: Boolean = false): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val lens = if (Build.VERSION.SDK_INT >= 33 && backdrop != null) rememberGraphicsLayer() else null
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInRoot() }.drawWithCache {
        if (size.isEmpty()) return@drawWithCache onDrawBehind { }
        val corner = (radius.toPx() * 1.35f).coerceAtMost(size.minDimension / 2f)
        val face = squirclePath(size, radius.toPx())
        val edge = .7.dp.toPx().coerceAtMost(size.minDimension / 4f)
        val innerFace = squirclePath(Size(size.width - edge * 2f, size.height - edge * 2f),
            (radius.toPx() - edge).coerceAtLeast(0f)).apply { translate(Offset(edge, edge)) }
        val faceColor = if (tinted) Brush.verticalGradient(
            0f to Color(0xFF142970).copy(alpha = .93f),
            .53f to IndigoGlass.copy(alpha = .90f),
            1f to Color(0xFF04AAD7).copy(alpha = .80f),
            endY = size.height,
        ) else Brush.linearGradient(
            0f to Color(0xFFEBEEFF).copy(alpha = .38f),
            .42f to Color.White.copy(alpha = .12f),
            .76f to PearlReflection.copy(alpha = .33f),
            1f to Color(0xFFFFEEE3).copy(alpha = .39f),
            end = Offset(size.width * .72f, size.height),
        )
        val innerLight = Brush.verticalGradient(
            0f to Color(0xFF364876).copy(alpha = if (tinted) .48f else .13f),
            .20f to Color.White.copy(alpha = if (tinted) .07f else .22f),
            .58f to Color.Transparent,
            .87f to Color.Transparent,
            1f to (if (tinted) SkyReflection else PearlReflection).copy(alpha = .62f),
            endY = size.height,
        )
        val specularRim = Brush.linearGradient(
            0f to Color.White,
            .24f to Color.White.copy(alpha = .64f),
            .48f to Color(0xFF7A83A7).copy(alpha = if (tinted) .60f else .32f),
            .74f to Color.White.copy(alpha = .13f),
            1f to Color.White.copy(alpha = .91f),
            end = Offset(size.width * .76f, size.height),
        )
        val shineX = minOf(6.dp.toPx(), size.width * .12f)
        val shineY = 3.dp.toPx()
        val shineHeight = minOf((if (tinted) 10.dp else 12.dp).toPx(), size.height * .24f)
        val shineWidth = size.width * if (tinted) .72f else .63f
        val gloss = Path().apply {
            moveTo(shineX, shineY + shineHeight)
            cubicTo(shineX, shineY + shineHeight * .57f, shineX + shineHeight * .8f, shineY,
                shineX + shineHeight * 2f, shineY)
            cubicTo(shineX + shineWidth * .40f, shineY, shineX + shineWidth * .85f, shineY,
                shineX + shineWidth, shineY + shineHeight * .24f)
            cubicTo(shineX + shineWidth * .88f, shineY + shineHeight * .62f,
                shineX + shineWidth * .49f, shineY + shineHeight * .54f,
                shineX + shineWidth * .24f, shineY + shineHeight * .58f)
            cubicTo(shineX + shineWidth * .10f, shineY + shineHeight * .60f,
                shineX + shineHeight * .58f, shineY + shineHeight * 1.14f,
                shineX, shineY + shineHeight)
            close()
        }
        val glossWhite = Brush.linearGradient(
            0f to Color.White.copy(alpha = .98f),
            .24f to Color.White.copy(alpha = .95f),
            .58f to Color.White.copy(alpha = .51f),
            1f to Color.Transparent,
            start = Offset(shineX, shineY), end = Offset(shineX + shineWidth, shineY + shineHeight * 1.8f),
        )
        val broadReflection = Brush.radialGradient(
            listOf(Color.White.copy(alpha = if (tinted) .29f else .46f), Color.Transparent),
            center = Offset(shineX + shineWidth * .18f, -shineHeight * 1.9f),
            radius = shineHeight * 6.2f,
        )
        val shadow = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer((if (tinted) 9.dp else 11.dp).toPx(), 0f, 3.dp.toPx(),
                if (tinted) 0x504360BC else 0x4A576785)
        }
        val nativeOutline = face.asAndroidPath()
        val bevelWidth = minOf(13.dp.toPx(), size.minDimension * .26f)
        if (Build.VERSION.SDK_INT >= 33 && lens != null) {
            lens.renderEffect = glassRefraction(size, corner, bevelWidth)
        }
        onDrawBehind {
            if (Build.VERSION.SDK_INT >= 28) {
                drawIntoCanvas { it.nativeCanvas.drawPath(nativeOutline, shadow) }
            } else {
                translate(top = 2.dp.toPx()) { drawPath(face, InkBlue.copy(alpha = .11f)) }
            }
            clipPath(face) {
                backdrop?.let {
                    val offset = it.origin - origin
                    if (lens != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                        lens.record {
                            drawRect(GlassGround)
                            translate(offset.x, offset.y) { drawLayer(it.layer) }
                        }
                        drawLayer(lens)
                    } else {
                        scale(1.09f, 1.09f) {
                            translate(offset.x, offset.y) { drawLayer(it.layer) }
                        }
                    }
                }
                drawRect(faceColor, size = size)
                drawRect(innerLight, size = size)
                drawRect(broadReflection, size = size)
                drawPath(face, Color(0xFF3D507C).copy(alpha = if (tinted) .72f else .38f), style = Stroke(1.4.dp.toPx()))
                drawPath(innerFace, specularRim, style = Stroke(1.dp.toPx()))
                drawPath(gloss, glossWhite)
            }
        }
    }.clip(SquircleShape(radius))
}

@RequiresApi(33)
private fun glassRefraction(size: Size, corner: Float, bevelWidth: Float): RenderEffect {
    val shader = RuntimeShader(GLASS_REFRACTION).apply {
        setFloatUniform("panel", size.width, size.height)
        setFloatUniform("corner", corner)
        setFloatUniform("bevelWidth", bevelWidth)
    }
    return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "source").asComposeRenderEffect()
}

private const val GLASS_REFRACTION = """
    uniform shader source;
    uniform float2 panel;
    uniform float corner;
    uniform float bevelWidth;

    half4 main(float2 point) {
        float2 mid = panel * 0.5;
        float2 local = point - mid;
        float2 cornerOffset = abs(local) - (mid - corner);
        float2 curve = max(cornerOffset, float2(0.0));
        float depth = corner - pow(dot(curve * curve, curve), 1.0 / 3.0)
            - min(max(cornerOffset.x, cornerOffset.y), 0.0);
        float bevel = 1.0 - smoothstep(0.0, bevelWidth, depth);
        float2 normal = min(cornerOffset.x, cornerOffset.y) > 0.0
            ? normalize(curve * curve)
            : (cornerOffset.x > cornerOffset.y ? float2(1, 0) : float2(0, 1));
        normal *= sign(local);
        float2 samplePoint = mid + local * 0.915 - normal * bevelWidth * 0.64 * bevel;
        float2 split = normal * bevel * bevelWidth * 0.075;
        half4 central = source.eval(samplePoint);
        half4 refracted = half4(source.eval(samplePoint + split).r, central.g, source.eval(samplePoint - split).b, central.a);
        float glint = smoothstep(0.51, 0.98, point.y / panel.y) * bevel * 0.24;
        return mix(refracted, half4(0.74, 0.87, 0.99, 1.0), half(glint));
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
