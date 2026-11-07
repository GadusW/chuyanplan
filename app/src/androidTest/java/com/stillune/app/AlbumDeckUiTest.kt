package com.stillune.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
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
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.data.LrcParser
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.playback.RepeatMode
import com.stillune.app.ui.AlbumDeck
import com.stillune.app.ui.AlbumCardPhotos
import com.stillune.app.ui.StilluneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

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
        val following = compose.onNodeWithTag("album-card-test:2", useUnmergedTree = true)
        val followingId = following.fetchSemanticsNode().id
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(-width * .3f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, index) }
        compose.onNodeWithTag("album-remaining").assertTextEquals("接下来 1 首")
        assertEquals(followingId, following.fetchSemanticsNode().id)
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(-width * .3f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, index) }
        compose.onNodeWithTag("album-remaining").assertTextEquals("接下来 0 首")
        compose.onNodeWithTag("album-front").performTouchInput { swipe(center, Offset(-width * .3f, centerY), 350) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, index) }
    }

    @Test fun neighboringCardKeepsItsIdentityWhenPromotedByTransport() {
        render()
        fun card(index: Int) = compose.onNodeWithTag("album-card-test:$index", useUnmergedTree = true)
        val front = card(1).fetchSemanticsNode()
        val neighbor = card(2).fetchSemanticsNode()
        val frontX = front.boundsInRoot.center.x
        val neighborX = neighbor.boundsInRoot.center.x
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("按键下一首").performClick()
        compose.mainClock.advanceTimeBy(112)
        assertEquals(front.id, card(1).fetchSemanticsNode().id)
        assertEquals(neighbor.id, card(2).fetchSemanticsNode().id)
        org.junit.Assert.assertTrue(card(1).fetchSemanticsNode().boundsInRoot.center.x < frontX)
        val incoming = card(2).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(incoming.center.x < neighborX)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("album-remaining").assertTextEquals("接下来 1 首")
        val promoted = card(2).fetchSemanticsNode()
        assertEquals(neighbor.id, promoted.id)
        assertEquals(frontX, promoted.boundsInRoot.center.x, 1f)
        org.junit.Assert.assertTrue("Promoted cover must face forward at its full width",
            promoted.boundsInRoot.width > incoming.width)
    }

    @Test fun consecutiveTransportStepsAnimateTheCurrentCardOnEveryPress() {
        render()
        listOf(1, 1, -1).forEach { step ->
            val startingIndex = index
            val center = compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot.center.x
            compose.mainClock.autoAdvance = false
            compose.onNodeWithText(if (step > 0) "按键下一首" else "按键上一首").performClick()
            compose.mainClock.advanceTimeBy(128)
            val shifted = compose.onNodeWithTag("album-front").fetchSemanticsNode().boundsInRoot.center.x
            org.junit.Assert.assertTrue("The active card must move on step from $startingIndex", (center - shifted) * step > 1f)
            assertEquals(startingIndex, index)
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { assertEquals(startingIndex + step, index) }
        }
    }

    @Test fun tapAnywhereOnCoverFlipsAndLyricTapReturns() {
        render()
        compose.onNodeWithTag("album-front").performTouchInput { click(Offset(width - 22f, height - 22f)) }
        compose.onNodeWithText("测试歌曲 1").assertIsDisplayed()
        compose.onNodeWithText("返回封面").assertDoesNotExist()
        compose.onNodeWithText("第一句歌词").performTouchInput { click(center) }
        compose.onNodeWithText("第一句歌词").assertDoesNotExist()
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

    @Test fun longPressManagesPhotosAndPreferredPhotoStaysOnSideCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val track = Track("test:${UUID.randomUUID()}", "照片测试", fileName = "test.mp3", source = "test")
        val cover = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val source = File.createTempFile("card-photo", ".png", context.cacheDir)
        val photos = AlbumCardPhotos(context)
        source.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
        try {
            runBlocking {
                repeat(2) { assertEquals(true, photos.add(track.identity, Uri.fromFile(source))) }
            }
        } finally {
            source.delete()
        }
        var cursor by mutableIntStateOf(0)
        val queue = listOf(track) + (1..2).map { track.copy(uri = "test:other:$it", identity = "test:other:$it") }
        val steps = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        compose.setContent {
            StilluneTheme {
                Column {
                    AlbumDeck(PlaybackState(queue, cursor, repeat = RepeatMode.OFF), cover, LrcParser.parse(""), false, { cover },
                        { cursor++ }, {}, {}, {}, Modifier.fillMaxWidth().height(410.dp),
                        stepRequests = steps, cachedArtwork = { cover })
                    TextButton(onClick = { steps.tryEmit(1) }) { Text("按键下一首") }
                }
            }
        }
        fun down() = compose.onNodeWithTag("album-front").performTouchInput {
            swipe(center, center + Offset(0f, height * .4f), 350)
        }
        fun menu() = compose.onNodeWithTag("album-front").performTouchInput { longClick(center) }
        fun choose(text: String) = compose.onNodeWithText(text).performClick()
        val ids = photos.photos(track.identity)

        menu()
        compose.onNodeWithText("删除这张图片").assertIsNotEnabled()
        choose("设为首选图")
        down()
        menu()
        choose("设为首选图")
        assertEquals(ids[0], photos.preferred(track.identity))
        down()
        menu()
        choose("设为首选图")
        assertEquals(ids[1], photos.preferred(track.identity))
        choose("按键下一首")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("接下来 1 首").fetchSemanticsNodes().isNotEmpty() }
        val sidePhoto = hasContentDescription("自定义卡片图片") and
            hasAnyAncestor(hasTestTag("album-card-${track.uri}"))
        compose.waitUntil(5_000) {
            compose.onAllNodes(sidePhoto, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { cursor = 0 }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("接下来 2 首").fetchSemanticsNodes().isNotEmpty() }
        menu()
        choose("删除这张图片")
        compose.waitUntil(5_000) { photos.photos(track.identity) == listOf(ids[0]) }
        assertEquals(null, photos.preferred(track.identity))
    }

    @Test fun firstPickerResultUpdatesTheAlreadyRenderedCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val track = Track("test:${UUID.randomUUID()}", "首次导入", fileName = "test.mp3", source = "test")
        val cover = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val source = File.createTempFile("first-card-photo", ".png", context.cacheDir)
        source.outputStream().use { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var pickerRequest: Int? = null
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    pickerRequest = requestCode
                }
            }
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    StilluneTheme {
                        AlbumDeck(PlaybackState(listOf(track), 0), cover, LrcParser.parse(""), false, { cover },
                            {}, {}, {}, {}, Modifier.fillMaxWidth().height(410.dp), cachedArtwork = { cover })
                    }
                }
            }
            fun swipePhoto(direction: Float) = compose.onNodeWithTag("album-front").performTouchInput {
                swipe(center, center + Offset(0f, height * direction), 350)
            }
            swipePhoto(-.4f)
            compose.runOnIdle {
                owner.activityResultRegistry.dispatchResult(
                    checkNotNull(pickerRequest), Activity.RESULT_OK, Intent().setData(Uri.fromFile(source))
                )
            }
            val photos = AlbumCardPhotos(context)
            compose.waitUntil(5_000) { photos.photos(track.identity).size == 1 }
            swipePhoto(.4f)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("自定义卡片图片").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            source.delete()
        }
    }
}
