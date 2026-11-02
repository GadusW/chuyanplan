package com.stillune.app.analysis

import androidx.room.*
import com.stillune.app.data.Track
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "audio_analysis", foreignKeys = [ForeignKey(entity = Track::class,
    parentColumns = ["uri"], childColumns = ["uri"], onDelete = ForeignKey.CASCADE)])
data class AnalysisCache(
    @PrimaryKey val uri: String,
    val signature: String,
    val state: String = "pending",
    val durationMs: Long = 0,
    val sampleStartMs: Long = 0,
    val sampleDurationMs: Long = 0,
    val sourceChannels: Int = 0,
    val sourceRate: Int = 0,
    val drEstimate: Double? = null,
    val correlation: Double? = null,
    val width: Double? = null,
    val balanceDb: Double? = null,
    val spectrum: ByteArray = byteArrayOf(),
    val scatter: ByteArray = byteArrayOf(),
    val analyzedAt: Long = 0,
    val error: String? = null,
)

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM audio_analysis WHERE uri = :uri") fun observe(uri: String): Flow<AnalysisCache?>
    @Query("SELECT * FROM audio_analysis WHERE uri = :uri") suspend fun get(uri: String): AnalysisCache?
    @Upsert suspend fun put(cache: AnalysisCache)
    @Query("SELECT COUNT(*) FROM audio_analysis WHERE state = 'ready'") fun readyCount(): Flow<Int>
    @Query("UPDATE audio_analysis SET state = 'pending', error = NULL WHERE uri = :uri") suspend fun reset(uri: String)
}
