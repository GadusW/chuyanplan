package com.stillune.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
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
        compose.onNodeWithText("乐库").assertExists()
        compose.onNodeWithText("此刻").assertDoesNotExist()
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
        compose.onNodeWithText("此刻").assertExists()
        compose.onNodeWithText("乐库").assertDoesNotExist()
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
}
