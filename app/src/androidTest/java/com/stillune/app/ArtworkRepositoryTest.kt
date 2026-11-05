package com.stillune.app

import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.data.PlayerAssetsRepository
import com.stillune.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArtworkRepositoryTest {
    @Test fun embeddedCoverIsDecodedCachedAndInvalidatedWhenFileChanges() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repo = PlayerAssetsRepository(context, Dispatchers.IO)
        val file = File(context.cacheDir, "cover-test.mp3")
        try {
            instrumentation.context.assets.open("audio/moon-cover.mp3").use { input -> file.outputStream().use { input.copyTo(it) } }
            val track = Track(file.toURI().toString(), "测试封面", fileName = file.name, source = "test")
            val cover = repo.artwork(track)
            assertNotNull(cover)
            assertEquals(256, cover!!.width)
            assertSame("Queue, library and mini player reuse a decoded bitmap", cover, repo.artwork(track))
            assertNotSame("Changed file metadata invalidates the cover", cover, repo.artwork(track.copy(modifiedAt = 1)))
            instrumentation.context.assets.open("audio/moon.mp3").use { input -> file.outputStream().use { input.copyTo(it) } }
            assertNull(repo.artwork(track.copy(modifiedAt = 2)))
        } finally { file.delete() }
    }
}
