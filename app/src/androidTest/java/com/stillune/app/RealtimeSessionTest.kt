package com.stillune.app

import android.content.*
import android.net.Uri
import android.os.IBinder
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.stillune.app.analysis.*
import com.stillune.app.data.Track
import com.stillune.app.playback.PlaybackService
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class RealtimeSessionTest {
    @Test fun analysisFollowsActualPlaybackHoldsOnPauseAndRestartsAfterSeek() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val file=File(context.cacheDir,"session-live.flac")
        instrumentation.context.assets.open("audio/moon.flac").use {src ->file.outputStream().use {src.copyTo(it)}}
        val connected=CountDownLatch(1)
        var service: PlaybackService?=null
        val connection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {service=(binder as PlaybackService.LocalBinder).service; connected.countDown()}
            override fun onServiceDisconnected(name: ComponentName?) {service=null}
        }
        val activity=ActivityScenario.launch(MainActivity::class.java)
        var collection: Job?=null
        try {
            context.bindService(Intent(context,PlaybackService::class.java),connection,Context.BIND_AUTO_CREATE)
            assertTrue(connected.await(10,TimeUnit.SECONDS))
            val engine=service!!
            val repository=RealtimeAnalysisRepository(RealtimePcmDecoder(context,Dispatchers.IO),engine.store,Dispatchers.IO)
            val latest=AtomicReference(RealtimeFrame())
            collection=launch(Dispatchers.Default) {repository.observe().collect {latest.set(it)}}
            instrumentation.runOnMainSync {engine.playQueue(listOf(Track(Uri.fromFile(file).toString(),"Live session",durationMs=6000,fileName="live.flac",source="test")),0)}
            withTimeout(15000) {while (latest.get().integrated==null) delay(50)}
            assertTrue(absDifference(latest.get().positionMs,engine.store.state.value.positionMs)<1100)
            instrumentation.runOnMainSync {engine.pause()}
            withTimeout(2000) {while (latest.get().status!="已暂停 · 读数保持") delay(20)}
            val paused=latest.get().measuredMs
            delay(300)
            assertEquals(paused,latest.get().measuredMs)
            instrumentation.runOnMainSync {engine.seek(2500);engine.resume()}
            withTimeout(15000) {while (latest.get().startMs<2400 || latest.get().integrated==null) delay(50)}
            assertTrue("New measurement after seek",latest.get().measuredMs<1500)
            instrumentation.runOnMainSync {engine.pause()}
            collection.cancelAndJoin()
        } finally {
            collection?.cancelAndJoin()
            instrumentation.runOnMainSync {service?.pause()}
            context.unbindService(connection); activity.close(); file.delete()
        }
    }
    private fun absDifference(a: Long,b: Long)=kotlin.math.abs(a-b)
}
