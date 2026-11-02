package com.stillune.app

import android.Manifest
import android.media.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.rule.GrantPermissionRule
import com.stillune.app.immersion.liveAudioEnergy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.math.*

class AudioEnergyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)).around(compose)
    @Test fun pulsedPcmProducesMultipleRealAttackEvents() = runBlocking {
        val id = compose.activity.getSystemService(AudioManager::class.java).generateAudioSessionId()
        val pcm = ShortArray(44100 * 2) { index ->
            val sample = index / 2
            val phase = sample % 22050
            val gain = if (phase in 4410..9702) .7 else .025
            (sin(2 * PI * 220 * sample / 44100) * 30000 * gain).toInt().toShort()
        }
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setSessionId(id).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        try {
            track.write(pcm, 0, pcm.size); track.setLoopPoints(0, pcm.size / 2, -1); track.play()
            val result = withTimeout(6000) { withContext(Dispatchers.Main) {
                liveAudioEnergy(id).first { it.available && it.beatId >= 3 }
            } }
            assertTrue(result.onset > .27f)
        } finally { track.stop(); track.release() }
    }
    @Test fun realAudioTrackProducesNonzeroRms() = runBlocking {
        val manager = compose.activity.getSystemService(AudioManager::class.java)
        val id = manager.generateAudioSessionId()
        val pcm = ShortArray(44100 * 2) { (sin(2 * PI * 440 * (it / 2) / 44100) * 16000).toInt().toShort() }
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setSessionId(id).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        try {
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size / 2, -1)
            track.play()
            val result = withTimeout(4000) { withContext(Dispatchers.Main) { liveAudioEnergy(id).first { it.available && it.rms > 0f } } }
            assertTrue(result.rms > 0f)
            // Exercise callbacks already posted to the main looper during cancellation.
            repeat(24) {
                withTimeout(2000) { withContext(Dispatchers.Main) { liveAudioEnergy(id).first { it.available } } }
            }
            withContext(Dispatchers.Main) { delay(250) }
        } finally { track.stop(); track.release() }
    }
}
