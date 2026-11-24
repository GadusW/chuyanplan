package com.stillune.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.health.*
import com.stillune.app.ui.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HearingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun listeningCalendarSelectsDatesAndShowsTheirBreakdown() {
        val today = LocalDate.of(2026, 11, 24)
        val days = List(31) { ListeningDay(today.minusDays(30L - it)) }.toMutableList()
        days[29] = days[29].copy(listenedMs = 3_720_000, headphoneMs = 2_400_000, abovePreferenceMs = 180_000)
        days[30] = days[30].copy(listenedMs = 600_000, headphoneMs = 300_000)
        compose.setContent { StilluneTheme {
            Column { ListeningCalendar(HearingHistory(days, 4_320_000), continuousMs = 180_000) }
        } }

        val todayCell = compose.onNodeWithContentDescription("今天，11月24日，10 分钟")
        val yesterdayCell = compose.onNodeWithContentDescription("11月23日，1 小时 2 分")
        todayCell.assertIsSelected()
        compose.onNodeWithText("今日聆听 · 10 分钟").assertIsDisplayed()
        yesterdayCell.performClick().assertIsSelected()
        todayCell.assertIsNotSelected()
        compose.onNodeWithText("11月23日 · 1 小时 2 分").assertIsDisplayed()
        compose.onNodeWithText("耳机 / 蓝牙 40 分钟").assertIsDisplayed()
        compose.onNodeWithText("超过当时提醒档位 3 分钟").assertIsDisplayed()
        compose.onNodeWithText("滚动 7 日 1 小时 12 分").assertIsDisplayed()
        compose.onNodeWithText("本段累计 3 分钟").assertIsDisplayed()

        compose.onNodeWithContentDescription("11月22日，无聆听记录").performClick().assertIsSelected()
        compose.onNodeWithText("11月22日 · 0 秒").assertIsDisplayed()
        todayCell.performClick()
        compose.onNodeWithText("今日聆听 · 10 分钟").assertIsDisplayed()
    }

    @Test fun healthIsThirdDestinationAndControlsAreHonestAndOperable() {
        var settings by mutableStateOf(HearingSettings())
        var read = false
        var selected by mutableIntStateOf(0)
        compose.setContent { StilluneTheme { Column {
            Box(Modifier.weight(1f)) {
                HearingContent(settings, HearingLive(volumeStep = 4, volumeMax = 15), emptyList(), EnvironmentReading(), false,
                    "系统音频输出", false, { settings = it(settings) }, {}, {}, {}, { read = true }, {}, {}, {})
            }
            FloatingNavigation(selected == 1, { selected = 0 }, { selected = 1 }, selected == 2, { selected = 2 })
        } } }
        compose.onAllNodesWithText("护耳", substring = false).onFirst().assertExists()
        compose.onNodeWithContentDescription("启用护耳").performClick().assertIsOn()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("20 分钟"))
        compose.onNodeWithText("20 分钟").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(20, settings.breakMinutes) }
        compose.onNodeWithContentDescription("提醒时自动暂停").performScrollTo().performClick().assertIsOn()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("入耳声压与剂量：尚无经验证的声学校准，不显示估算值。"))
        compose.onNodeWithText("入耳声压与剂量：尚无经验证的声学校准，不显示估算值。").performScrollTo().assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("打开知识书架"))
        compose.onNodeWithText("打开知识书架").performClick()
        compose.runOnIdle { assertTrue(read) }
        compose.onNodeWithContentDescription("护耳").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("此刻").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("乐库").performClick().assertIsSelected()
    }
    @Test fun knowledgeReaderSupportsFavoriteAndClosesWithoutAutoplay() {
        var open by mutableStateOf(true)
        var favorite by mutableStateOf(false)
        var finished = false
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val article = context.assets.open("knowledge/builtin.json").use {
            KnowledgePackage.parse(it.readBytes()).first()
        }
        compose.setContent { StilluneTheme {
            if (open) KnowledgeReader(article, favorite, { favorite = !favorite }, { open = false }, { finished = true; open = false })
        } }
        compose.onNodeWithText(article.title).assertExists()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("收藏这篇").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("读完了，歇一会儿"))
        compose.onNodeWithText("读完了，歇一会儿").performClick()
        compose.runOnIdle { assertTrue(finished); assertFalse(open) }
    }

    @Test fun shelfFiltersReadAndFavoritesAndOffersImport() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val articles = context.assets.open("knowledge/builtin.json").use { KnowledgePackage.parse(it.readBytes()) }
        var imported = false
        compose.setContent { StilluneTheme {
            KnowledgeShelf(KnowledgeState(articles, read = setOf(articles.first().id),
                favorites = setOf(articles[1].id), ready = true), KnowledgeImportState(),
                {}, { imported = true }, {})
        } }
        compose.onNodeWithText("收藏").performClick()
        compose.onNodeWithText(articles[1].title).assertExists()
        compose.onNodeWithText(articles.first().title).assertDoesNotExist()
        compose.onNodeWithText("导入知识更新").performScrollTo().performClick()
        compose.onNodeWithText("选择 ZIP 文件").performClick()
        compose.runOnIdle { assertTrue(imported) }
    }
}
