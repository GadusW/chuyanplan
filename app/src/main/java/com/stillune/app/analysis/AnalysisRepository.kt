package com.stillune.app.analysis

import android.content.Context
import android.net.Uri
import android.system.Os
import androidx.room.withTransaction
import com.stillune.app.data.*
import com.stillune.app.di.IoDispatcher
import com.stillune.app.playback.PlaybackStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AnalysisRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LibraryDatabase,
    private val decoder: NativePcmAnalyzer,
    private val playback: PlaybackStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val lock = Mutex()
    fun observe(uri: String) = db.analysis().observe(uri)
    val readyCount = db.analysis().readyCount()
    private fun busy() = playback.state.value.let { it.playing || it.loading }
    suspend fun reset(uri: String) = db.analysis().reset(uri)

    suspend fun runBatch(priority: String? = null, maxTracks: Int = Int.MAX_VALUE): Boolean = withContext(io) {
        lock.withLock {
            var processed = 0
            val tracks = db.tracks().observe().first().sortedBy { if (it.uri == priority) 0 else 1 }
            for (track in tracks) {
                currentCoroutineContext().ensureActive()
                if (busy()) return@withLock false
                val signature = signature(track)
                val old = db.analysis().get(track.uri)
                if (old?.signature == signature && old.state in setOf("ready", "failed")) continue
                if (processed >= maxTracks) return@withLock true // Remaining work is reserved for the charging-idle job.
                val pending = AnalysisCache(track.uri, signature)
                if (!putIfPresent(pending.copy(state = "analyzing"))) continue
                processed++
                try {
                    val decoded = coroutineScope {
                        val calculation = async { decoder.analyze(track) }
                        val guard = launch {
                            playback.state.first { it.playing || it.loading }
                            calculation.cancel(CancellationException("让出播放资源"))
                        }
                        try { calculation.await() } finally { guard.cancel() }
                    }
                    currentCoroutineContext().ensureActive()
                    if (signature(track) != signature) {
                        putIfPresent(pending.copy(error = "文件已改变，等待重新分析"))
                        continue
                    }
                    val m = decoded.metrics
                    db.withTransaction {
                        if (db.tracks().get(track.uri) != null) db.analysis().put(pending.copy(state = "ready",
                            durationMs = m.durationMs, sampleStartMs = m.sampleStartMs, sampleDurationMs = m.sampleDurationMs,
                            sourceChannels = decoded.channels, sourceRate = decoded.rate,
                            drEstimate = m.drEstimate, correlation = m.correlation, width = m.width, balanceDb = m.balanceDb,
                            spectrum = m.spectrum, scatter = m.scatter, analyzedAt = System.currentTimeMillis()))
                    }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException && currentCoroutineContext().isActive) {
                        putIfPresent(pending.copy(state = "failed", error = "离线分析超时，可稍后手动重试。播放不受影响。"))
                        continue
                    }
                    withContext(NonCancellable) {
                        putIfPresent(pending)
                    }
                    if (!currentCoroutineContext().isActive) throw e
                    return@withLock false
                } catch (e: Exception) {
                    putIfPresent(pending.copy(state = "failed",
                        error = e.message?.take(140) ?: "分析暂不可用，播放不受影响。"))
                }
            }
            true
        }
    }

    private suspend fun putIfPresent(cache: AnalysisCache): Boolean = db.withTransaction {
        if (db.tracks().get(cache.uri) == null) false else { db.analysis().put(cache); true }
    }

    private fun signature(track: Track): String {
        val stat = try { context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { Os.fstat(it.fileDescriptor) } }
            catch (_: Exception) { null }
        return "pcm16-stereo-44100_fft2048-dr3s-v1:${track.sizeBytes}:${track.durationMs}:${track.modifiedAt}:${stat?.st_size}:${stat?.st_mtime}"
    }
}
