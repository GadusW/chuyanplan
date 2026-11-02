package com.stillune.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.analysis.*
import com.stillune.app.data.*
import com.stillune.app.playback.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnalysisCacheTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun versionTwoUpgradeKeepsExistingAnalysisCache() = runBlocking {
        val name = "history-migration-test.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.stillune.app.data.LibraryDatabase/2.json").bufferedReader().use { JSONObject(it.readText()) }
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { old ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO tracks VALUES ('cached','Cached Song','Artist','Album',6000,100,'a.flac','Music/','media','FLAC',44100,16,'primary:Music/a.flac',0)")
            old.execSQL("INSERT INTO audio_analysis (uri,signature,state,durationMs,sampleStartMs,sampleDurationMs,sourceChannels,sourceRate,drEstimate,spectrum,scatter,analyzedAt) VALUES ('cached','same','ready',6000,2000,4000,2,44100,9.5,X'0102',X'0304',123)")
            old.version = 2
        }
        val db = Room.databaseBuilder(context,LibraryDatabase::class.java,name).addMigrations(LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Cached Song",db.tracks().get("cached")?.title)
            val cache = requireNotNull(db.analysis().get("cached"))
            assertEquals("ready",cache.state)
            assertEquals(9.5,cache.drEstimate!!,0.0)
            assertArrayEquals(byteArrayOf(1,2),cache.spectrum)
            assertNull(db.memory().resume())
            assertTrue(db.memory().history().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun schemaUpgradePreservesExistingLibrary() = runBlocking {
        val name = "analysis-migration-test.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.stillune.app.data.LibraryDatabase/1.json").bufferedReader().use { it.readText() }
        val sql = JSONObject(schema).getJSONObject("database").getJSONArray("entities").getJSONObject(0)
            .getString("createSql").replace("\${TABLE_NAME}", "tracks")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL(sql)
            old.execSQL("INSERT INTO tracks VALUES ('existing','Old Song','Artist','Album',6000,100,'a.flac','Music/','media','FLAC',44100,16,'primary:Music/a.flac')")
            old.version = 1
        }
        val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_1_2,LibraryDatabase.MIGRATION_2_3,LibraryDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Old Song", db.tracks().observe().first().single().title)
            assertEquals(0L, db.tracks().observe().first().single().modifiedAt)
            assertNull(db.analysis().get("existing"))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun cacheIsReusedInvalidatedAndAnalysisYieldsToPlayback() = runBlocking {
        val file = File(context.cacheDir,"analysis-cache-test.flac")
        InstrumentationRegistry.getInstrumentation().context.assets.open("audio/moon.flac").use { src -> file.outputStream().use { src.copyTo(it) } }
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val store = PlaybackStore()
        val repository = AnalysisRepository(context, db, NativePcmAnalyzer(context, Dispatchers.IO), store, Dispatchers.IO)
        val track = Track(Uri.fromFile(file).toString(), "Cached", fileName="cached.flac",source="test",durationMs=6000,sizeBytes=file.length())
        try {
            db.tracks().upsert(listOf(track))
            val interrupt = launch { delay(80); store.mutable.value = PlaybackState(playing = true) }
            assertFalse(repository.runBatch())
            interrupt.join()
            assertEquals("pending", db.analysis().get(track.uri)?.state)
            store.mutable.value = PlaybackState()
            assertTrue(repository.runBatch())
            val first = db.analysis().get(track.uri)!!
            assertEquals("ready",first.state)
            assertTrue(repository.runBatch())
            assertEquals(first.analyzedAt, db.analysis().get(track.uri)!!.analyzedAt)
            db.analysis().put(first.copy(signature="old-algorithm"))
            assertTrue(repository.runBatch())
            assertNotEquals("old-algorithm", db.analysis().get(track.uri)!!.signature)
            assertTrue(db.analysis().get(track.uri)!!.analyzedAt > first.analyzedAt)
            db.tracks().remove(track.uri)
            assertNull(db.analysis().get(track.uri))
        } finally { db.close(); file.delete() }
    }
}
