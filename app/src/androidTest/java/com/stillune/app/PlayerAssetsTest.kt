package com.stillune.app

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.stillune.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion = 29)
class PlayerAssetsTest {
    @Test fun linkedLyricsAreReadAndRemainAssociatedAcrossRepositoryInstances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "stillune-test.lrc")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        try {
            context.contentResolver.openOutputStream(uri)!!.use { it.write("[00:01.00]月色与旋律\n[00:02.50]轻轻相伴".toByteArray()) }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            val track = Track("content://stillune/lyric-test", "测试", fileName = "test.flac", source = "test")
            val assets = PlayerAssetsRepository(context, Dispatchers.IO)
            assets.associateLyrics(track, uri)
            val restored = PlayerAssetsRepository(context, Dispatchers.IO).lyrics(track)
            assertTrue(restored.synced)
            assertEquals(listOf(1000L, 2500L), restored.lines.map { it.timeMs })
            assertEquals("月色与旋律", restored.lines.first().text)
        } finally { context.contentResolver.delete(uri, null, null) }
    }
}
