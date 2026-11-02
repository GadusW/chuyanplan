package com.stillune.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.stillune.app.analysis.AnalysisCache
import com.stillune.app.analysis.AnalysisDao
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observe(): Flow<List<Track>>
    @Upsert suspend fun upsert(tracks: List<Track>)
    @Query("SELECT * FROM tracks WHERE source = :source") suspend fun fromSource(source: String): List<Track>
    @Query("DELETE FROM tracks WHERE uri = :uri") suspend fun remove(uri: String)
    @Query("SELECT * FROM tracks WHERE uri = :uri") suspend fun get(uri: String): Track?
}

@Database(entities = [Track::class, AnalysisCache::class, ResumeRecord::class, HistoryRecord::class], version = 3, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun analysis(): AnalysisDao
    abstract fun memory(): PlaybackMemoryDao
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS playback_resume (id INTEGER NOT NULL PRIMARY KEY, queueJson TEXT NOT NULL, trackIndex INTEGER NOT NULL, positionMs INTEGER NOT NULL, shuffle INTEGER NOT NULL, repeatMode TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS listening_history (uri TEXT NOT NULL PRIMARY KEY, trackJson TEXT NOT NULL, positionMs INTEGER NOT NULL, playedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""CREATE TABLE IF NOT EXISTS audio_analysis (
                    uri TEXT NOT NULL PRIMARY KEY, signature TEXT NOT NULL, state TEXT NOT NULL,
                    durationMs INTEGER NOT NULL, sampleStartMs INTEGER NOT NULL, sampleDurationMs INTEGER NOT NULL,
                    sourceChannels INTEGER NOT NULL, sourceRate INTEGER NOT NULL,
                    drEstimate REAL, correlation REAL, width REAL, balanceDb REAL,
                    spectrum BLOB NOT NULL, scatter BLOB NOT NULL, analyzedAt INTEGER NOT NULL, error TEXT,
                    FOREIGN KEY(uri) REFERENCES tracks(uri) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            }
        }
    }
}
