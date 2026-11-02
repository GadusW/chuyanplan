package com.stillune.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stillune.app.analysis.*
import com.stillune.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RealtimeUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun targetsSettingsSourcesCameraAndExpandedViewsOperateWithoutResettingMeasurements() {
        var options by mutableStateOf(RealtimeOptions())
        var resets=0
        val frame=RealtimeFrame(status="正在测量",running=true,measuredMs=36000,sourceChannels=2,
            momentary=-21.0,momentaryMax=-18.0,shortTerm=-22.0,integrated=-23.1,lra=5.2,truePeak=-2.5,
            spectrum=FloatArray(128) {-40f},history=List(20) {FloatArray(128) {-40f}})
        compose.setContent {StilluneTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                RealtimeAnalysisContent(frame,options,{options=it},{resets++})
            }
        }}
        compose.onNodeWithText("Max · 400 ms").assertExists()
        compose.onNodeWithText("不启用目标").performClick()
        compose.onNodeWithText("EBU R128").performClick()
        compose.onNodeWithText("本次片段响度在参考范围内").assertExists()
        compose.onNodeWithContentDescription("频谱 设置").performScrollTo().performClick()
        compose.onNodeWithText("8192").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle {assertEquals(8192,options.fftSize);assertEquals(0,resets)}
        compose.onNodeWithContentDescription("放大频谱").performScrollTo().performClick()
        compose.onAllNodesWithText("输入源 · 双声道能量").onLast().performClick()
        compose.onNodeWithText("左声道 L").performClick()
        compose.onNodeWithContentDescription("关闭放大图表").performClick()
        compose.runOnIdle {assertEquals(SpectrumSource.LEFT,options.source)}
        compose.onNodeWithContentDescription("放大频谱瀑布").performScrollTo().performClick()
        compose.onAllNodesWithText("斜向").onLast().performClick()
        compose.onNodeWithContentDescription("关闭放大图表").performClick()
        compose.onNodeWithContentDescription("放大立体声声场").performScrollTo().performClick()
        compose.onAllNodesWithText("Lissajous").onLast().performClick()
        compose.onNodeWithContentDescription("关闭放大图表").performClick()
        compose.onNodeWithContentDescription("重新测量").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(1,resets)}
    }
}
