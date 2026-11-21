package com.stillune.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import java.io.File
import kotlin.math.abs
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryLabelsAreCenteredAndFloatingNavigationRespondsToTouch() {
        compose.setContent {
            var tab by remember { mutableStateOf("歌曲") }
            var player by remember { mutableStateOf(false) }
            StilluneTheme {
                Column {
                    LibraryTabs(tab) { tab = it }
                    FloatingNavigation(player, { player = false }, { player = true })
                }
            }
        }
        compose.onNodeWithText("乐库").assertDoesNotExist()
        compose.onNodeWithText("此刻").assertExists()
        val slotWidth = compose.onNodeWithContentDescription("此刻").fetchSemanticsNode().boundsInRoot.width
        listOf("专辑", "艺术家", "文件夹").forEach { label ->
            compose.onNodeWithText(label).performClick().assertIsSelected()
            val button = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("$label horizontal center", button.center.x, text.center.x, 1.5f)
            assertEquals("$label vertical center", button.center.y, text.center.y, 1.5f)
        }
        compose.onNodeWithContentDescription("此刻").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithContentDescription("此刻").performTouchInput { up() }
        compose.onNodeWithContentDescription("此刻").assertIsSelected()
        compose.onNodeWithText("此刻").assertDoesNotExist()
        compose.onNodeWithText("乐库").assertExists()
        assertEquals("Slide anchors keep their positions", slotWidth,
            compose.onNodeWithContentDescription("此刻").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.onNodeWithContentDescription("乐库").performClick().assertIsSelected()
    }

    @Test fun draggingAcrossTwoTabsCommitsOnlyAfterReleaseAndCancellationKeepsThePage() {
        val visits = mutableListOf<Int>()
        compose.setContent {
            var page by remember { mutableIntStateOf(0) }
            fun open(index: Int) { page = index; visits.add(index) }
            StilluneTheme {
                FloatingNavigation(page == 1, { open(0) }, { open(1) }, page == 2, { open(2) })
            }
        }
        val rail = compose.onNodeWithContentDescription("悬浮导航")
        val area = rail.fetchSemanticsNode().boundsInRoot
        fun anchor(name: String) = compose.onNodeWithContentDescription(name)
            .fetchSemanticsNode().boundsInRoot.center - area.topLeft
        val library = anchor("乐库")
        val now = anchor("此刻")
        val hearing = anchor("护耳")

        rail.performTouchInput {
            down(library)
            moveTo(now, 96L)
            moveTo(hearing, 96L)
        }
        compose.onNodeWithContentDescription("乐库").assertIsSelected()
        compose.runOnIdle { assertEquals(emptyList<Int>(), visits) }
        rail.performTouchInput { up() }
        compose.onNodeWithContentDescription("护耳").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(2), visits) }

        rail.performTouchInput {
            down(hearing)
            moveTo(library, 120L)
            cancel()
        }
        compose.onNodeWithContentDescription("护耳").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(2), visits) }
    }
    @Test fun pressingTheCurrentModeCyclesAndASmallMoveStillCountsAsAClick() {
        val visits = mutableListOf<Int>()
        compose.setContent {
            var page by remember { mutableIntStateOf(0) }
            fun open(index: Int) { page = index; visits.add(index) }
            StilluneTheme {
                FloatingNavigation(page == 1, { open(0) }, { open(1) }, page == 2, { open(2) })
            }
        }
        compose.onNodeWithContentDescription("乐库").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(3f, 0f), 80L)
            up()
        }
        compose.onNodeWithContentDescription("此刻").assertIsSelected()
        compose.onNodeWithText("此刻").assertDoesNotExist()
        compose.onNodeWithText("乐库").assertExists()
        compose.onNodeWithText("护耳").assertExists()
        compose.onNodeWithContentDescription("此刻").performClick().assertIsNotSelected()
        compose.onNodeWithContentDescription("护耳").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(1, 2), visits) }
    }

    @Test fun theButtonFaceStaysNeutralAcrossBlueAndGoldEdgeLights() {
        compose.setContent {
            var page by remember { mutableIntStateOf(0) }
            StilluneTheme {
                FloatingNavigation(page == 1, { page = 0 }, { page = 1 }, page == 2, { page = 2 })
            }
        }
        val rail = compose.onNodeWithContentDescription("悬浮导航")
        val root = rail.fetchSemanticsNode().boundsInRoot
        fun faceSample(name: String): androidx.compose.ui.graphics.Color {
            val bounds = compose.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot
            val image = rail.captureToImage()
            val x = (bounds.left - root.left + bounds.width * .22f).toInt()
            val y = (bounds.center.y - root.top).toInt()
            return image.toPixelMap()[x, y]
        }
        val blue = faceSample("乐库")
        compose.onNodeWithContentDescription("护耳")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
            .assertIsSelected()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val gold = faceSample("护耳")
        assertTrue("Button face should stay neutral as the rim light changes: blue=$blue gold=$gold",
            abs(blue.red - blue.blue) < .025f && abs(blue.green - blue.blue) < .025f &&
                abs(gold.red - gold.blue) < .025f && abs(gold.green - gold.blue) < .025f &&
                minOf(blue.red, blue.green, blue.blue, gold.red, gold.green, gold.blue) > .9f)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "navigation-light-02414.png").outputStream().use {
            rail.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

}
