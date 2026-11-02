package com.stillune.app

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

class ImmersionUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realAttackEventChangesTheRenderedRippleAndParticleFields() {
        compose.mainClock.autoAdvance = false
        var style by mutableStateOf(ImmersionStyle.RIPPLES)
        var audio by mutableStateOf(AudioEnergy(available = true))
        compose.setContent { StilluneTheme {
            ImmersiveScene(PlaybackState(playing = true), null, style, Tilt(), audio,
                audioAllowed = true, onStyle = {}, onToggle = {}, onPrevious = {},
                onNext = {}, onSeek = {}, onExit = {})
        } }
        for (choice in listOf(ImmersionStyle.RIPPLES, ImmersionStyle.PARTICLES)) {
            compose.runOnIdle { style = choice; audio = AudioEnergy(available = true) }
            compose.mainClock.advanceTimeBy(100)
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
