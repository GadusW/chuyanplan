package com.stillune.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class GettingStartedUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guideLeadsToReadingSearchAndImportWithoutDeveloperSetup() {
        compose.onNodeWithText("使用指南").performScrollTo().performClick()
        compose.onNodeWithText("从一首歌开始").assertIsDisplayed()
        compose.onNodeWithText("先读一篇聆听知识").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("三分钟，听懂一点").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜索知识或主题").performScrollTo().performTextInput("找不到的主题")
        compose.onNodeWithText("查看全部知识").performScrollTo().performClick()
        compose.onNodeWithText("搜索知识或主题").assertExists()
        compose.onNodeWithContentDescription("导入知识更新").performClick()
        compose.onNodeWithText("添加新的三分钟知识").assertIsDisplayed()
        compose.onNodeWithText("选择 ZIP 文件").assertIsEnabled()
        compose.onNodeWithText("先读内置文章").performClick()
        compose.onNodeWithText("读一篇未读", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("放大阅读字号").performClick()
        compose.onNodeWithContentDescription("使用标准字号").assertExists().performClick()
        compose.onNodeWithContentDescription("关闭知识阅读").performClick()
        compose.onNodeWithContentDescription("关闭知识书架").performClick()
        compose.onNodeWithText("使用指南").performScrollTo().assertIsDisplayed()
    }
}
