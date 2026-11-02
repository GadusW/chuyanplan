package com.stillune.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.data.*
import com.stillune.app.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PlaybackMemoryTest {
    @Test fun queuePositionModesAndRecentHistorySurviveDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "playback-memory-test.db"
        context.deleteDatabase(name)
        val tracks = listOf("one", "two").map { Track("content://test/$it",it,fileName="$it.flac",source="test",durationMs=10000) }
        var db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        var memory = PlaybackMemory(db,Dispatchers.IO)
        val snapshot = PlaybackState(queue=tracks,index=1,positionMs=2345,durationMs=10000,playing=true,shuffle=true,repeat=RepeatMode.ONE)
        try {
            memory.checkpoint(snapshot,visit=true)
            memory.flush()
            val firstVisit = memory.history.first().single()
            memory.checkpoint(snapshot.copy(positionMs=3456,playing=false))
            memory.flush()
            assertEquals(firstVisit.playedAt,memory.history.first().single().playedAt)
            assertEquals(3456L,memory.history.first().single().positionMs)
            memory.close(); db.close()
            db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
            memory = PlaybackMemory(db,Dispatchers.IO)
            val restored = requireNotNull(memory.restore())
            assertEquals(tracks,restored.queue)
            assertEquals(1,restored.index)
            assertEquals(3456L,restored.positionMs)
            assertTrue(restored.shuffle)
            assertEquals(RepeatMode.ONE,restored.repeat)
            assertFalse("Opening the app must not autoplay",restored.playing)
            assertEquals(tracks[1],memory.history.first().single().track)
        } finally { memory.close(); db.close(); context.deleteDatabase(name) }
    }
}
