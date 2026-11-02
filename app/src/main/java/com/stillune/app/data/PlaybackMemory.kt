package com.stillune.app.data

import androidx.room.*
import com.stillune.app.di.IoDispatcher
import com.stillune.app.playback.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "playback_resume")
data class ResumeRecord(@PrimaryKey val id: Int = 1, val queueJson: String, val trackIndex: Int,
    val positionMs: Long, val shuffle: Boolean, val repeatMode: String)

@Entity(tableName = "listening_history")
data class HistoryRecord(@PrimaryKey val uri: String, val trackJson: String, val positionMs: Long, val playedAt: Long)
data class ListeningEntry(val track: Track, val positionMs: Long, val playedAt: Long)

@Dao
interface PlaybackMemoryDao {
    @Query("SELECT * FROM playback_resume WHERE id=1") suspend fun resume(): ResumeRecord?
    @Upsert suspend fun saveResume(record: ResumeRecord)
    @Upsert suspend fun saveVisit(record: HistoryRecord)
    @Query("UPDATE listening_history SET positionMs=:position WHERE uri=:uri") suspend fun updatePosition(uri: String, position: Long)
    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT 100") fun history(): Flow<List<HistoryRecord>>
    @Query("DELETE FROM listening_history WHERE uri NOT IN (SELECT uri FROM listening_history ORDER BY playedAt DESC LIMIT 100)") suspend fun trim()
}

internal object TrackMemoryCodec {
    fun encode(t: Track) = JSONObject().apply {
        put("uri",t.uri); put("title",t.title); put("artist",t.artist); put("album",t.album)
        put("duration",t.durationMs); put("size",t.sizeBytes); put("file",t.fileName)
        put("folder",t.folder); put("source",t.source); put("format",t.format)
        put("rate",t.sampleRate); put("depth",t.bitDepth); put("identity",t.identity); put("modified",t.modifiedAt)
    }
    fun decode(j: JSONObject) = Track(uri=j.getString("uri"), title=j.getString("title"), artist=j.optString("artist","未知艺术家"),
        album=j.optString("album","本地音乐"),durationMs=j.optLong("duration"),sizeBytes=j.optLong("size"),
        fileName=j.getString("file"),folder=j.optString("folder"),source=j.optString("source","history"),
        format=j.optString("format"),sampleRate=j.optInt("rate"),bitDepth=j.optInt("depth"),
        identity=j.optString("identity",j.getString("uri")),modifiedAt=j.optLong("modified"))
}

/** One application-lifetime writer; service destruction cannot cancel an in-flight checkpoint. */
@Singleton
class PlaybackMemory @Inject constructor(private val db: LibraryDatabase, @IoDispatcher private val io: CoroutineDispatcher) {
    private data class Write(val state: PlaybackState?, val visit: Boolean = false, val barrier: CompletableDeferred<Unit>? = null)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private var cachedQueue: List<Track>? = null
    private var cachedJson = ""
    val history: Flow<List<ListeningEntry>> = db.memory().history().map { rows ->
        rows.mapNotNull { row -> runCatching { ListeningEntry(TrackMemoryCodec.decode(JSONObject(row.trackJson)),row.positionMs,row.playedAt) }.getOrNull() }
    }.flowOn(io)
    init {
        scope.launch {
            for (write in writes) {
                try {
                    val state = write.state
                    if (state?.track != null) {
                        if (state.queue != cachedQueue) {
                            cachedJson = JSONArray().apply { state.queue.forEach { put(TrackMemoryCodec.encode(it)) } }.toString()
                            cachedQueue = state.queue
                        }
                        db.withTransaction {
                            db.memory().saveResume(ResumeRecord(queueJson=cachedJson,trackIndex=state.index,
                                positionMs=state.positionMs.coerceAtLeast(0),shuffle=state.shuffle,repeatMode=state.repeat.name))
                            if (write.visit) {
                                db.memory().saveVisit(HistoryRecord(state.track!!.uri,TrackMemoryCodec.encode(state.track!!).toString(),
                                    state.positionMs,System.currentTimeMillis()))
                                db.memory().trim()
                            } else db.memory().updatePosition(state.track!!.uri,state.positionMs)
                        }
                    }
                    write.barrier?.complete(Unit)
                } catch (failure: Exception) {
                    write.barrier?.completeExceptionally(failure)
                    android.util.Log.w("PlaybackMemory", "Playback checkpoint failed", failure)
                }
            }
        }
    }
    fun checkpoint(state: PlaybackState, visit: Boolean = false) { if (state.track != null) writes.trySend(Write(state,visit)) }
    suspend fun flush() { val barrier = CompletableDeferred<Unit>(); writes.send(Write(null,barrier=barrier)); barrier.await() }
    suspend fun restore(): PlaybackState? = withContext(io) {
        runCatching {
            val row = db.memory().resume() ?: return@withContext null
            val json = JSONArray(row.queueJson)
            val queue = (0 until json.length()).map { TrackMemoryCodec.decode(json.getJSONObject(it)) }
            if (queue.isEmpty() || row.trackIndex !in queue.indices) return@withContext null
            val duration = queue[row.trackIndex].durationMs
            PlaybackState(queue=queue,index=row.trackIndex,positionMs=if (duration > 0) row.positionMs.coerceIn(0,duration) else row.positionMs.coerceAtLeast(0),
                durationMs=duration,shuffle=row.shuffle,repeat=runCatching { RepeatMode.valueOf(row.repeatMode) }.getOrDefault(RepeatMode.ALL))
        }.getOrNull()
    }
    /** Used only by isolated repository tests; the app singleton lives for the process. */
    fun close() { writes.close(); scope.cancel() }
}
