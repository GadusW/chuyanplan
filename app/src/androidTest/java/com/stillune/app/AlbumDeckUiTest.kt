package com.stillune.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.stillune.app.data.LrcParser
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.playback.RepeatMode
import com.stillune.app.ui.AlbumDeck
import com.stillune.app.ui.StilluneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlbumDeckUiTest {
    @get:Rule val compose = createComposeRule()
    private var index = 0
    private fun render(small: Boolean = false, plain: Boolean = false, repeat: RepeatMode = RepeatMode.OFF) {
        val queue = (1..3).map { Track("test:$it", "测试歌曲 $it", fileName = "$it.mp3", source = "test") }
        compose.setContent {
            var cursor by remember { mutableIntStateOf(0) }
            var lyrics by remember { mutableStateOf(false) }
            StilluneTheme {
                val state = PlaybackState(queue, cursor, repeat = repeat)
                val steps = remember { MutableSharedFlow<Int>(extraBufferCapacity = 1) }
                Column {
                AlbumDeck(state, null, LrcParser.parse(if (plain) "第一句歌词\n第二句歌词\n第三句歌词" else "[00:00.00]第一句歌词\n[00:03.00]第二句歌词"),
                    lyrics, { null }, { state.nextIndex?.let { cursor = it; index = it } }, { lyrics = !lyrics }, {}, {},
                    (if (small) Modifier.width(154.dp).height(164.dp) else Modifier.fillMaxWidth().height(410.dp)),
                    onPrevious = { state.previousIndex?.let { cursor = it; index = it } }, stepRequests = steps)
                Row {
                    TextButton(onClick = { steps.tryEmit(-1) }) { Text("按键上一首") }
                    TextButton(onClick = { steps.tryEmit(1) }) { Text("按键下一首") }
                }
                }
            }
        }
    }

    @Test fun shortDragSpringsBackAndCommittedDragsAdvanceOnlyOnce() {
        render()
        compose.onNodeWithTag("album-remaining").assertTextEquals("接下来 2 首")
        compose.onNodeWithTag("album-front").performTouchInput {
            swipe(center, center - Offset(width * .13f, 0f), 350)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, index) }
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(-width * .3f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, index) }
        compose.onNodeWithTag("album-remaining").assertTextEquals("接下来 1 首")
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(-width * .3f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, index) }
        compose.onNodeWithTag("album-remaining").assertTextEquals("接下来 0 首")
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(-width * .3f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, index) }
    }

    @Test fun tapAnywhereOnCoverFlipsAndLyricTapReturns() {
        render()
        compose.onNodeWithTag("album-front").performTouchInput { click(Offset(width - 22f, height - 22f)) }
        compose.onNodeWithText("测试歌曲 1").assertIsDisplayed()
        compose.onNodeWithText("返回封面").assertDoesNotExist()
        compose.onNodeWithText("第一句歌词").performTouchInput { click(center) }
        compose.onNodeWithText("测试歌曲 1").assertDoesNotExist()
    }

    @Test fun compactDeckSupportsSwipeWithoutACornerZone() {
        render(small = true)
        compose.onNodeWithTag("album-front").performTouchInput {
            swipe(Offset(width - 22f, height - 22f), Offset(0f, height - 22f), 350)
        }
        compose.runOnIdle { assertEquals(1, index) }
    }

    @Test fun transportAnimatesDeckBeforeCommittingAndWraps() {
        render(repeat = RepeatMode.ALL)
        val initial = compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("按键上一首").performClick()
        compose.mainClock.advanceTimeBy(112)
        val moving = compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(moving.center.x > initial.center.x)
        assertEquals(0, index)
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(2, index) }
        compose.onNodeWithText("按键下一首").performClick()
        compose.runOnIdle { assertEquals(0, index) }
    }

    @Test fun reverseSwipeWrapsBetweenFirstAndLastInLoop() {
        render(repeat = RepeatMode.ALL)
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(width * 1.2f, centerY), 350) }
        compose.runOnIdle { assertEquals(2, index) }
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(0f, centerY), 350) }
        compose.runOnIdle { assertEquals(0, index) }
    }

    @Test fun plainTextBackStartsWithFirstLine() {
        render(plain = true)
        compose.onNodeWithTag("album-front").performTouchInput { click(center) }
        compose.onNodeWithText("第一句歌词").assertIsDisplayed()
        compose.onNodeWithText("第二句歌词").assertIsDisplayed()
    }
}
