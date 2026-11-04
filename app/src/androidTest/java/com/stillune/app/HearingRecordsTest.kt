package com.stillune.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.data.LibraryDatabase
import com.stillune.app.health.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HearingRecordsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun isolatedContext(): Context = object : ContextWrapper(context) {
        private val unique = "hearing-test-${System.nanoTime()}"
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = super.getSharedPreferences(unique, mode)
    }

    @Test fun remindersUseActualPlayingTimeAndRespectPauseMuteMasterSwitchAndCooldown() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val repository = HearingRepository(isolatedContext(), db, Dispatchers.IO)
        val wall = System.currentTimeMillis()
        try {
            main {
                repository.update { it.copy(enabled = true, breakMinutes = 20, volumePercent = 60) }
                // Forty minutes of virtual time: first 20 playing, then 20 paused.
                for (index in 0..2400) repository.recordSample(1_000 + index * 500L, wall + index * 500L, true, 4, 15, false, true)
                assertEquals(HearingNoticeKind.BREAK, repository.live.value.notice?.kind)
                repository.dismissNotice()
                repository.recordSample(1_201_500, wall + 1_200_500, false, 4, 15, false, true)
                for (index in 1..2400) repository.recordSample(1_201_500 + index * 500L, wall + 1_200_500 + index * 500L, false, 4, 15, false, true)
                assertEquals(0L, repository.live.value.continuousMs)
                assertNull(repository.live.value.notice)
                repository.flushPending()
            }
            repository.awaitWrites()
            assertEquals(1_200_500L, repository.records.first().sumOf { it.listenedMs })
            assertEquals(1_200_500L, repository.records.first().sumOf { it.headphoneMs })
            main {
                // Muted playback does not create more listening time.
                repository.recordSample(2_402_500, wall + 2_401_500, true, 0, 15, true, true)
                repository.recordSample(2_403_000, wall + 2_402_000, true, 0, 15, true, true)
                repository.update { it.copy(enabled = false) }
                repository.recordSample(2_403_500, wall + 2_402_500, true, 12, 15, false, true)
                assertNull(repository.live.value.notice)
                repository.flushPending()
            }
            repository.awaitWrites()
            assertEquals(1_200_500L, repository.records.first().sumOf { it.listenedMs })
        } finally { repository.close(); db.close() }
    }

    @Test fun volumeAndEnvironmentPromptsRequireSustainedEvidenceAndNeverInventSpl() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val repository = HearingRepository(isolatedContext(), db, Dispatchers.IO)
        val wall = System.currentTimeMillis()
        try {
            main {
                repository.update { it.copy(enabled = true) }
                for (index in 0..19) repository.recordSample(1000 + index * 500L, wall + index * 500L, true, 12, 15, false, true)
                assertNull(repository.live.value.notice)
                repository.recordSample(11_000, wall + 10_000, true, 12, 15, false, true)
                assertEquals(HearingNoticeKind.VOLUME, repository.live.value.notice?.kind)
                repository.dismissNotice()
                for (index in 21..60) repository.recordSample(1000 + index * 500L, wall + index * 500L, true, 12, 15, false, true)
                assertNull(repository.live.value.notice)
                repository.environmentChanged(EnvironmentReading(true, 12f, "test"), 1000)
                repository.environmentChanged(EnvironmentReading(true, null, "输入不可用"), 7000)
                repository.environmentChanged(EnvironmentReading(true, 12f, "test"), 11_000)
                assertNull(repository.live.value.notice)
                repository.environmentChanged(EnvironmentReading(true, 12f, "test"), 21_000)
                assertEquals(HearingNoticeKind.ENVIRONMENT, repository.live.value.notice?.kind)
                assertFalse(repository.live.value.notice!!.message.contains("dBA"))
                repository.flushPending()
            }
            repository.awaitWrites()
        } finally { main { repository.dismissNotice() }; repository.close(); db.close() }
    }

    @Test fun durableMinuteRecordsSurviveReopenAndCanBeDeletedWithoutChangingOtherTables() = runBlocking {
        val name = "hearing-persistence-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        try {
            val row = HearingMinute(System.currentTimeMillis() / 60_000 * 60_000, 1500, 1000, 500)
            db.hearing().add(row); db.hearing().add(row.copy(listenedMs = 500, headphoneMs = 0, abovePreferenceMs = 0))
            db.close()
            db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
            assertEquals(row.copy(listenedMs = 2000), db.hearing().observe().first().single())
            db.hearing().clear()
            assertTrue(db.hearing().observe().first().isEmpty())
            assertTrue(db.tracks().observe().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun versionThreeMigrationPreservesQueueAndAddsEmptyHearingHistory() = runBlocking {
        val name = "hearing-migration-test.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.stillune.app.data.LibraryDatabase/3.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONObject("database").getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO playback_resume VALUES (1,'[]',-1,12345,0,'ALL')")
            old.version = 3
        }
        val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name).addMigrations(LibraryDatabase.MIGRATION_3_4).build()
        try {
            assertEquals(12345L, db.memory().resume()?.positionMs)
            assertTrue(db.hearing().observe().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
