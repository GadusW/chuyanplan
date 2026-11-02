package com.stillune.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.data.LrcParser
import com.stillune.app.ui.LyricsPane
import com.stillune.app.ui.StilluneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Rule
import org.junit.Test

class LyricsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun activeLineIsHighlightedAndTappingAnotherLineSeeks() {
        var requested = -1L
        val lyrics = LrcParser.parse("[00:00.00]让声音停泊\n[00:02.00]让热爱长久\n[00:04.00]此刻安心聆听")
        compose.setContent { StilluneTheme { LyricsPane(lyrics, 2500, true, { requested = it }, {}) } }
        compose.onNodeWithText("让热爱长久").assertIsSelected()
        compose.onNodeWithText("让声音停泊").performClick()
        compose.runOnIdle { assertEquals(0L, requested) }
    }

    @Test fun singleTapReturnsToCoverAndDoubleTapStillSeeks() {
        var returned = false
        var requested = -1L
        val lyrics = LrcParser.parse("[00:00.00]让声音停泊\n[00:02.00]让热爱长久")
        compose.setContent { StilluneTheme {
            LyricsPane(lyrics, 0, true, { requested = it }, {}, onReturnToCover = { returned = true })
        } }
        compose.onNodeWithText("让热爱长久").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(2000L, requested); assertEquals(false, returned) }
        compose.onNodeWithText("让声音停泊").performTouchInput { click() }
        // Tap recognition waits for the system double-tap timeout.
        compose.waitUntil(3000) { returned }
        compose.runOnIdle { assertTrue(returned) }
    }
}
