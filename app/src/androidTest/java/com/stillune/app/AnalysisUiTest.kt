package com.stillune.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.analysis.*
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackState
import com.stillune.app.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnalysisUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cachedSpectrogramSeeksAndMetricsCanBeReadWithoutADecoder() {
        var target = -1L
        val track = Track("test", "Test", fileName="test.flac",source="test")
        val cache = AnalysisCache("test","test",state="ready",durationMs=10000,sampleStartMs=3333,sampleDurationMs=6667,
            drEstimate=9.5,correlation=.4,width=.55,sourceChannels=2,
            spectrum=ByteArray(AudioAccumulator.COLUMNS*AudioAccumulator.BANDS) { (it%256).toByte() })
        compose.setContent { StilluneTheme {
            AnalysisContent(cache, PlaybackState(queue=listOf(track),index=0), {}, { target=it },
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        } }
        compose.onNodeWithContentDescription("整曲频谱进度").performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
        compose.runOnIdle { assertEquals(7500L,target) }
        compose.onNodeWithText("DR 片段估算").assertExists()
        compose.onNodeWithText("立体声声场").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("分析结果说明").performScrollTo().performClick()
        compose.onNodeWithText("怎样读懂声音画像").assertIsDisplayed()
    }
}
