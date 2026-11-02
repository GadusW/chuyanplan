package com.stillune.app

import com.stillune.app.data.LrcParser
import org.junit.Assert.*
import org.junit.Test

class LrcParserTest {
    @Test fun repeatedTimestampsFractionsAndOffsetAreAppliedAndSorted() {
        val lyrics = LrcParser.parse("[offset:-100]\n[ar:Stillune]\n[00:05.25][00:01.2]月色落在唱片上\n[00:02.345]让声音停泊")
        assertTrue(lyrics.synced)
        assertEquals(listOf(1100L, 2245L, 5150L), lyrics.lines.map { it.timeMs })
        assertEquals(-1, lyrics.activeIndex(500))
        assertEquals(1, lyrics.activeIndex(3000))
        assertEquals(2, lyrics.activeIndex(8000))
    }
    @Test fun plainTextRemainsReadableWithoutInventedTiming() {
        val lyrics = LrcParser.parse("\uFEFF第一行\n\n第二行")
        assertFalse(lyrics.synced)
        assertEquals(listOf("第一行", "第二行"), lyrics.lines.map { it.text })
    }
    @Test fun invalidSecondsAreIgnoredAndNegativeOffsetsClampToZero() {
        val lyrics = LrcParser.parse("[offset:-500]\n[00:00.10]开始\n[00:65.00]无效")
        assertEquals(1, lyrics.lines.size)
        assertEquals(0L, lyrics.lines.single().timeMs)
    }
}
