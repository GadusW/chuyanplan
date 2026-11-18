package com.stillune.app.ui

import android.animation.ValueAnimator
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
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
import kotlin.math.ceil
import kotlin.math.hypot

internal class GlassBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
        private set

    fun capture(modifier: Modifier): Modifier = modifier
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
}

@Composable
internal fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}
private val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Record only the environment, never controls or glass itself, so sampling cannot recurse. */
@Composable
internal fun GlassScene(artwork: android.graphics.Bitmap?, content: @Composable BoxScope.() -> Unit) {
    val backdrop = rememberGlassBackdrop()
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            MoonlitBackdrop(artwork, backdrop.capture(Modifier.matchParentSize()))
            content()
        }
    }
}

internal enum class GlassMaterial(
    val curve: Dp,
    val displacement: Float,
    val magnification: Float,
    val reflection: Float,
    val tint: Color,
    val fallback: Color,
    val shadowColor: Int,
) {
    Surface(14.dp, .41f, .025f, .70f, Color(0x22FFFFFF), Color(0xCEF5FAFE), 0x205A6980),
    Floating(20.dp, .76f, .055f, .81f, Color(0x57F6FBFF), Color(0xEDF5FAFE), 0x30526780),
    Accent(14.dp, .71f, .08f, .78f, Color(0xC8174971), Color(0xFF235477), 0x34335E82),
}

private val GlassPressEasing = CubicBezierEasing(.23f, 1f, .32f, 1f)

@Composable
internal fun animateGlassPressure(pressed: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (pressed && ValueAnimator.areAnimatorsEnabled()) 1f else 0f,
    animationSpec = tween(if (pressed) 100 else 150, easing = GlassPressEasing),
    label = "glass-pressure",
)

/** The lens transforms its backing layers. Labels, icons and interaction feedback stay in Compose. */
internal fun Modifier.glassPanel(
    radius: Dp = 20.dp,
    material: GlassMaterial = GlassMaterial.Surface,
    foregroundBackdrop: GlassBackdrop? = null,
    pressure: State<Float>? = null,
): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val lens = if (Build.VERSION.SDK_INT >= 33 && backdrop != null) rememberGraphicsLayer() else null
    var origin by remember { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInRoot() }.drawWithCache {
        if (size.isEmpty()) return@drawWithCache onDrawWithContent { drawContent() }
        val corner = (radius.toPx() * 1.35f).coerceAtMost(size.minDimension / 2f)
        val face = squirclePath(size, radius.toPx())
        val nativeOutline = face.asAndroidPath()
        val shadowOffset = 2.dp.toPx()
        val shadow = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
            setShadowLayer(7.dp.toPx(), 0f, shadowOffset, material.shadowColor)
        }
        val fallbackColor = material.fallback
        val fallbackFace = Brush.verticalGradient(
            0f to lerp(fallbackColor, Color.White.copy(alpha = fallbackColor.alpha), .12f),
            .48f to fallbackColor,
            1f to lerp(fallbackColor, InkBlue.copy(alpha = fallbackColor.alpha), .09f),
            endY = size.height,
        )
        val curve = material.curve.toPx().coerceAtMost(size.minDimension * .32f)
        val blur = if (foregroundBackdrop != null) 3.5.dp.toPx() else 0f
        val margin = ceil(curve * (material.displacement + .03f) + blur * 2f + 2f).toInt()
        val sampleSize = IntSize(ceil(size.width).toInt() + margin * 2, ceil(size.height).toInt() + margin * 2)
        val sampleBounds = Size(sampleSize.width.toFloat(), sampleSize.height.toFloat())
        val optics = if (Build.VERSION.SDK_INT >= 33 && lens != null) {
            GlassRefraction(size, corner, curve, margin.toFloat(), blur, material)
        } else null
        var lastPressure = Float.NaN
        onDrawWithContent {
            val compression = pressure?.value ?: 0f
            val scaleX = 1f - compression * .027f
            val scaleY = 1f - compression * .050f
            scale(scaleX, scaleY) {
                if (Build.VERSION.SDK_INT >= 28) {
                    drawIntoCanvas { it.nativeCanvas.drawPath(nativeOutline, shadow) }
                }
                clipPath(face) {
                    if (Build.VERSION.SDK_INT >= 33 && lens != null && optics != null &&
                        drawContext.canvas.nativeCanvas.isHardwareAccelerated
                    ) {
                        if (lastPressure != compression) {
                            lens.renderEffect = optics.effect(compression, scaleX, scaleY)
                            lastPressure = compression
                        }
                        lens.record(size = sampleSize) {
                            drawRect(GlassGround, size = sampleBounds)
                            translate(margin.toFloat(), margin.toFloat()) {
                                backdrop?.let { drawBackdrop(it, origin) }
                                foregroundBackdrop?.let { drawBackdrop(it, origin) }
                            }
                        }
                        translate(-margin.toFloat(), -margin.toFloat()) { drawLayer(lens) }
                    } else {
                        drawRect(fallbackFace)
                    }
                }
                this@onDrawWithContent.drawContent()
            }
        }
    }.clip(SquircleShape(radius))
}

private fun DrawScope.drawBackdrop(backdrop: GlassBackdrop, origin: Offset) {
    val offset = backdrop.origin - origin
    translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
}

@RequiresApi(33)
private class GlassRefraction(
    size: Size, corner: Float, curve: Float, margin: Float, blur: Float, material: GlassMaterial,
) {
    private val shader = RuntimeShader(GLASS_REFRACTION).apply {
        setFloatUniform("panel", size.width, size.height)
        setFloatUniform("corner", corner)
        setFloatUniform("curve", curve)
        setFloatUniform("margin", margin)
        setFloatUniform("displacement", material.displacement)
        setFloatUniform("magnification", material.magnification)
        setFloatUniform("reflection", material.reflection)
        setColorUniform("tint", material.tint.toArgb())
        setColorUniform("ground", GlassGround.toArgb())
    }
    private val soften = if (blur > 0f) {
        android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP)
    } else null

    fun effect(pressure: Float, scaleX: Float, scaleY: Float): RenderEffect {
        shader.setFloatUniform("pressure", pressure)
        shader.setFloatUniform("squash", scaleX, scaleY)
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "source")
        return (soften?.let { android.graphics.RenderEffect.createChainEffect(lens, it) } ?: lens).asComposeRenderEffect()
    }
}

private const val GLASS_REFRACTION = """
    uniform shader source;
    uniform float2 panel;
    uniform float corner;
    uniform float curve;
    uniform float margin;
    uniform float displacement;
    uniform float magnification;
    uniform float reflection;
    uniform float pressure;
    uniform float2 squash;
    layout(color) uniform half4 tint;
    layout(color) uniform half4 ground;

    half3 sceneAt(float2 point) {
        half4 pixel = source.eval(point);
        return pixel.rgb + (1.0 - pixel.a) * ground.rgb;
    }

    half4 main(float2 point) {
        float2 center = panel * 0.5;
        float2 local = point - margin - center;
        if (abs(local.x) > center.x + 1.0 || abs(local.y) > center.y + 1.0) {
            return half4(0.0);
        }
        float2 box = abs(local) - (center - corner);
        float2 arc = max(box, float2(0.0));
        // A superellipse closely follows the continuous Compose outline at the corners.
        float depth = corner - pow(dot(arc * arc, arc), 1.0 / 3.0)
            - min(max(box.x, box.y), 0.0);
        float bend = 1.0 - smoothstep(0.0, curve, depth);
        float2 direction = (arc.x > 0.0 || arc.y > 0.0)
            ? normalize(arc * arc)
            : (box.x > box.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
        direction *= sign(local);

        // Magnify the center slightly and draw surrounding pixels into the curved face.
        float slope = bend * 0.86 * (1.0 - pressure * 0.21);
        float2 transmission = local * (1.0 - magnification) + direction * (curve * displacement * slope);
        // Map samples back to screen space while the face and its controls contract.
        float2 samplePoint = margin + center + transmission * squash;
        half3 transmitted = sceneAt(samplePoint);
        if (bend > 0.12) {
            float dispersion = smoothstep(0.12, 0.85, bend);
            float2 split = direction * squash * (curve * 0.018 * dispersion * slope);
            transmitted.r = sceneAt(samplePoint + split).r;
            transmitted.b = sceneAt(samplePoint - split).b;
        }
        half3 glass = mix(transmitted, tint.rgb, tint.a);

        // Shade the entire continuous face with the same normal that bends the backdrop.
        float3 normal = normalize(float3(direction * slope / squash, sqrt(max(1.0 - slope * slope, 0.0))));
        float key = pow(max(dot(normal, float3(-0.34, -0.69, 0.64)), 0.0), 5.0);
        float bounce = pow(max(dot(normal, float3(0.45, 0.72, 0.53)), 0.0), 6.0);
        float facing = dot(normal.xy, float2(-0.45, -0.89));
        glass *= half(1.0 - max(-facing, 0.0) * 0.060);
        float light = reflection * (0.29 * key + 0.14 * bounce);
        return half4(mix(glass, half3(0.99, 0.995, 1.0), half(light)), 1.0);
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
