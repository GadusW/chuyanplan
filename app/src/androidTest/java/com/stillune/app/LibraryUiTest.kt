package com.stillune.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.Assert.assertEquals

class LibraryUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    )).around(compose)

    @Test fun importActionsAndPlayerNavigationAreReachable() {
        compose.onNodeWithContentDescription("悬浮导航").assertExists()
        compose.onNodeWithText("艺术家").performScrollTo().performClick()
        compose.onNodeWithText("艺术家").assertIsSelected()
        compose.onNodeWithText("格式").performScrollTo().performClick()
        compose.onNodeWithText("格式").assertIsSelected()
        compose.onNodeWithText("歌曲").performScrollTo().performClick()
        compose.waitUntil(5000) {
            try { compose.onNodeWithText("导入音乐").assertIsEnabled(); true } catch (_: AssertionError) { false }
        }
        compose.onNodeWithText("导入音乐").performClick()
        // Modal sheets have a platform window transition in addition to Compose animation time.
        compose.waitUntil(5000) {
            try { compose.onNodeWithText("扫描手机音乐").assertIsDisplayed(); true } catch (_: AssertionError) { false }
        }
        compose.onNodeWithText("扫描手机音乐").assertIsDisplayed()
        compose.onNodeWithText("选择音乐文件夹").assertIsDisplayed()
        compose.onNodeWithText("导入音频文件").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭导入").performClick()
        compose.onNodeWithText("此刻").performClick()
        compose.onNodeWithContentDescription("播放队列").assertExists()
        compose.onNodeWithContentDescription("播放进度").assertExists()
        compose.onNodeWithText("音频分析").performClick()
        compose.onNodeWithText("音频分析").assertIsSelected()
        compose.onAllNodesWithText("声音画像").onFirst().assertExists()
        compose.onNodeWithText("极简").performClick()
        compose.onNodeWithText("极简").assertIsSelected()
        // Both layouts offer an explicit reverse path even without a lyric file.
        listOf("极简", "音频分析").forEach { mode ->
            compose.onNodeWithText(mode).performClick()
            try {
                compose.onNodeWithContentDescription("显示歌词").assertIsOn()
                compose.onNodeWithContentDescription("显示歌词").performSemanticsAction(SemanticsActions.OnClick) { it() }
            } catch (_: AssertionError) { }
            compose.onNodeWithContentDescription("显示歌词").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(3000) { compose.onAllNodesWithText("返回封面").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("返回封面").performClick()
            compose.onNodeWithContentDescription("显示歌词").assertIsOff()
        }
        compose.onNodeWithText("实时分析").performScrollTo().performClick()
        compose.onNodeWithContentDescription("实时分析播放进度").assertExists()
        compose.onNodeWithText("Short Term").assertExists()
        compose.onNodeWithContentDescription("实时分析播放选项").performClick()
        compose.waitUntil(3000) {
            try {compose.onNodeWithText("播放队列").assertIsDisplayed();true} catch (_: AssertionError) {false}
        }
        compose.onNodeWithText("播放队列").assertIsDisplayed()
        compose.onNodeWithText("输出设备").assertIsDisplayed()
        compose.onNodeWithText("封面 / 歌词").performClick()
        compose.onNodeWithText("返回封面").performScrollTo().performClick()
        compose.onNodeWithText("极简").performClick()
        compose.onNodeWithContentDescription("全屏播放").performClick()
        compose.onNodeWithContentDescription("退出全屏").assertExists()
        compose.onNodeWithText("乐库").assertDoesNotExist()
        try {
            compose.onNodeWithContentDescription("显示歌词").assertIsOn()
            compose.onNodeWithContentDescription("显示歌词").performSemanticsAction(SemanticsActions.OnClick) { it() }
        } catch (_: AssertionError) { /* Already off. */ }
        compose.onNodeWithContentDescription("显示歌词").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("显示歌词").assertIsOn()
        compose.onNodeWithContentDescription("显示歌词").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("显示歌词").assertIsOff()
        compose.onNodeWithContentDescription("退出全屏").performClick()
        compose.onNodeWithText("乐库").performClick()
        compose.onNodeWithText("寻找一首想听的歌").assertIsDisplayed()
    }
}
