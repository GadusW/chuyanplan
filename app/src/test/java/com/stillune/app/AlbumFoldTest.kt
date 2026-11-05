package com.stillune.app

import androidx.compose.ui.geometry.Offset
import com.stillune.app.ui.AlbumFold
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AlbumFoldTest {
    private fun area(points: List<Offset>) = abs(points.indices.sumOf { i ->
        val a = points[i]; val b = points[(i + 1) % points.size]
        (a.x * b.y - b.x * a.y).toDouble()
    }) / 2

    @Test fun foldedCornerReflectsToFingerAndPreservesPaperArea() {
        listOf(Offset(-24f, -28f), Offset(-180f, -200f), Offset(-280f, -80f), Offset(-60f, -270f), Offset(-420f, -450f)).forEach { drag ->
            val fold = AlbumFold.create(300f, 300f, drag)
            assertEquals(90000.0, area(fold.front) + area(fold.flap), .1)
            assertEquals(2, fold.crease.size)
            assertTrue("Reflected corner follows the finger", fold.flap.any { (it - fold.tip).getDistance() < .01f })
            assertTrue("Top-left stays fixed", fold.front.contains(Offset.Zero))
        }
    }

    @Test fun shortDragOnlyExposesSmallCorner() {
        val short = AlbumFold.create(300f, 300f, Offset(-20f, -20f))
        val long = AlbumFold.create(300f, 300f, Offset(-220f, -220f))
        assertTrue(area(short.front) > 89500)
        assertTrue(area(long.flap) > area(short.flap) * 20)
    }
}
