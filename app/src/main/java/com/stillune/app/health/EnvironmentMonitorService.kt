package com.stillune.app.health

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.stillune.app.MainActivity
import com.stillune.app.R
import com.stillune.app.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/** Relative microphone energy only. No SPL conversion, raw audio storage, or automatic restart. */
@AndroidEntryPoint
class EnvironmentMonitorService : Service() {
    @Inject lateinit var hearing: HearingRepository
    @Inject @IoDispatcher lateinit var io: CoroutineDispatcher
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = AtomicReference<AudioRecord?>(null)
    private var job: Job? = null
    private var wake: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop" || !hearing.settings.value.enabled) { stopSelf(); return START_NOT_STICKY }
        if (job != null) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            hearing.environmentChanged(EnvironmentReading(status = "麦克风未授权，请在护耳页重新开启")); stopSelf(); return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "环境趋势监测", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 21, Intent(this, MainActivity::class.java).setAction(MainActivity.OPEN_HEALTH)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 22, Intent(this, javaClass).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("栖声 · 环境趋势监测中").setContentText("麦克风仅在本机分析，不保存录音。点此查看或关闭。")
                .setContentIntent(open).setOngoing(true).setSilent(true).addAction(0, "停止监测", stop).build()
            if (Build.VERSION.SDK_INT >= 30) startForeground(19, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(19, notification)
        } catch (_: Exception) {
            hearing.environmentChanged(EnvironmentReading(status = "系统未允许启动麦克风，请回到护耳页重试")); stopSelf(); return START_NOT_STICKY
        }
        hearing.environmentChanged(EnvironmentReading(true, status = "正在建立本次基线，请保持手机位置稳定"))
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Stillune:environment").apply {
            setReferenceCounted(false); acquire(600_000L)
        }
        scope.launch { hearing.settings.collect { if (!it.enabled) stopSelf() } }
        job = scope.launch {
            try {
                readings().flowOn(io).collect { reading ->
                    if (wake?.isHeld == false) wake?.acquire(600_000L)
                    hearing.environmentChanged(reading)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { hearing.environmentChanged(EnvironmentReading(status = "麦克风不可用或授权已撤回，请关闭其他录音应用后重试")) }
            finally { stopSelf() }
        }
        return START_NOT_STICKY
    }

    @android.annotation.SuppressLint("MissingPermission") // Permission checked before launching; revocation handled as unavailable.
    private fun readings(): Flow<EnvironmentReading> = flow {
        val audio = getSystemService(AudioManager::class.java)
        val builtin = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: error("No built-in microphone")
        val source = if (audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
            MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minimum = AudioRecord.getMinBufferSize(48_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        val record = AudioRecord.Builder().setAudioSource(source).setAudioFormat(AudioFormat.Builder()
            .setSampleRate(48_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(max(minimum * 2, 48_000)).build()
        recorder.set(record)
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED)
            record.setPreferredDevice(builtin)
            record.startRecording()
            val buffer = ShortArray(12_000)
            var baselinePower = 0.0
            var baselineBlocks = 0
            var smoothed = 0.0
            var zeroBlocks = 0
            var lastRoute = -1
            var lastValid = false
            while (currentCoroutineContext().isActive) {
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                currentCoroutineContext().ensureActive()
                check(count > 0)
                val route = record.routedDevice
                val silenced = Build.VERSION.SDK_INT >= 29 && record.activeRecordingConfiguration?.isClientSilenced == true
                var power = 0.0
                for (i in 0 until count) { val value = buffer[i] / 32768.0; power += value * value }
                power /= count
                zeroBlocks = if (power <= 1e-12) zeroBlocks + 1 else 0
                val valid = route?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && !silenced && zeroBlocks < 8
                if (!valid) {
                    baselineBlocks = 0; baselinePower = 0.0; lastValid = false
                    emit(EnvironmentReading(true, status = "输入被静音、改道或无有效数据，暂不能判断环境"))
                    continue
                }
                if (route.id != lastRoute || !lastValid) { baselineBlocks = 0; baselinePower = 0.0; smoothed = 0.0 }
                lastRoute = route.id; lastValid = true
                if (baselineBlocks < 12) {
                    baselinePower += power; baselineBlocks++
                    emit(EnvironmentReading(true, status = "正在建立本次基线，请保持手机位置稳定"))
                } else {
                    val change = 10 * log10(max(power, 1e-12) / max(baselinePower / baselineBlocks, 1e-12))
                    smoothed += .2 * (change - smoothed)
                    emit(EnvironmentReading(true, smoothed.toFloat().coerceIn(-60f, 60f),
                        if (source == MediaRecorder.AudioSource.UNPROCESSED) "相对起始基线 · 原始输入路径" else "相对起始基线 · 系统处理可能影响趋势"))
                }
            }
        } finally {
            recorder.compareAndSet(record, null)
            runCatching { record.stop() }
            record.release()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { recorder.get()?.stop() }
        if (wake?.isHeld == true) wake?.release()
        val current = hearing.environment.value
        hearing.environmentChanged(EnvironmentReading(status = if (current.running) "监测已停止" else current.status))
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object { private const val CHANNEL = "stillune_environment" }
}
