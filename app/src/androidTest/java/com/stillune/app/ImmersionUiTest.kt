package com.stillune.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.roundToInt

class ImmersionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realAttackEventChangesTheRenderedRippleAndParticleFields() {
        compose.mainClock.autoAdvance = false
        var style by mutableStateOf(ImmersionStyle.RIPPLES)
        var audio by mutableStateOf(AudioEnergy(available = true))
        val artwork = stripedArtwork()
        compose.setContent { StilluneTheme {
            ImmersiveScene(PlaybackState(playing = true), artwork, style, Tilt(), audio,
                audioAllowed = true, onStyle = {}, onToggle = {}, onPrevious = {},
                onNext = {}, onSeek = {}, onExit = {})
        } }
        for (choice in listOf(ImmersionStyle.RIPPLES, ImmersionStyle.PARTICLES)) {
            compose.runOnIdle { style = choice; audio = AudioEnergy(available = true) }
            compose.mainClock.advanceTimeBy(100)
            if (choice == ImmersionStyle.RIPPLES) compose.waitUntil(timeoutMillis = 5_000) {
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
            assertTrue("$choice must visibly react to the audio attack", changes > 20)
        }
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
            listOf("节奏波纹","节奏粒子","封面视差").forEach { label ->
                compose.onNodeWithText(label).performClick()
                compose.mainClock.advanceTimeBy(120)
            }
        }
        compose.onNodeWithContentDescription("退出体感沉浸").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("已退出沉浸").assertIsDisplayed()
    }
}
