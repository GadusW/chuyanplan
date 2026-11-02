package com.stillune.app.analysis

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.stillune.app.data.Track
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia

data class DecodedAnalysis(val metrics: AudioMetrics, val channels: Int, val rate: Int)

class NativePcmAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun analyze(track: Track): DecodedAnalysis = withContext(io) {
        withTimeout(8 * 60 * 1000L) {
            val input = context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r") ?: error("无法读取音频")
            val pipe = ParcelFileDescriptor.createPipe()
            val poll = arrayOf(StructPollfd().apply { fd = pipe[0].fileDescriptor; events = OsConstants.POLLIN.toShort() })
            fun readAvailable(bytes: ByteArray, offset: Int, length: Int): Int =
                if (Os.poll(poll, 20) > 0 && poll[0].revents.toInt() and OsConstants.POLLIN != 0)
                    Os.read(pipe[0].fileDescriptor, bytes, offset, length) else -1
            val vlc = LibVLC(context, arrayListOf("--no-video", "--no-audio-time-stretch", "--avcodec-threads=1"))
            val player = MediaPlayer(vlc)
            val ended = AtomicBoolean(false)
            val failed = AtomicBoolean(false)
            try {
                val media = Media(vlc, input.fileDescriptor)
                val length: Long
                var channels = 0; var rate = 0
                try {
                    media.parse(IMedia.Parse.ParseLocal)
                    length = media.duration.takeIf { it > 0 } ?: track.durationMs
                    for (i in 0 until media.trackCount) {
                        (media.getTrack(i) as? IMedia.AudioTrack)?.let { channels = it.channels; rate = it.rate }
                    }
                    require(length > 0) { "无法确定歌曲时长，暂不能建立整曲频谱。" }
                    media.addOption(":sout=#transcode{acodec=s16l,channels=2,samplerate=44100}:std{access=file,mux=raw,dst=/proc/self/fd/${pipe[1].fd}}")
                    media.addOption(":sout-transcode-threads=1")
                    media.addOption(":sout-mux-caching=0")
                    player.setEventListener { event ->
                        if (event.type == MediaPlayer.Event.EndReached) ended.set(true)
                        if (event.type == MediaPlayer.Event.EncounteredError) failed.set(true)
                    }
                    player.media = media
                } finally { media.release() }
                val accumulator = AudioAccumulator(length)
                player.play()
                val bytes = ByteArray(65536)
                var carry = 0
                var endedAt = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    if (failed.get()) error("此文件暂时无法离线解码，请检查格式与读取授权。")
                    if (ended.get() && endedAt == 0L) endedAt = SystemClock.elapsedRealtime()
                    val count = try { readAvailable(bytes, carry, bytes.size-carry) }
                    catch (e: ErrnoException) {
                        if (e.errno != OsConstants.EAGAIN) throw e
                        -1
                    }
                    if (count > 0) {
                        val size = carry + count
                        var offset = 0
                        while (offset + 3 < size) {
                            val left = (bytes[offset].toInt() and 255) or (bytes[offset+1].toInt() shl 8)
                            val right = (bytes[offset+2].toInt() and 255) or (bytes[offset+3].toInt() shl 8)
                            accumulator.add(left / 32768.0, right / 32768.0)
                            offset += 4
                        }
                        carry = size-offset
                        if (carry > 0) bytes.copyInto(bytes, 0, offset, size)
                    } else {
                        if (endedAt > 0 && SystemClock.elapsedRealtime()-endedAt > 200) break
                        delay(12)
                    }
                }
                val metrics = accumulator.finish()
                require(metrics.durationMs >= (length * .95).toLong()) { "离线解码未覆盖完整音频，请重试。" }
                DecodedAnalysis(metrics, channels, rate)
            } finally {
                player.setEventListener(null)
                // VLC may open the proc pipe O_RDWR. Closing only our read end can leave its
                // writer blocked. Drain without doing DSP while stop joins the native producer.
                withContext(NonCancellable) {
                    try {
                        coroutineScope {
                            val stopping = async(io) { player.stop() }
                            val discard = ByteArray(65536)
                            while (!stopping.isCompleted) {
                                val read = try { readAvailable(discard, 0, discard.size) }
                                    catch (e: ErrnoException) { if (e.errno != OsConstants.EAGAIN) throw e; -1 }
                                if (read <= 0) delay(4)
                            }
                            stopping.await()
                        }
                    } finally {
                        pipe.forEach { it.close() }
                        player.release(); vlc.release(); input.close()
                    }
                }
            }
        }
    }
}
