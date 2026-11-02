package com.stillune.app.playback

import android.content.*
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PlaybackConnection @Inject constructor(@ApplicationContext private val context: Context) : ServiceConnection {
    private var service: PlaybackService? = null
    private val pending = mutableListOf<(PlaybackService) -> Unit>()
    private var bound = false
    fun connect() {
        if (!bound) bound = context.bindService(Intent(context, PlaybackService::class.java), this, Context.BIND_AUTO_CREATE)
    }
    fun command(block: (PlaybackService) -> Unit) {
        service?.let(block) ?: run { pending += block; connect() }
    }
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as PlaybackService.LocalBinder).service
        pending.toList().also { pending.clear() }.forEach { it(service!!) }
    }
    override fun onServiceDisconnected(name: ComponentName?) { service = null }
    fun close() {
        if (bound) context.unbindService(this)
        bound = false; service = null; pending.clear()
    }
}
