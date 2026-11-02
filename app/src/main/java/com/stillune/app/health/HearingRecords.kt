package com.stillune.app.health

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hearing_minutes")
data class HearingMinute(
    @PrimaryKey val startMs: Long,
    val listenedMs: Long = 0,
    val headphoneMs: Long = 0,
    val abovePreferenceMs: Long = 0,
)

@Dao
abstract class HearingDao {
    @Query("SELECT * FROM hearing_minutes ORDER BY startMs DESC")
    abstract fun observe(): Flow<List<HearingMinute>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(row: HearingMinute)
    @Query("UPDATE hearing_minutes SET listenedMs=listenedMs+:duration, headphoneMs=headphoneMs+:headphone, abovePreferenceMs=abovePreferenceMs+:above WHERE startMs=:start")
    protected abstract suspend fun increment(start: Long, duration: Long, headphone: Long, above: Long)
    @Transaction
    open suspend fun add(row: HearingMinute) {
        insert(HearingMinute(row.startMs))
        increment(row.startMs, row.listenedMs, row.headphoneMs, row.abovePreferenceMs)
    }
    @Query("DELETE FROM hearing_minutes WHERE startMs < :before") abstract suspend fun prune(before: Long)
    @Query("DELETE FROM hearing_minutes") abstract suspend fun clear()
}
