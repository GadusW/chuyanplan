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

    @Test fun tabsAreCenteredAndNavigationLabelsStayVisible() {
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
        compose.onNodeWithText("播放").assertExists()
        val initialBounds = compose.onNodeWithContentDescription("播放页").fetchSemanticsNode().boundsInRoot
        listOf("专辑", "艺术家", "文件夹").forEach { label ->
            compose.onNodeWithText(label).performClick().assertIsSelected()
            val button = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("$label horizontal center", button.center.x, text.center.x, 1.5f)
            assertEquals("$label vertical center", button.center.y, text.center.y, 1.5f)
        }
        compose.onNodeWithContentDescription("播放页").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithContentDescription("播放页").performTouchInput { up() }
        compose.onNodeWithContentDescription("播放页").assertIsSelected()
        compose.onNodeWithText("播放").assertExists()
        compose.onNodeWithText("乐库").assertExists()
        assertEquals("Selection keeps the navigation target stable", initialBounds,
            compose.onNodeWithContentDescription("播放页").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("乐库").performClick().assertIsSelected()
    }
}
