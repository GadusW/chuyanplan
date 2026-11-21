package com.stillune.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.stillune.app.immersion.CurlWake

@RequiresApi(33)
internal class CurlRefraction {
    private val shader = RuntimeShader(FOLD_SHADER)
    private var texture: Bitmap? = null
    private var rasterRevision = Long.MIN_VALUE

    fun effect(wake: CurlWake, revision: Long, width: Float, height: Float, cover: RectF): RenderEffect {
        val existing = texture
        val bitmap = if (existing?.width == wake.width && existing.height == wake.height) existing else {
            Bitmap.createBitmap(wake.width, wake.height, Bitmap.Config.ARGB_8888).also {
                texture = it
                rasterRevision = Long.MIN_VALUE
            }
        }
        if (revision != rasterRevision) {
            bitmap.setPixels(wake.rasterize(), 0, wake.width, 0, 0, wake.width, wake.height)
            val field = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
            }
            shader.setInputBuffer("displacement", field)
            rasterRevision = revision
        }
        shader.setFloatUniform("viewport", width, height)
        shader.setFloatUniform("fieldSize", wake.width.toFloat(), wake.height.toFloat())
        shader.setFloatUniform("cover", cover.left * width, cover.top * height, cover.right * width, cover.bottom * height)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "scene").asComposeRenderEffect()
    }

    private companion object {
        // A scalar field rotates the sampling direction as its intensity increases.
        const val FOLD_SHADER = """
            uniform shader scene;
            uniform shader displacement;
            uniform float2 viewport;
            uniform float2 fieldSize;
            uniform float4 cover;

            half4 main(float2 pixel) {
                float2 field = float2(displacement.eval(pixel * fieldSize / viewport).rg);
                if (field.x < 0.002) return scene.eval(pixel);

                float shortSide = min(viewport.x, viewport.y);
                float2 toEdge = min(pixel, viewport - pixel);
                float screenWeight = smoothstep(0.0, shortSide * 0.095, min(toEdge.x, toEdge.y));
                float2 inside = min(pixel - cover.xy, cover.zw - pixel);
                float coverShort = min(cover.z - cover.x, cover.w - cover.y);
                float inCover = smoothstep(0.0, max(1.0, coverShort * 0.10), min(inside.x, inside.y));
                float accent = smoothstep(0.10, 0.70, field.y);
                float coverWeight = 1.0 - inCover * mix(0.66, 0.12, accent);

                float angle = field.x * 6.28;
                float2 direction = float2(cos(angle), sin(angle));
                float strength = mix(0.157, 0.225, accent) * field.x * shortSide;
                float2 samplePoint = pixel + direction * strength * coverWeight * screenWeight;
                return scene.eval(clamp(samplePoint, float2(0.5), viewport - 0.5));
            }
        """
    }
}
