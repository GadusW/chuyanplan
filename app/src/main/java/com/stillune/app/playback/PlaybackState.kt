package com.stillune.app.playback

import com.stillune.app.data.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode { ALL, ONE, OFF }
data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.ALL,
    val output: String = "系统音频输出",
    val error: String? = null,
    val audioSessionId: Int = 0,
) { val track: Track? get() = queue.getOrNull(index) }

@Singleton
class PlaybackStore @Inject constructor() {
    internal val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
}

object QueuePolicy {
    fun next(index: Int, count: Int, repeat: RepeatMode, automatic: Boolean): Int? = when {
        count == 0 -> null
        automatic && repeat == RepeatMode.ONE -> index.coerceIn(0, count - 1)
        index + 1 < count -> index + 1
        repeat != RepeatMode.OFF -> 0
        else -> null
    }
}
