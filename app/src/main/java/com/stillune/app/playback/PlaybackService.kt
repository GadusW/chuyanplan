package com.stillune.app.playback

import android.app.*
import android.annotation.SuppressLint
import android.content.*
import android.media.*
import android.net.Uri
import android.os.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.stillune.app.MainActivity
import com.stillune.app.R
import com.stillune.app.data.Track
import com.stillune.app.data.PlayerAssetsRepository
import com.stillune.app.data.PlaybackMemory
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

@AndroidEntryPoint
class PlaybackService : Service() {
    @Inject lateinit var store: PlaybackStore
    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher
    @Inject lateinit var assets: PlayerAssetsRepository
    @Inject lateinit var memory: PlaybackMemory
    inner class LocalBinder : Binder() { val service get() = this@PlaybackService }
    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var vlc: LibVLC
    private lateinit var player: MediaPlayer
    private lateinit var session: MediaSessionCompat
    private lateinit var audio: AudioManager
    private var visualSessionId = 0
    private lateinit var focusRequest: AudioFocusRequest
    private lateinit var wakeLock: PowerManager.WakeLock
    private var descriptor: ParcelFileDescriptor? = null
    private var loadJob: Job? = null
    private var restoreJob: Job? = null
    private var pendingSeekMs = 0L
    private var lastCheckpointAt = 0L
    private var artworkJob: Job? = null
    private var notificationArtwork: Bitmap? = null
    private var metadataDuration = -1L
    private val fallbackArtwork by lazy { BitmapFactory.decodeResource(resources, R.drawable.stillune_foreground) }
    private var resumeOnFocus = false
    private var foreground = false
    private var hasFocus = false
    private val shuffleVisited = mutableSetOf<Int>()
    private val s get() = store.state.value
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { resumeOnFocus = false; pause() }
    }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateOutput()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateOutput()
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        visualSessionId = audio.generateAudioSessionId().coerceAtLeast(0)
        vlc = LibVLC(this, arrayListOf("--no-video", "--no-audio-time-stretch").apply {
            if (visualSessionId > 0) add("--audiotrack-session-id=$visualSessionId")
        })
        player = MediaPlayer(vlc)
        player.setAudioOutput("audiotrack")
        audio = getSystemService(AudioManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Stillune:playback")
        wakeLock.setReferenceCounted(false)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> { hasFocus = false; resumeOnFocus = false; pause() }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        resumeOnFocus = s.playing; hasFocus = false; pause(abandonFocus = false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.setVolume(25)
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true; player.setVolume(100)
                        if (resumeOnFocus) { resumeOnFocus = false; resume() }
                    }
                }
            }, Handler(Looper.getMainLooper())).build()
        session = MediaSessionCompat(this, "Stillune").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onSkipToNext() = next()
                override fun onSkipToPrevious() = previous()
                override fun onSeekTo(pos: Long) = seek(pos)
                override fun onStop() = stopPlayback()
            })
            isActive = true
            setSessionActivity(playerIntent())
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "音乐播放", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        audio.registerAudioDeviceCallback(devices, Handler(Looper.getMainLooper()))
        updateOutput()
        scope.launch {
            while (isActive) {
                if (s.playing) {
                    if (!wakeLock.isHeld) wakeLock.acquire(30 * 60 * 1000L)
                    store.mutable.update { it.copy(positionMs = player.time.coerceAtLeast(0), durationMs = player.length.coerceAtLeast(it.track?.durationMs ?: 0)) }
                    updateSession()
                    if (SystemClock.elapsedRealtime() - lastCheckpointAt >= 3000) {
                        lastCheckpointAt = SystemClock.elapsedRealtime()
                        memory.checkpoint(s)
                    }
                }
                delay(500)
            }
        }
        restoreJob = scope.launch {
            val previous = memory.restore()
            if (previous != null && s.track == null) {
                store.mutable.value = previous.copy(output=s.output)
                shuffleVisited.clear(); shuffleVisited.add(previous.index)
                notificationArtwork = fallbackArtwork
                updateMetadata(); updateSession()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "pause" -> pause()
            "play" -> resume()
            "next" -> next()
            "previous" -> previous()
            "stop" -> stopPlayback()
        }
        return START_NOT_STICKY
    }

    fun playQueue(queue: List<Track>, index: Int, startPositionMs: Long = 0) {
        if (queue.isEmpty() || index !in queue.indices) return
        restoreJob?.cancel(); restoreJob = null
        resumeOnFocus = false
        shuffleVisited.clear(); shuffleVisited.add(index)
        store.mutable.update { it.copy(queue = queue.toList(), index = index, error = null) }
        loadCurrent(startPositionMs)
    }

    private fun enterForeground(): Boolean {
        return try {
            ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))
            startForeground(NOTIFICATION_ID, notification())
            foreground = true
            true
        } catch (_: Exception) {
            store.mutable.update { it.copy(playing = false, loading = false, error = "暂时无法启动后台播放，请回到应用重试。") }
            false
        }
    }

    private fun requestFocus(): Boolean {
        if (!hasFocus) hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) {
            store.mutable.update { it.copy(loading = false, playing = false, error = "其他应用正在使用音频，请稍后重试。") }
            leaveForeground()
        }
        return hasFocus
    }

    @SuppressLint("Recycle") // Active decoder owns the descriptor until next/stop/error/onDestroy; pending opens close in finally.
    private fun loadCurrent(startPositionMs: Long = 0) {
        val track = s.track ?: return
        loadJob?.cancel()
        player.setEventListener(null)
        player.stop()
        descriptor?.close(); descriptor = null
        pendingSeekMs = startPositionMs.coerceAtLeast(0).let { if (track.durationMs > 0 && it >= track.durationMs - 100) 0 else it }
        store.mutable.update { it.copy(loading = true, playing = false, positionMs = pendingSeekMs, durationMs = track.durationMs,
            error = null, audioSessionId = visualSessionId) }
        // Publish a complete session before the first MediaStyle notification. System UI can
        // inspect this synchronously while creating the media card.
        artworkJob?.cancel()
        notificationArtwork = fallbackArtwork
        updateMetadata()
        updateSession()
        if (!enterForeground() || !requestFocus()) return
        artworkJob = scope.launch {
            val art = assets.artwork(track)
            ensureActive()
            if (s.track?.uri == track.uri) {
                notificationArtwork = art ?: fallbackArtwork
                updateMetadata()
                notifyPlayback()
            }
        }
        loadJob = scope.launch {
            var pendingDescriptor: ParcelFileDescriptor? = null
            try {
                withContext(io) { pendingDescriptor = contentResolver.openFileDescriptor(Uri.parse(track.uri), "r") }
                val fd = pendingDescriptor ?: error("无法读取音频")
                descriptor = fd
                pendingDescriptor = null
                val media = Media(vlc, fd.fileDescriptor)
                media.addOption(":no-video")
                if (pendingSeekMs > 0) media.addOption(":start-time=${pendingSeekMs / 1000.0}")
                if (visualSessionId > 0) media.addOption(":audiotrack-session-id=$visualSessionId")
                player.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            if (pendingSeekMs > 0) { player.time = pendingSeekMs; pendingSeekMs = 0 }
                            store.mutable.update { it.copy(playing = true, loading = false, error = null) }
                            memory.checkpoint(s, visit = true)
                            if (!wakeLock.isHeld) wakeLock.acquire(30 * 60 * 1000L)
                            updateSession(); notifyPlayback()
                        }
                        MediaPlayer.Event.Paused -> {
                            store.mutable.update { it.copy(playing = false, loading = false) }
                            releaseWakeLock(); updateSession(); notifyPlayback()
                        }
                        MediaPlayer.Event.EndReached -> { releaseWakeLock(); next(automatic = true) }
                        MediaPlayer.Event.EncounteredError -> fail("无法解码或读取「${track.fileName}」，请检查文件与授权。")
                    }
                }
                player.media = media
                media.release()
                player.play()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail("文件不可用或授权已失效，请重新导入「${track.fileName}」。") }
            finally { pendingDescriptor?.close() }
        }
    }

    fun toggle() { if (s.playing || s.loading) pause() else resume() }
    fun resume() {
        if (s.track == null) return
        if (descriptor == null || s.error != null) { loadCurrent(s.positionMs); return }
        if (enterForeground() && requestFocus()) {
            if (s.durationMs > 0 && s.positionMs >= s.durationMs - 100) player.time = 0
            player.play()
        }
    }
    fun pause(abandonFocus: Boolean = true) {
        loadJob?.cancel(); loadJob = null
        if (::player.isInitialized) player.pause()
        store.mutable.update { it.copy(playing = false, loading = false) }
        releaseWakeLock()
        if (abandonFocus) { audio.abandonAudioFocusRequest(focusRequest); hasFocus = false; resumeOnFocus = false }
        updateSession(); leaveForeground(); notifyPlayback()
        memory.checkpoint(s)
    }
    fun seek(positionMs: Long) {
        if (s.durationMs <= 0) return
        val pos = positionMs.coerceIn(0, s.durationMs)
        if (descriptor != null) player.time = pos
        store.mutable.update { it.copy(positionMs = pos) }; updateSession()
        memory.checkpoint(s)
    }
    fun next(automatic: Boolean = false) {
        val next = if (s.shuffle && s.queue.size > 1 && !(automatic && s.repeat == RepeatMode.ONE)) {
            var remaining = s.queue.indices.filter { it !in shuffleVisited }
            if (remaining.isEmpty() && s.repeat != RepeatMode.OFF) {
                shuffleVisited.clear(); shuffleVisited.add(s.index)
                remaining = s.queue.indices.filter { it != s.index }
            }
            remaining.randomOrNull()
        } else QueuePolicy.next(s.index, s.queue.size, s.repeat, automatic)
        if (next == null) {
            pause(); store.mutable.update { it.copy(positionMs = it.durationMs) }
        } else { shuffleVisited.add(next); store.mutable.update { it.copy(index = next) }; loadCurrent() }
    }
    fun previous() {
        if (s.positionMs > 3000) seek(0)
        else if (s.queue.isNotEmpty()) { store.mutable.update { it.copy(index = (it.index - 1 + it.queue.size) % it.queue.size) }; loadCurrent() }
    }
    fun toggleShuffle() {
        shuffleVisited.clear(); shuffleVisited.add(s.index)
        store.mutable.update { it.copy(shuffle = !it.shuffle) }
        memory.checkpoint(s)
    }
    fun cycleRepeat() {
        store.mutable.update { it.copy(repeat = RepeatMode.entries[(it.repeat.ordinal + 1) % RepeatMode.entries.size]) }
        memory.checkpoint(s)
    }
    fun clearError() { store.mutable.update { it.copy(error = null) } }

    private fun updateOutput() {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val label = when {
            outputs.any { it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER) } -> "蓝牙设备 · 跟随系统"
            outputs.any { it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE) } -> "耳机 / USB · 跟随系统"
            else -> "手机扬声器 · 跟随系统"
        }
        store.mutable.update { it.copy(output = label) }
    }
    private fun updateSession() {
        if (!::session.isInitialized) return
        if (metadataDuration != s.durationMs && s.track != null) updateMetadata()
        val state = when { s.error != null -> PlaybackStateCompat.STATE_ERROR; s.loading -> PlaybackStateCompat.STATE_BUFFERING
            s.playing -> PlaybackStateCompat.STATE_PLAYING; else -> PlaybackStateCompat.STATE_PAUSED }
        session.setPlaybackState(PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_STOP)
            .setState(state, s.positionMs, if (s.playing) 1f else 0f, SystemClock.elapsedRealtime()).build())
    }
    private fun updateMetadata() {
        val track = s.track ?: return
        metadataDuration = s.durationMs
        session.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, track.uri)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, s.durationMs)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, notificationArtwork)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, notificationArtwork).build())
    }
    private fun playerIntent() = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).setAction(MainActivity.OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun action(name: String) = PendingIntent.getService(this, name.hashCode(),
        Intent(this, PlaybackService::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle(s.track?.title ?: "栖声 · Stillune")
        .setContentText(s.track?.let { "${it.artist} · ${it.album}" } ?: "让声音停泊")
        .setLargeIcon(notificationArtwork).setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setContentIntent(playerIntent()).setShowWhen(false)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setSilent(true)
        .setOngoing(s.playing || s.loading).setDeleteIntent(action("stop"))
        .addAction(android.R.drawable.ic_media_previous, "上一首", action("previous"))
        .addAction(if (s.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (s.playing) "暂停" else "播放", action(if (s.playing) "pause" else "play"))
        .addAction(android.R.drawable.ic_media_next, "下一首", action("next"))
        .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        .build()
    private fun notifyPlayback() { if (s.track != null) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    private fun leaveForeground() { if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false } }
    private fun releaseWakeLock() { if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release() }
    private fun fail(message: String) {
        player.stop(); descriptor?.close(); descriptor = null
        store.mutable.update { it.copy(playing = false, loading = false, error = message) }
        releaseWakeLock(); audio.abandonAudioFocusRequest(focusRequest); hasFocus = false
        updateSession(); leaveForeground(); notifyPlayback()
    }
    private fun stopPlayback() {
        artworkJob?.cancel(); artworkJob = null
        pause(); player.stop(); descriptor?.close(); descriptor = null
        store.mutable.update { PlaybackState(output = it.output) }
        session.setMetadata(null)
        session.setPlaybackState(PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_STOPPED, 0, 0f).build())
        stopForeground(STOP_FOREGROUND_REMOVE); getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }
    override fun onDestroy() {
        memory.checkpoint(s)
        scope.cancel(); player.setEventListener(null); player.release(); vlc.release()
        descriptor?.close(); releaseWakeLock(); audio.abandonAudioFocusRequest(focusRequest)
        audio.unregisterAudioDeviceCallback(devices); unregisterReceiver(noisy); session.release()
        store.mutable.update { PlaybackState(output = it.output) }
        super.onDestroy()
    }
    companion object { private const val CHANNEL = "stillune_playback"; private const val NOTIFICATION_ID = 7 }
}
