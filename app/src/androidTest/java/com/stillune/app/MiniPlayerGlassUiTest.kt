package com.stillune.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.stillune.app.ui.GlassMaterial
import com.stillune.app.ui.GlassScene
import com.stillune.app.ui.glassPanel
import com.stillune.app.ui.rememberGlassBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@SdkSuppress(minSdkVersion = 33)
class MiniPlayerGlassUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun straightEdgesBlendIntoTheBackdropWhileTheFaceStillFollowsIt() {
        val blue = Color(0xFF536B83)
        val green = Color(0xFF507B60)
        val backdropColor = mutableStateOf(blue)
        compose.setContent {
            GlassScene(null) {
                val foreground = rememberGlassBackdrop()
                Box(foreground.capture(Modifier.fillMaxSize()).background(backdropColor.value))
                Box(Modifier.align(Alignment.Center).size(260.dp, 60.dp).testTag("mini-player-glass")
                    .glassPanel(material = GlassMaterial.MiniPlayer, foregroundBackdrop = foreground))
            }
        }
        fun readPanel() = compose.onNodeWithTag("mini-player-glass").captureToImage().toPixelMap()
        fun difference(observed: Color, expected: Color): Float {
            val color = observed.convert(ColorSpaces.Srgb)
            return maxOf(abs(color.red - expected.red), abs(color.green - expected.green), abs(color.blue - expected.blue))
        }

        val first = readPanel()
        val x = first.width / 2
        val nearEdge = (first.height * .04f).toInt().coerceAtLeast(1)
        assertTrue("Top edge should blend into the backdrop", difference(first[x, nearEdge], blue) < .09f)
        assertTrue("Bottom edge should blend into the backdrop", difference(first[x, first.height - 1 - nearEdge], blue) < .09f)
        assertTrue("The center should retain the glass tint", difference(first[x, first.height / 2], blue) > .12f)

        compose.runOnIdle { backdropColor.value = green }
        val second = readPanel()
        assertTrue("The face should sample the updated backdrop", difference(second[x, second.height / 2], first[x, first.height / 2]) > .07f)
        assertTrue("The updated backdrop should also reach the edge", difference(second[x, nearEdge], green) < .09f)
    }
}
