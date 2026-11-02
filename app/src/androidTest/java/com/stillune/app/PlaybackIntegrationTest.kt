package com.stillune.app

import android.content.*
import android.app.Notification
import android.app.NotificationManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.stillune.app.data.*
import com.stillune.app.playback.PlaybackService
import com.stillune.app.health.HearingSettings
import com.stillune.app.health.EnvironmentMonitorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import com.stillune.app.immersion.liveAudioEnergy
import kotlinx.coroutines.flow.onEach
import com.stillune.app.immersion.AudioEnergy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class PlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(message: String, timeout: Long = 10000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) { if (predicate()) return; Thread.sleep(100) }
        fail(message)
    }
    private fun fixtures(): List<Uri> {
        val audio = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return listOf("moon.mp3", "moon.wav", "moon.flac", "moon.aac", "moon-alac.m4a").map { name ->
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "Stillune_test_${System.nanoTime()}_$name")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/StilluneTest/")
                put(MediaStore.Audio.Media.MIME_TYPE, when (name.substringAfterLast('.')) {
                    "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "flac" -> "audio/flac"; "aac" -> "audio/aac"; else -> "audio/mp4"
                })
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(audio, values)!!
            instrumentation.context.assets.open("audio/$name").use { input ->
                context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        }
    }

    @Test fun fiveFormatsScanImportDecodeSeekAndPlayInBackground() = runBlocking {
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_MEDIA_AUDIO").close()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.RECORD_AUDIO")).use { it.readBytes() }
        val uris = fixtures()
        val db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        val repository = LibraryRepository(context, db, db.tracks(), Dispatchers.IO)
        var service: PlaybackService? = null
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as PlaybackService.LocalBinder).service; connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null }
        }
        // ActivityScenario tracks the launch Intent. Match the subsequent notification Intent
        // because MainActivity correctly retains onNewIntent for rotation/deep-link restoration.
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).setAction(MainActivity.OPEN_PLAYER))
        var testFailure: Throwable? = null
        var hearingSettings: HearingSettings? = null
        try {
            val scan = repository.scanMedia(ScanOptions()) { }
            assertTrue("All five formats should be indexed", scan.found >= 5)
            val imported = repository.importFiles(uris)
            assertEquals(5, imported.found)
            repository.importFiles(uris)
            // MediaStore's merged external collection and external_primary can have different URI
            // strings for the same row. The repository deliberately preserves the first valid URI.
            val ids = uris.map(ContentUris::parseId).toSet()
            val tracks = db.tracks().observe().first().filter {
                Uri.parse(it.uri).authority == "media" && ContentUris.parseId(Uri.parse(it.uri)) in ids
            }
            assertEquals("Repeated import must not duplicate files", 5, tracks.size)
            assertTrue("Import must preserve normalized media identities", tracks.all { it.identity.startsWith("primary:Music/StilluneTest/") })
            assertEquals("ALAC must be identified inside M4A", "ALAC", tracks.first {
                ContentUris.parseId(Uri.parse(it.uri)) == ContentUris.parseId(uris.last())
            }.format)
            main { context.bindService(Intent(context, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE) }
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val engine = service!!
            main { hearingSettings = engine.hearing.settings.value; engine.hearing.update { it.copy(enabled = true) } }
            for (track in tracks) {
                main { engine.playQueue(listOf(track), 0) }
                waitFor("${track.fileName}: software decoding failed: ${engine.store.state.value.error}") {
                    val state = engine.store.state.value
                    state.playing && state.positionMs > 350 && state.error == null
                }
                assertTrue("Duration must be known for ${track.fileName}", engine.store.state.value.durationMs > 4000)
                if (track == tracks.first()) {
                    val sessionId = engine.store.state.value.audioSessionId
                    assertTrue("RMS must use our own positive playback session", sessionId > 0)
                    val captured = mutableListOf<AudioEnergy>()
                    val signal = try { withTimeout(4000) {
                        withContext(Dispatchers.Main) { liveAudioEnergy(sessionId).onEach { captured += it }.first { it.available && it.rms > .000001f } }
                    } } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
                        throw AssertionError("RMS session=$sessionId frames=${captured.size} max=${captured.maxOfOrNull { it.rms }} status=${captured.lastOrNull()}", failure)
                    }
                    assertTrue("Actual decoded audio must produce a real RMS signal", signal.rms > 0f)
                }
                main { engine.seek(2500) }
                waitFor("Seek failed: ${track.fileName}") { engine.store.state.value.positionMs >= 2400 }
                main { engine.pause() }
                assertFalse(engine.store.state.value.playing)
            }
            main { engine.playQueue(tracks, 0) }
            waitFor("Queue did not start") { engine.store.state.value.playing && engine.store.state.value.positionMs > 300 }
            val notifications = context.getSystemService(NotificationManager::class.java)
            var published: Notification? = null
            waitFor("Media notification was not published") {
                published = notifications.activeNotifications.firstOrNull { it.id == 7 }?.notification
                published != null
            }
            val notification = requireNotNull(published)
            @Suppress("DEPRECATION")
            val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
            assertNotNull("System UI must receive a MediaSession token", token)
            val controller = MediaController(context, token!!)
            waitFor("Session title was not synchronized") {
                controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == tracks[0].title
            }
            val metadata = requireNotNull(controller.metadata)
            assertEquals(tracks[0].artist, metadata.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertTrue(metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) > 4000)
            assertNotNull("Embedded or fallback artwork must be available", metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
            assertEquals("System transport should show three actions", 3, notification.actions.size)
            controller.transportControls.pause()
            waitFor("System media pause did not reach the player") { !engine.store.state.value.playing }
            controller.transportControls.play()
            waitFor("System media play did not reach the player") { engine.store.state.value.playing }
            controller.transportControls.seekTo(1500)
            waitFor("System media seek failed") { engine.store.state.value.positionMs >= 1400 }
            controller.transportControls.skipToNext()
            waitFor("Next track did not play") { engine.store.state.value.index == 1 && engine.store.state.value.playing }
            waitFor("Notification title did not follow next track") {
                controller.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == tracks[1].uri
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            val before = engine.store.state.value.positionMs
            val hearingBefore = engine.hearing.live.value.continuousMs
            waitFor("Background playback stopped") { engine.store.state.value.positionMs > before + 500 && engine.store.state.value.playing }
            waitFor("Hearing records stopped when activity went to background") { engine.hearing.live.value.continuousMs > hearingBefore + 250 }
            main { engine.pause() }
            Thread.sleep(600)
            val pausedHearing = engine.hearing.live.value.continuousMs
            Thread.sleep(700)
            assertEquals("Paused playback must not accumulate listening time", pausedHearing, engine.hearing.live.value.continuousMs)
            // A corrupt, inaccessible file must surface an error and remain recoverable.
            main { notification.contentIntent.send() }
            waitFor("Notification tap did not return to foreground") {
                runCatching { scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED }.getOrDefault(false)
            }
            main { engine.playQueue(listOf(tracks.first().copy(uri = "content://media/external/audio/media/999999999")), 0) }
            waitFor("Missing file did not report an error") { engine.store.state.value.error != null && !engine.store.state.value.playing }
            main { engine.playQueue(listOf(tracks.first()), 0) }
            waitFor("Player did not recover after a missing file") { engine.store.state.value.playing && engine.store.state.value.error == null }
            main { engine.pause() }
            main { engine.playQueue(listOf(tracks.first()),0,2300) }
            waitFor("Resumed decoder did not seek to the saved position") {
                engine.store.state.value.playing && engine.store.state.value.positionMs >= 2200
            }
            main { engine.pause() }
            engine.memory.flush()
            val saved = requireNotNull(engine.memory.restore())
            assertEquals(tracks.first().uri,saved.track?.uri)
            assertTrue(saved.positionMs >= 2200)
            assertFalse(saved.playing)
            main { context.startForegroundService(Intent(context, EnvironmentMonitorService::class.java)) }
            waitFor("Opt-in microphone foreground service did not start: ${engine.hearing.environment.value.status}") {
                engine.hearing.environment.value.running
            }
            assertTrue(notifications.activeNotifications.any { it.id == 19 })
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            Thread.sleep(800)
            assertTrue("Environment monitoring should survive leaving its page", engine.hearing.environment.value.running)
            main { engine.hearing.update { it.copy(enabled = false) } }
            waitFor("Master switch did not stop the microphone") { !engine.hearing.environment.value.running }
            waitFor("Microphone foreground notification was not removed") { notifications.activeNotifications.none { it.id == 19 } }
        } catch (failure: Throwable) {
            testFailure = failure
            throw failure
        } finally {
            context.stopService(Intent(context, EnvironmentMonitorService::class.java))
            main { hearingSettings?.let { previous -> service?.hearing?.update { previous } } }
            main { if (service != null) context.unbindService(connection) }
            try { scenario.close() } catch (cleanup: Throwable) {
                if (testFailure != null) testFailure.addSuppressed(cleanup) else throw cleanup
            }
            db.close()
            uris.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}
