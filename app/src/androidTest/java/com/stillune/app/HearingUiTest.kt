package com.stillune.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.health.*
import com.stillune.app.data.Track
import com.stillune.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HearingUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun healthIsThirdDestinationAndControlsAreHonestAndOperable() {
        var settings by mutableStateOf(HearingSettings())
        var read: String? = null
        var selected by mutableIntStateOf(0)
        compose.setContent { StilluneTheme { Column {
            Box(Modifier.weight(1f)) {
                HearingContent(settings, HearingLive(volumeStep = 4, volumeMax = 15), emptyList(), EnvironmentReading(), false,
                    "系统音频输出", false, { settings = it(settings) }, {}, {}, {}, { read = it }, {}, {}, {})
            }
            FloatingNavigation(selected == 1, { selected = 0 }, { selected = 1 }, selected == 2, { selected = 2 })
        } } }
        compose.onAllNodesWithText("护耳", substring = false).onFirst().assertExists()
        compose.onNodeWithContentDescription("启用护耳").performClick().assertIsOn()
        compose.onNodeWithText("20 分钟").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(20, settings.breakMinutes) }
        compose.onNodeWithContentDescription("提醒时自动暂停").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("入耳声压与剂量：尚无经验证的声学校准，不显示估算值。").performScrollTo().assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(AppreciationLibrary.articles.first().title))
        compose.onNodeWithText(AppreciationLibrary.articles.first().title).performClick()
        compose.runOnIdle { assertEquals("space", read) }
        compose.onAllNodesWithText("护耳", substring = false).onLast().performClick().assertIsSelected()
        compose.onNodeWithText("此刻").performClick().assertIsSelected()
        compose.onNodeWithText("乐库").performClick().assertIsSelected()
    }
    @Test fun silentReaderClosesWithoutOfferingAutoplay() {
        var open by mutableStateOf(true)
        compose.setContent { StilluneTheme { if (open) AppreciationReader(AppreciationLibrary.articles.first()) { open = false } } }
        compose.onNodeWithText("听见一张唱片里的空间").assertExists()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("关闭音乐小札").performClick()
        compose.runOnIdle { assertFalse(open) }
    }

    @Test fun trackReadingShowsLocalLessonBeforeAnyNetworkRequest() {
        var requested = false
        val track = Track("content://test/1", "本地试音", fileName = "test.flac", source = "test")
        compose.setContent {
            StilluneTheme {
                TrackReadingDialog(TrackReading(track, analysisLessons.first()), { requested = true }, {})
            }
        }
        compose.onNodeWithText("本地试音").assertExists()
        compose.onNodeWithText("一分钟看懂音频分析").assertExists()
        compose.runOnIdle { assertFalse(requested) }
        compose.onNodeWithText("查找开放乐评").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(requested) }
    }
}
