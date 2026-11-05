package com.stillune.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.stillune.app.data.LrcParser
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.ui.AlbumDeck
import com.stillune.app.ui.StilluneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlbumDeckUiTest {
    @get:Rule val compose = createComposeRule()
    private var index = 0
    private fun render(small: Boolean = false, plain: Boolean = false) {
        val queue = (1..3).map { Track("test:$it", "测试歌曲 $it", fileName = "$it.mp3", source = "test") }
        compose.setContent {
            var cursor by remember { mutableIntStateOf(0) }
            var lyrics by remember { mutableStateOf(false) }
            StilluneTheme {
                AlbumDeck(PlaybackState(queue, cursor), null, LrcParser.parse(if (plain) "第一句歌词\n第二句歌词\n第三句歌词" else "[00:00.00]第一句歌词\n[00:03.00]第二句歌词"),
                    lyrics, { null }, { cursor++; index = cursor }, { lyrics = !lyrics }, {}, {},
                    (if (small) Modifier.width(154.dp).height(164.dp) else Modifier.fillMaxWidth().height(410.dp)))
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

    @Test fun peelingKeepsCardFixedAndReturnsWithoutSkippingMusic() {
        render()
        val before = compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("album-front").performTouchInput {
            down(Offset(width - 40f, height - 40f))
            moveTo(Offset(width * .4f, height * .45f), 450)
        }
        val during = compose.onNodeWithTag("album-front").fetchSemanticsNode()
        assertEquals(before, during.boundsInRoot)
        org.junit.Assert.assertFalse(during.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].contains("揭角 0%"))
        compose.onNodeWithTag("album-front").performTouchInput { moveTo(Offset(-width * .3f, -height * .4f), 400) }
        assertEquals(before, compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("album-front").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("album-front").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "揭角 0%"))
        compose.runOnIdle { assertEquals(0, index) }
        // Explicit accessibility action remains available without requiring a precision drag.
        compose.onNodeWithContentDescription("揭角查看歌词").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("返回封面").assertExists().performClick()
    }

    @Test fun smallLandscapeCardPeelsAndCenterDragStillChangesSong() {
        render(small = true)
        val before = compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("album-front").performTouchInput {
            down(Offset(width - 40f, height - 40f))
            moveTo(center, 350)
        }
        val during = compose.onNodeWithTag("album-front").fetchSemanticsNode()
        assertEquals(before, during.boundsInRoot)
        org.junit.Assert.assertFalse(during.config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "揭角 0%")
        compose.onNodeWithTag("album-front").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0, index) }
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(0f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, index) }
    }

    @Test fun plainTextLyricsPreviewStartsWithFirstLine() {
        render(plain = true)
        compose.onNodeWithTag("album-front").performTouchInput {
            down(Offset(width - 40f, height - 40f))
            moveTo(Offset(width * .2f, height * .2f), 350)
        }
        compose.onNodeWithText("第一句歌词").assertExists()
        compose.onNodeWithText("第二句歌词").assertExists()
        compose.onNodeWithTag("album-front").performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, index) }
    }
}
