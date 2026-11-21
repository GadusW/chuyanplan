package com.stillune.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.test.filters.SdkSuppress
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.data.Track
import com.stillune.app.immersion.*
import com.stillune.app.playback.PlaybackState
import com.stillune.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class ImmersionUiTest {
    @get:Rule val compose = createComposeRule()

    @SdkSuppress(minSdkVersion = 33)
    @Test fun curlShaderResamplesTheCoverAndClearsWhenTheWakeExpires() {
        val wake = CurlWake().apply { resize(360, 720) }
        val curl = CurlRefraction()
        val cover = RectF(.08f, .25f, .92f, .75f)
        var revision by mutableLongStateOf(0L)
        compose.setContent {
            ComposeCanvas(Modifier.fillMaxSize().graphicsLayer {
                val current = revision
                renderEffect = if (wake.active) curl.effect(wake, current, size.width, size.height, cover) else null
            }) {
                val cell = size.width / 16f
                for (x in 0..15) drawRect(
                    color = if (x % 2 == 0) ComposeColor.Blue else ComposeColor.White,
                    topLeft = Offset(x * cell, 0f), size = Size(cell, size.height)
                )
            }
        }
        val root = compose.onRoot()
        val calm = root.captureToImage().toPixelMap()
        compose.runOnIdle { wake.advance(.016f, Tilt(), 1f); revision++ }
        val curled = root.captureToImage().toPixelMap()
        var changed = 0
        for (y in calm.height * 2 / 5 until calm.height * 3 / 5 step 6) {
            for (x in calm.width / 4 until calm.width * 3 / 4 step 6) {
                if (kotlin.math.abs(calm[x, y].red - curled[x, y].red) > .4f) changed++
            }
        }
        assertTrue("The GPU pass must fold texture inside the cover: $changed changed samples", changed > 20)
        compose.runOnIdle {
            repeat(29) { wake.advance(.016f, Tilt(), 0f) }
            revision++
        }
        val fading = root.captureToImage().toPixelMap()
        assertTrue((calm.width / 4 until calm.width * 3 / 4 step 7).any { x ->
            fading[x, calm.height / 2] != curled[x, calm.height / 2]
        })
        compose.runOnIdle {
            repeat(47) { wake.advance(.016f, Tilt(), 0f) }
            revision++
        }
        val settled = root.captureToImage().toPixelMap()
        for (x in calm.width / 4 until calm.width * 3 / 4 step 11) {
            assertEquals(calm[x, calm.height / 2], settled[x, settled.height / 2])
        }
    }
    @Test fun realAttackEventChangesTheRenderedRippleField() {
        compose.mainClock.autoAdvance = false
        var audio by mutableStateOf(AudioEnergy(available = true))
        val artwork = stripedArtwork()
        compose.setContent { StilluneTheme {
            ImmersiveScene(PlaybackState(playing = true), artwork, ImmersionStyle.RIPPLES, Tilt(), audio,
                audioAllowed = true, onStyle = {}, onToggle = {}, onPrevious = {},
                onNext = {}, onSeek = {}, onExit = {})
        } }
        compose.mainClock.advanceTimeBy(100)
        compose.waitUntil(timeoutMillis = 5_000) {
            val preview = compose.onRoot().captureToImage().toPixelMap()
            val center = preview[preview.width / 2, (preview.height * .46f).toInt()]
            center.red > .94f || center.blue > .94f
        }
        val quiet = compose.onRoot().captureToImage().toPixelMap()
        compose.runOnIdle { audio = AudioEnergy(.5f, true, level = 1f, onset = 1f, beatId = 1L) }
        compose.mainClock.advanceTimeBy(350)
        val attack = compose.onRoot().captureToImage().toPixelMap()
        var changes = 0
        for (y in 0 until quiet.height step 12) for (x in 0 until quiet.width step 12) {
            val a = quiet[x, y]; val b = attack[x, y]
            if (kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue) > .08f) changes++
        }
        assertTrue("Ripples must visibly react to the audio attack", changes > 20)
    }

    @Test fun mosaicGpuRespondsToMusicAndSilentHandMotionAndRespectsReducedMotion() {
        compose.mainClock.autoAdvance = false
        var root: View? = null
        var audio by mutableStateOf(AudioEnergy(available = true))
        var tilt by mutableStateOf(Tilt(available = true))
        var playing by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        val artwork = stripedArtwork(markEdges = true)
        compose.setContent { StilluneTheme {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            ImmersiveScene(PlaybackState(playing = playing), artwork, ImmersionStyle.HOLO_MOSAIC,
                tilt, audio, reducedMotion = reduced, audioAllowed = true,
                onStyle = {}, onToggle = {}, onPrevious = {}, onNext = {}, onSeek = {}, onExit = {})
        } }
        compose.mainClock.advanceTimeBy(2400)
        val surface = compose.runOnIdle { checkNotNull(findSurface(root)) }
        fun capture(): Bitmap? {
            val width = surface.width.coerceAtLeast(1)
            val height = surface.height.coerceAtLeast(1)
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            var result = PixelCopy.ERROR_UNKNOWN
            try {
                PixelCopy.request(surface, image, { result = it; latch.countDown() }, Handler(Looper.getMainLooper()))
            } catch (_: IllegalArgumentException) { return null }
            return if (latch.await(2, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) image else null
        }
        fun differences(a: Bitmap, b: Bitmap): Int {
            var changed = 0
            for (y in 0 until a.height step 15) for (x in 0 until a.width step 15) {
                val p = a.getPixel(x, y)
                val q = b.getPixel(x, y)
                if (kotlin.math.abs(Color.red(p) - Color.red(q)) + kotlin.math.abs(Color.blue(p) - Color.blue(q)) > 38) changed++
            }
            return changed
        }
        var reference: Bitmap? = null
        compose.waitUntil(8_000) {
            // Artwork is prepared off-thread and AndroidView needs a frame to receive it.
            compose.mainClock.advanceTimeByFrame()
            capture()?.let { image ->
                val hasCover = (image.height * 2 / 5 until image.height / 2 step 13).any { y ->
                    (image.width / 4 until image.width * 3 / 4 step 7).any { x ->
                        val pixel = image.getPixel(x, y)
                        maxOf(Color.red(pixel), Color.blue(pixel)) - Color.green(pixel) > 85
                    }
                }
                if (hasCover) {
                    reference = image
                }
            }
            reference != null
        }
        val quiet = checkNotNull(reference)
        compose.runOnIdle { audio = AudioEnergy(.5f, true, level = 1f, onset = 1f, beatId = 1L) }
        compose.mainClock.advanceTimeBy(340)
        compose.waitUntil(8_000) { capture()?.let { differences(quiet, it) > 35 } == true }
        compose.runOnIdle { audio = AudioEnergy(); playing = false; reduced = true }
        compose.mainClock.advanceTimeBy(160)
        compose.waitUntil(8_000) { capture()?.let { differences(quiet, it) < 5 } == true }
        compose.runOnIdle { reduced = false }
        compose.mainClock.advanceTimeBy(2400)
        compose.runOnIdle { tilt = Tilt(.52f, -.36f, .85f, true, .55f, -.4f) }
        compose.mainClock.advanceTimeBy(430)
        compose.waitUntil(8_000) { capture()?.let { differences(quiet, it) > 35 } == true }
        compose.onNodeWithContentDescription("体感沉浸画面").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("退出体感沉浸").assertIsDisplayed()
    }

    private fun findSurface(view: View?): GLSurfaceView? {
        if (view is GLSurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findSurface(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test fun silentRippleSceneFitsArtworkAndCalmsWhenMotionIsReduced() {
        compose.mainClock.autoAdvance = false
        val artwork = stripedArtwork(markEdges = true)
        var tilt by mutableStateOf(Tilt(available = true))
        var reduced by mutableStateOf(false)
        compose.setContent { StilluneTheme {
            ImmersiveScene(PlaybackState(), artwork, ImmersionStyle.RIPPLES, tilt, AudioEnergy(),
                reducedMotion = reduced, onStyle = {}, onToggle = {}, onPrevious = {},
                onNext = {}, onSeek = {}, onExit = {})
        } }
        compose.mainClock.advanceTimeBy(120)
        val root = compose.onRoot()
        compose.waitUntil(timeoutMillis = 5_000) {
            val preview = root.captureToImage().toPixelMap()
            val side = minOf(preview.width * .86f, preview.height * .69f)
            val row = (preview.height * .46f).toInt()
            val left = preview[(preview.width * .5f - side * .42f).roundToInt(), row]
            val right = preview[(preview.width * .5f + side * .42f).roundToInt(), row]
            left.green > .96f && left.red < .04f && right.red > .96f && right.green > .96f
        }
        val still = root.captureToImage().toPixelMap()
        // Saturated edge markers prove both sides of the cover are visible. Above it, only the wash remains.
        val top = still[still.width / 2, 8]
        assertTrue(top.red in 0.15f..0.8f && top.blue in 0.15f..0.8f)
        compose.runOnIdle { tilt = Tilt(.55f, -.45f, .9f, true, .5f, -.4f) }
        compose.mainClock.advanceTimeBy(280)
        val moving = root.captureToImage().toPixelMap()
        assertTrue((30 until still.width - 30 step 10).any { x ->
            kotlin.math.abs(moving[x, still.height / 2].red - still[x, still.height / 2].red) > .15f
        })
        compose.runOnIdle { reduced = true }
        compose.mainClock.advanceTimeBy(160)
        val calm = root.captureToImage().toPixelMap()
        for (x in 30 until still.width - 30 step 10) {
            assertEquals(still[x, still.height / 2], calm[x, still.height / 2])
        }
    }

    @Test fun rippleRimsBrightenDarkBackdropsAndShadeLightBackdrops() {
        val width = 360
        val height = 760
        val water = WaterSurface().apply {
            resize(width, height)
            advance(.016f, Tilt(), 0f, 1f)
            repeat(42) { advance(.016f, Tilt(), 0f, 0f) }
        }
        for (value in listOf(32, 222)) {
            val artwork = Bitmap.createBitmap(64, 40, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(value, value, value))
            }
            val surface = CoverRefraction(artwork, width, height)
            val still = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val moving = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            surface.draw(Canvas(still), width.toFloat(), height.toFloat(), null, true)
            surface.draw(Canvas(moving), width.toFloat(), height.toFloat(), water, true)
            var brightest = 0
            var darkest = 0
            // Sample only the soft background above the fitted cover.
            for (y in 130..238 step 2) for (x in 40..320 step 2) {
                val delta = Color.red(moving.getPixel(x, y)) - Color.red(still.getPixel(x, y))
                brightest = maxOf(brightest, delta)
                darkest = minOf(darkest, delta)
            }
            if (value < 100) {
                assertTrue("Dark artwork must show a bright rim: $brightest", brightest >= 10)
                assertTrue("Dark artwork must still have a neighboring trough: $darkest", darkest <= -2)
            } else {
                assertTrue("Light artwork must show a dark trough: $darkest", darkest <= -10)
                assertTrue("Light artwork must still catch a bright rim: $brightest", brightest >= 2)
            }
        }
    }

    private fun stripedArtwork(markEdges: Boolean = false): Bitmap {
        val side = 256
        val pixels = IntArray(side * side) { index ->
            val column = index % side
            when {
                markEdges && column < 24 -> Color.GREEN
                markEdges && column >= side - 24 -> Color.YELLOW
                column / 9 % 2 == 0 -> Color.RED
                else -> Color.BLUE
            }
        }
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }
    @Test fun controlsStartHiddenWakeOnTapHideAfterTenSecondsAndTripleTapExits() {
        var exits = 0
        var plays = 0
        val track = Track("test", "月下，声音的纹理", fileName = "demo.flac", source = "test")
        compose.setContent { StilluneTheme {
            ImmersiveScene(PlaybackState(queue = listOf(track), index = 0, durationMs = 36000), null,
                ImmersionStyle.PARALLAX, Tilt(available = true), AudioEnergy(),
                onStyle = {}, onToggle = { plays++ }, onPrevious = {}, onNext = {}, onSeek = {}, onExit = { exits++ })
        } }
        compose.onNodeWithText(track.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("体感沉浸画面").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("播放").performClick()
        compose.runOnIdle { assertEquals(1, plays) }
        compose.mainClock.advanceTimeBy(9000)
        compose.onNodeWithContentDescription("沉浸播放进度").assertExists()
        compose.mainClock.advanceTimeBy(1300)
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("体感沉浸画面").performTouchInput {
            repeat(3) { click(center); advanceEventTime(120) }
        }
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun repeatedEffectSwitchesAndExplicitExitStayStable() {
        compose.mainClock.autoAdvance = false
        val track = Track("test", "彩色专辑", fileName="demo.flac",source="test")
        compose.setContent { StilluneTheme {
            var style by remember { mutableStateOf(ImmersionStyle.PARALLAX) }
            var exited by remember { mutableStateOf(false) }
            if (exited) Text("已退出沉浸") else ImmersiveScene(
                PlaybackState(queue=listOf(track),index=0,durationMs=36000,playing=true),null,style,
                Tilt(.8f,-.7f,.8f,true,.5f,-.5f),AudioEnergy(.4f,true),audioAllowed=true,
                onStyle={style=it},onToggle={},onPrevious={},onNext={},onSeek={},onExit={exited=true})
        } }
        compose.onNodeWithContentDescription("体感沉浸画面").performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(350)
        repeat(8) {
            listOf("节奏波纹","全息马赛克","封面视差").forEach { label ->
                compose.onNodeWithText(label).performClick()
                compose.mainClock.advanceTimeBy(120)
            }
        }
        compose.onNodeWithContentDescription("退出体感沉浸").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("已退出沉浸").assertIsDisplayed()
    }
}
