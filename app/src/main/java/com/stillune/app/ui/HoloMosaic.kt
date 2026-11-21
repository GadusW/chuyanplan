package com.stillune.app.ui

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stillune.app.R
import com.stillune.app.immersion.MOSAIC_WAVE_LIMIT
import com.stillune.app.immersion.MosaicFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun HoloMosaicBackdrop(artwork: Bitmap?, viewport: IntSize, frame: () -> MosaicFrame, modifier: Modifier) {
    val context = LocalContext.current
    val resources = context.resources
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fallback by remember(context) {
        mutableStateOf(context.getSystemService(ActivityManager::class.java).deviceConfigurationInfo.reqGlEsVersion < 0x30000)
    }
    val prepared by produceState<Bitmap?>(null, artwork, resources) {
        value = withContext(Dispatchers.Default) {
            val source = artwork ?: defaultRippleArtwork(resources)
            val ratio = minOf(1f, 1024f / maxOf(source.width, source.height))
            val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
            Bitmap.createScaledBitmap(readable, (readable.width * ratio).roundToInt().coerceAtLeast(1),
                (readable.height * ratio).roundToInt().coerceAtLeast(1), true)
        }
    }
    if (fallback) {
        StaticMosaic(prepared, viewport, modifier)
        return
    }
    val view = remember(context) { MosaicSurfaceView(context) { fallback = true } }
    val currentFrame by rememberUpdatedState(frame)
    DisposableEffect(view, lifecycle) {
        view.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) view.setActive(true)
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) view.setActive(false)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.setActive(false)
        }
    }
    LaunchedEffect(view) {
        snapshotFlow { currentFrame() }.collect { view.submit(it) }
    }
    val cover = prepared
    LaunchedEffect(view, cover) { view.showArtwork(cover) }
    AndroidView(factory = { view }, modifier = modifier)
}

private class MosaicSurfaceView(context: Context, onFailure: () -> Unit) : GLSurfaceView(context) {
    private val mosaic = MosaicRenderer(context) { post { onFailure() } }
    private var active = true

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setEGLContextClientVersion(3)
        setEGLConfigChooser(false)
        setRenderer(mosaic)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        if (value) {
            onResume()
            requestRender()
        } else onPause()
    }

    fun submit(frame: MosaicFrame) {
        mosaic.frame = frame
        if (active) requestRender()
    }

    fun showArtwork(bitmap: Bitmap?) {
        if (bitmap === mosaic.artwork) return
        mosaic.artwork = bitmap
        if (active) requestRender()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return
        // Scale the surface as a unit to bound the cost of diffraction on high-density displays.
        val scale = minOf(1f, sqrt(720_000f / (width.toFloat() * height)))
        holder.setFixedSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
    }
}

private class MosaicRenderer(context: Context, private val reportFailure: () -> Unit) : GLSurfaceView.Renderer {
    private val resources = context.resources
    @Volatile var frame = MosaicFrame()
    @Volatile var artwork: Bitmap? = null
    private var uploaded: Bitmap? = null
    private var program = 0
    private var texture = 0
    private var uniforms = IntArray(0)
    private val packedWaves = FloatArray(MOSAIC_WAVE_LIMIT * 4)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        glClearColor(.025f, .033f, .064f, 1f)
        uploaded = null
        try {
            val fragment = resources.openRawResource(R.raw.holo_mosaic_fragment).bufferedReader().use { it.readText() }
            val vertexId = compile(GL_VERTEX_SHADER, VERTEX)
            val fragmentId = compile(GL_FRAGMENT_SHADER, fragment)
            program = glCreateProgram()
            glAttachShader(program, vertexId)
            glAttachShader(program, fragmentId)
            glLinkProgram(program)
            glDeleteShader(vertexId)
            glDeleteShader(fragmentId)
            val status = IntArray(1)
            glGetProgramiv(program, GL_LINK_STATUS, status, 0)
            check(status[0] == GL_TRUE) { glGetProgramInfoLog(program) }
            glUseProgram(program)
            val vertices = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
            val byteCount = vertices.size * Float.SIZE_BYTES
            val buffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder()).asFloatBuffer()
            buffer.put(vertices).position(0)
            glGenVertexArrays(1, status, 0)
            glBindVertexArray(status[0])
            glGenBuffers(1, status, 0)
            glBindBuffer(GL_ARRAY_BUFFER, status[0])
            glBufferData(GL_ARRAY_BUFFER, byteCount, buffer, GL_STATIC_DRAW)
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0)
            uniforms = arrayOf("size", "artworkRatio", "time", "entrance", "energy", "pointer", "waveCount", "waves[0]")
                .map { glGetUniformLocation(program, it) }.toIntArray()
            glGenTextures(1, status, 0)
            texture = status[0]
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, texture)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glUniform1i(glGetUniformLocation(program, "artwork"), 0)
        } catch (error: Exception) {
            fail(error)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        if (program != 0) glUniform2f(uniforms[0], width.toFloat(), height.toFloat())
    }

    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT)
        val cover = artwork ?: return
        if (program == 0) return
        val replacedArtwork = cover !== uploaded
        if (replacedArtwork) {
            try {
                glBindTexture(GL_TEXTURE_2D, texture)
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, cover, 0)
                glGenerateMipmap(GL_TEXTURE_2D)
                check(glGetError() == GL_NO_ERROR) { "Could not upload mosaic artwork" }
                glUniform1f(uniforms[1], cover.width.toFloat() / cover.height)
                uploaded = cover
            } catch (error: Exception) {
                fail(error)
                return
            }
        }
        val scene = frame
        glUniform1f(uniforms[2], scene.time)
        glUniform1f(uniforms[3], scene.entrance)
        glUniform1f(uniforms[4], scene.energy)
        glUniform2f(uniforms[5], scene.lightX, scene.lightY)
        glUniform1i(uniforms[6], scene.waves.size)
        scene.waves.forEachIndexed { index, wave ->
            val offset = index * 4
            packedWaves[offset] = wave.x
            packedWaves[offset + 1] = wave.y
            packedWaves[offset + 2] = wave.age
            packedWaves[offset + 3] = wave.strength
        }
        if (scene.waves.isNotEmpty()) glUniform4fv(uniforms[7], scene.waves.size, packedWaves, 0)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        if (replacedArtwork) {
            val result = glGetError()
            if (result != GL_NO_ERROR) fail(IllegalStateException("Mosaic draw failed: $result"))
        }
    }

    private fun compile(type: Int, source: String): Int {
        val id = glCreateShader(type)
        glShaderSource(id, source)
        glCompileShader(id)
        val status = IntArray(1)
        glGetShaderiv(id, GL_COMPILE_STATUS, status, 0)
        check(status[0] == GL_TRUE) { glGetShaderInfoLog(id) }
        return id
    }

    private fun fail(error: Exception) {
        Log.w("HoloMosaic", "Using the static mosaic renderer", error)
        if (program != 0) glDeleteProgram(program)
        program = 0
        reportFailure()
    }

    private companion object {
        const val VERTEX = """#version 300 es
            layout(location = 0) in vec2 position;
            void main() { gl_Position = vec4(position, 0, 1); }
        """
    }
}

@Composable
private fun StaticMosaic(artwork: Bitmap?, viewport: IntSize, modifier: Modifier) {
    val surface by produceState<CoverRefraction?>(null, artwork, viewport) {
        if (artwork != null && viewport.width > 0 && viewport.height > 0) {
            value = withContext(Dispatchers.Default) { CoverRefraction(artwork, viewport.width, viewport.height) }
        }
    }
    Box(modifier.drawWithCache {
        val tiles = Path()
        val pitch = (size.minDimension * .071f).coerceAtLeast(1f)
        val seam = pitch * .038f
        for (row in 0..(size.height / pitch).toInt()) {
            for (column in 0..(size.width / pitch).toInt()) {
                val x = column * pitch
                val y = row * pitch
                tiles.addRoundRect(RoundRect(Rect(x + seam, y + seam, x + pitch - seam, y + pitch - seam),
                    CornerRadius(pitch * .082f)))
            }
        }
        val rim = Brush.linearGradient(listOf(Color(0x5599ADC9), Color(0x106273A2), Color(0x771F293D)),
            Offset.Zero, Offset(size.width, size.height))
        onDrawBehind {
            drawRect(Color(0xFF0B1021))
            clipPath(tiles) {
                surface?.draw(drawContext.canvas.nativeCanvas, size.width, size.height, null, true)
            }
            drawPath(tiles, rim, style = Stroke((pitch * .028f).coerceAtLeast(1f)))
        }
    })
}
