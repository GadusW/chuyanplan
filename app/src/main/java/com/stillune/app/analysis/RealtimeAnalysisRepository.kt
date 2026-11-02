package com.stillune.app.analysis

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.stillune.app.data.Track
import com.stillune.app.di.IoDispatcher
import com.stillune.app.playback.PlaybackState
import com.stillune.app.playback.PlaybackStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

private class AnalysisResync : Exception()

/** Independent, silent source decoder. Never attaches to global session 0 or records the microphone. */
class RealtimePcmDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun read(track: Track, startMs: Long, playback: () -> PlaybackState,
                     onBlock: suspend (FloatArray, Int, Int) -> Unit) = withContext(io) {
        val input = context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r") ?: error("文件读取授权已失效，请重新导入。")
        input.use {
            val pipe = ParcelFileDescriptor.createPipe()
            try {
                val vlc = LibVLC(context, arrayListOf("--no-video", "--no-audio-time-stretch", "--avcodec-threads=1"))
                try {
                    val player = MediaPlayer(vlc)
                    val ended = AtomicBoolean(false); val failed = AtomicBoolean(false)
                    val poll = arrayOf(StructPollfd().apply { fd=pipe[0].fileDescriptor; events=OsConstants.POLLIN.toShort() })
                    fun read(bytes: ByteArray, offset: Int, count: Int): Int =
                        if (Os.poll(poll,20)>0 && poll[0].revents.toInt() and OsConstants.POLLIN != 0)
                            Os.read(pipe[0].fileDescriptor,bytes,offset,count) else -1
                    try {
                        var channels = 2
                        val media = Media(vlc, input.fileDescriptor)
                        try {
                            media.parse(IMedia.Parse.ParseLocal)
                            for (i in 0 until media.trackCount) (media.getTrack(i) as? IMedia.AudioTrack)?.let { channels=it.channels }
                            media.addOption(":start-time=${String.format(Locale.ROOT,"%.3f",startMs/1000.0)}")
                            media.addOption(":sout=#transcode{acodec=fl32,channels=2,samplerate=48000}:std{access=file,mux=raw,dst=/proc/self/fd/${pipe[1].fd}}")
                            media.addOption(":sout-transcode-threads=1"); media.addOption(":sout-mux-caching=0")
                            player.setEventListener {
                                if (it.type == MediaPlayer.Event.EndReached) ended.set(true)
                                if (it.type == MediaPlayer.Event.EncounteredError) failed.set(true)
                            }
                            player.media=media
                        } finally { media.release() }
                        player.play()
                        val bytes = ByteArray(RealtimeProcessor.HOP*8)
                        val pcm = FloatArray(RealtimeProcessor.HOP*2)
                        var filled = 0; var frames = 0L; var lastData = SystemClock.elapsedRealtime()
                        while (true) {
                            ensureActive()
                            val current = playback()
                            val position = startMs+frames*1000/RealtimeProcessor.RATE
                            if (!current.playing || current.loading) { delay(50); continue }
                            val estimated = current.positionMs + if (current.positionTimestampMs>0)
                                (SystemClock.elapsedRealtime()-current.positionTimestampMs).coerceIn(0,650) else 0
                            if (frames > 0 && estimated-position > 1200) throw AnalysisResync()
                            if (position > estimated+30) { delay(8); continue }
                            if (failed.get()) error("此文件暂不能建立实时 PCM 通路，播放仍可继续。")
                            val count = read(bytes,filled,bytes.size-filled)
                            if (count > 0) {
                                filled += count; lastData=SystemClock.elapsedRealtime()
                                if (filled == bytes.size) {
                                    for (i in pcm.indices) {
                                        val p=i*4
                                        val bits=(bytes[p].toInt() and 255) or ((bytes[p+1].toInt() and 255) shl 8) or
                                            ((bytes[p+2].toInt() and 255) shl 16) or (bytes[p+3].toInt() shl 24)
                                        pcm[i]=Float.fromBits(bits)
                                    }
                                    onBlock(pcm,RealtimeProcessor.HOP,channels); frames += RealtimeProcessor.HOP; filled=0
                                }
                            } else {
                                val idle=SystemClock.elapsedRealtime()-lastData
                                if (ended.get() && idle>250) break
                                if (idle>10000) error("实时解码未返回数据，可点重新测量再试。")
                                delay(5)
                            }
                        }
                    } finally {
                        player.setEventListener(null)
                        // Native stop joins its pipe writer. Drain until it returns, even on cancellation.
                        withContext(NonCancellable) {
                            try {
                                coroutineScope {
                                    val stopping=async(io) { player.stop() }
                                    val discard=ByteArray(65536)
                                    while (!stopping.isCompleted) { if (read(discard,0,discard.size)<=0) delay(4) }
                                    stopping.await()
                                }
                            } finally { player.release() }
                        }
                    }
                } finally { vlc.release() }
            } finally { pipe.forEach { it.close() } }
        }
    }
}

@Singleton
class RealtimeAnalysisRepository @Inject constructor(
    private val decoder: RealtimePcmDecoder, private val playback: PlaybackStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutableOptions = MutableStateFlow(RealtimeOptions())
    val options = mutableOptions.asStateFlow()
    fun configure(value: RealtimeOptions) { mutableOptions.value=value }
    private val reset = MutableStateFlow(0L)
    fun reset() { reset.update { it+1 } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<RealtimeFrame> = channelFlow {
        combine(playback.state.map { Triple(it.track?.uri,it.timelineRevision,it.error) }.distinctUntilChanged(),reset) { key, generation -> key to generation }
            .collectLatest {
                val latest=AtomicReference(RealtimeFrame())
                val current=playback.state.value
                val track=current.track
                if (track==null || current.error!=null) {
                    send(latest.get().copy(status=if (track==null) "先选择一首音乐" else "播放出错，实时分析已停止")); return@collectLatest
                }
                if (!current.playing) send(latest.get().copy(status="播放后开始测量"))
                playback.state.first { it.playing && !it.loading }
                while (currentCoroutineContext().isActive) {
                    val start=playback.state.value.positionMs
                    send(RealtimeFrame(status="正在连接源音频",startMs=start))
                    var processor: RealtimeProcessor?=null
                    try {
                        coroutineScope {
                            val pause=launch {
                                playback.state.map { it.playing }.distinctUntilChanged().collect { running ->
                                    send(latest.get().copy(status=if (running) "正在测量" else "已暂停 · 读数保持",running=running))
                                }
                            }
                            try {
                                decoder.read(track,start,{playback.state.value}) { pcm,count,channels ->
                                    if (processor==null) processor=RealtimeProcessor(channels,start)
                                    val frame=processor!!.process(pcm,count,options.value)
                                    latest.set(frame); send(frame)
                                }
                            } finally { pause.cancel() }
                        }
                        send(latest.get().copy(status="片段测量结束",running=false)); break
                    } catch (_: AnalysisResync) {
                        send(RealtimeFrame(status="正在与播放重新对齐",startMs=playback.state.value.positionMs))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { send(RealtimeFrame(status=e.message?.take(140) ?: "实时分析暂不可用，可重新测量。",error=true)); break }
                }
            }
    }.buffer(kotlinx.coroutines.channels.Channel.CONFLATED).flowOn(io)
}
