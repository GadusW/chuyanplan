package com.stillune.app.health

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.edit
import com.stillune.app.data.LibraryDatabase
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

@Singleton
class HearingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    db: LibraryDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val prefs = context.getSharedPreferences("hearing-care", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(HearingSettings(
        prefs.getBoolean("enabled", false), prefs.getInt("break", 40).coerceIn(20, 60),
        prefs.getInt("volume", 60).coerceIn(30, 90), prefs.getBoolean("pause", false),
        prefs.getString("headphone", "").orEmpty(), prefs.getString("isolation", "尚未填写").orEmpty(),
    ))
    val settings = _settings.asStateFlow()
    private val _live = MutableStateFlow(HearingLive(unobservedMs = prefs.getLong("unobserved", 0)))
    val live = _live.asStateFlow()
    private val _environment = MutableStateFlow(EnvironmentReading())
    val environment = _environment.asStateFlow()
    val records = db.hearing().observe()
    private val scope = CoroutineScope(SupervisorJob() + io)
    private sealed interface Write {
        data class Add(val rows: List<HearingMinute>) : Write
        data object Clear : Write
        data class Barrier(val done: CompletableDeferred<Unit>) : Write
    }
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val pending = mutableMapOf<Long, HearingMinute>()
    private val clock = ListeningClock()
    private var lastFlush = 0L
    private var pausedAt = 0L
    private var lastReminder = 0L
    private var lastVolumeReminder = restoredReminder("volumeWall")
    private var aboveMs = 0L
    private var envAboveAt = 0L
    private var lastEnvReminder = restoredReminder("environmentWall")
    private var noticeSequence = System.currentTimeMillis()
    private var continuous = if (System.currentTimeMillis() - prefs.getLong("checkpoint", 0) in 0..300_000 &&
        (prefs.getLong("pausedWall", 0) == 0L || System.currentTimeMillis() - prefs.getLong("pausedWall", 0) < 300_000))
        prefs.getLong("continuous", 0).coerceAtLeast(0) else 0L

    init {
        lastReminder = prefs.getLong("lastReminder", 0).coerceIn(0, continuous)
        scope.launch {
            for (write in writes) {
                try {
                    when (write) {
                        is Write.Add -> { write.rows.forEach { db.hearing().add(it) }; db.hearing().prune(System.currentTimeMillis() - 31L * 86_400_000) }
                        Write.Clear -> db.hearing().clear()
                        is Write.Barrier -> write.done.complete(Unit)
                    }
                    if (write !is Write.Barrier) _live.update { it.copy(storageError = false) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    _live.update { it.copy(storageError = true, unobservedMs = it.unobservedMs +
                        if (write is Write.Add) write.rows.sumOf { row -> row.listenedMs } else 0) }
                    if (write is Write.Barrier) write.done.completeExceptionally(IllegalStateException("记录未能保存"))
                }
            }
        }
    }

    /** Called on Main by the playback service, including pause/start transitions. */
    fun sample(playing: Boolean, audio: AudioManager, headphones: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val step = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        recordSample(now, wall, playing, step, maximum, audio.isStreamMute(AudioManager.STREAM_MUSIC), headphones)
    }

    internal fun recordSample(now: Long, wall: Long, playing: Boolean, step: Int, maximum: Int, muted: Boolean, headphones: Boolean) {
        val active = settings.value.enabled && playing && step > 0 && !muted
        val above = step * 100 > maximum * settings.value.volumePercent
        val result = clock.advance(ListeningClock.Sample(now, wall, active, headphones, above))
        result.slices.forEach { slice ->
            val old = pending[slice.minute] ?: HearingMinute(slice.minute)
            pending[slice.minute] = old.copy(listenedMs = old.listenedMs + slice.duration,
                headphoneMs = old.headphoneMs + if (slice.headphones) slice.duration else 0,
                abovePreferenceMs = old.abovePreferenceMs + if (slice.above) slice.duration else 0)
            continuous += slice.duration
            if (slice.above) aboveMs += slice.duration else aboveMs = 0
        }
        if (pausedAt > 0 && now - pausedAt >= 300_000) { continuous = 0; lastReminder = 0 }
        if (!active) {
            if (pausedAt == 0L) pausedAt = now
            if (now - pausedAt >= 300_000) { continuous = 0; lastReminder = 0 }
            aboveMs = 0
        } else pausedAt = 0
        if (active && continuous - lastReminder >= settings.value.breakMinutes * 60_000L) {
            lastReminder = continuous
            showNotice(HearingNoticeKind.BREAK, "让双耳歇一歇", "这段聆听已累计 ${continuous / 60_000} 分钟。可以暂停片刻，读一段音乐小札。")
        } else if (active && aboveMs >= 10_000 && now - lastVolumeReminder >= 600_000) {
            lastVolumeReminder = now; aboveMs = 0
            prefs.edit { putLong("volumeWall", wall) }
            showNotice(HearingNoticeKind.VOLUME, "音量超过了你的提醒档位", "当前系统档位高于你设置的 ${settings.value.volumePercent}%。可以调低一些；百分比不是入耳分贝。")
        }
        _live.update { it.copy(continuousMs = continuous, volumeStep = step, volumeMax = maximum,
            headphonesConnected = headphones, unobservedMs = it.unobservedMs + result.missingMs) }
        if ((!active && pending.isNotEmpty()) || (settings.value.enabled && now - lastFlush >= 5_000)) flushPending(wall, now)
    }

    fun update(transform: (HearingSettings) -> HearingSettings) {
        val next = transform(settings.value).let { it.copy(breakMinutes = it.breakMinutes.coerceIn(20, 60), volumePercent = it.volumePercent.coerceIn(30, 90), headphoneName = it.headphoneName.take(80)) }
        _settings.value = next
        prefs.edit {
            putBoolean("enabled", next.enabled); putInt("break", next.breakMinutes)
            putInt("volume", next.volumePercent); putBoolean("pause", next.pauseOnReminder)
            putString("headphone", next.headphoneName); putString("isolation", next.isolation)
        }
        if (!next.enabled) {
            clock.reset()
            continuous = 0; lastReminder = 0; aboveMs = 0
            _live.update { it.copy(continuousMs = 0, notice = null) }
            notifyHearing(context, null)
            flushPending()
        }
    }

    fun environmentChanged(reading: EnvironmentReading, now: Long = SystemClock.elapsedRealtime()) {
        _environment.value = reading
        if (settings.value.enabled && reading.running && (reading.changeDb ?: 0f) >= 10f) {
            if (envAboveAt == 0L) envAboveAt = now
            if (now - envAboveAt >= 10_000 && now - lastEnvReminder >= 600_000) {
                lastEnvReminder = now
                prefs.edit { putLong("environmentWall", System.currentTimeMillis()) }
                showNotice(HearingNoticeKind.ENVIRONMENT, "周围的声音变强了", "相对本次起始基线，麦克风输入持续上升。留意是否正在提高耳机音量，也可以换个安静的地方。")
            }
        } else envAboveAt = 0
    }

    private fun showNotice(kind: HearingNoticeKind, title: String, message: String) {
        // Keep an unread rest reminder rather than replacing it with a less useful prompt.
        if (live.value.notice?.kind == HearingNoticeKind.BREAK) return
        _live.update { it.copy(notice = HearingNotice(++noticeSequence, kind, title, message)) }
        notifyHearing(context, live.value.notice)
    }
    fun dismissNotice() { _live.update { it.copy(notice = null) }; notifyHearing(context, null) }
    fun flushPending(wall: Long = System.currentTimeMillis(), elapsed: Long = SystemClock.elapsedRealtime()) {
        if (pending.isNotEmpty()) { writes.trySend(Write.Add(pending.values.toList())); pending.clear() }
        prefs.edit {
            putLong("continuous", continuous); putLong("lastReminder", lastReminder); putLong("checkpoint", wall)
            putLong("pausedWall", if (pausedAt > 0) wall - (elapsed - pausedAt) else 0)
            putLong("unobserved", live.value.unobservedMs)
        }
        lastFlush = elapsed
    }
    fun clearRecords() {
        clock.reset()
        dismissNotice()
        pending.clear(); writes.trySend(Write.Clear)
        continuous = 0; lastReminder = 0; aboveMs = 0
        _live.update { it.copy(continuousMs = 0, unobservedMs = 0) }
        flushPending()
    }
    suspend fun awaitWrites() { val done = CompletableDeferred<Unit>(); writes.send(Write.Barrier(done)); done.await() }
    internal fun close() { writes.close(); scope.cancel() }
    private fun restoredReminder(key: String): Long = (SystemClock.elapsedRealtime() -
        (System.currentTimeMillis() - prefs.getLong(key, 0)).coerceAtLeast(0)).coerceAtLeast(-600_000)
}
