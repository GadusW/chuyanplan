package com.stillune.app

import com.stillune.app.analysis.*
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class RealtimeAnalysisTest {
    private fun tone(offset: Int, amplitude: Double=.1, frequency: Double=1000.0, right: Double=1.0): FloatArray =
        FloatArray(9600) { i -> (amplitude*sin(2*PI*frequency*(offset+i/2)/48000)*(if (i%2==0) 1.0 else right)).toFloat() }

    @Test fun kWeightedStereoReferenceIsMinusTwentyAndMonoIsThreeLower() {
        val stereo=RealtimeProcessor(); val mono=RealtimeProcessor(1)
        var s=RealtimeFrame(); var m=RealtimeFrame()
        repeat(50) { s=stereo.process(tone(it*4800)); m=mono.process(tone(it*4800)) }
        // 1 kHz, -20 dBFS sample-peak sine: independent FFmpeg ebur128 reference ≈ -20.0 stereo / -23.0 mono.
        assertEquals(-20.0,s.integrated!!,.06)
        assertEquals(-23.01,m.integrated!!,.06)
        assertEquals(s.integrated!!,s.shortTerm!!,.04)
        assertEquals(s.momentary!!,s.momentaryMax!!,.04)
        assertEquals(0.0,s.lra!!,.03)
    }

    @Test fun silenceHasNoFabricatedMeasurementsAndNonFiniteInputIsBounded() {
        val processor=RealtimeProcessor()
        var result=RealtimeFrame()
        repeat(35) { result=processor.process(FloatArray(9600)) }
        assertNull(result.integrated); assertNull(result.truePeak); assertNull(result.correlation)
        assertNull(result.levels.rmsL); assertNull(result.lra)
        result=processor.process(FloatArray(9600) { if (it%2==0) Float.NaN else Float.POSITIVE_INFINITY })
        assertNull(result.integrated)
        assertTrue(result.spectrum.all {it.isFinite() && it<=-99f})
        assertTrue(result.scatter.all {it.isFinite()})
    }

    @Test fun peakRmsAndHoldDifferAndHoldEventuallyReleases() {
        val processor=RealtimeProcessor()
        val peak=processor.process(tone(0,.5))
        assertEquals(-6.0206,peak.levels.peakL!!,.01)
        assertEquals(-9.0309,peak.levels.rmsL!!,.01)
        val held=processor.process(FloatArray(9600))
        assertEquals(peak.levels.peakL!!,held.levels.holdL!!,.01)
        var released=held
        repeat(25) {released=processor.process(FloatArray(9600))}
        assertTrue(released.levels.holdL!! < held.levels.holdL!!-10)
        assertEquals(peak.truePeak!!,released.truePeak!!,.01)
    }

    @Test fun intersampleTruePeakExceedsSamplePeakForQuarterRateTone() {
        val processor=RealtimeProcessor()
        var result=RealtimeFrame()
        repeat(12) { block ->
            val pcm=FloatArray(9600) { i ->
                val n=block*4800+i/2
                (.9*sin(PI/2*n+PI/4)*min(1.0,n/1000.0)).toFloat()
            }
            result=processor.process(pcm)
        }
        assertTrue("Four-times interpolation must recover the between-sample crest",result.truePeak!!-result.levels.peakL!!>2.5)
        assertEquals(20*log10(.9),result.truePeak!!,.35)
    }

    @Test fun spectrumRetainsAntiphaseEnergyAndMidSideSelectRealChannels() {
        val processor=RealtimeProcessor()
        val options=RealtimeOptions(averageMs=0)
        val stereo=processor.process(tone(0,.5,right=-1.0),configuration=options)
        assertEquals(-1.0,stereo.correlation!!,1e-8)
        val peak=stereo.spectrum.indices.maxBy {stereo.spectrum[it]}
        assertEquals(1000.0,RealtimeProcessor.frequency(peak),80.0)
        assertEquals(-6.0,stereo.spectrum.max().toDouble(),.7)
        val mid=processor.process(tone(4800,.5,right=-1.0),configuration=options.copy(source=SpectrumSource.MID))
        assertTrue(mid.spectrum.max() < -99)
        val side=processor.process(tone(9600,.5,right=-1.0),configuration=options.copy(source=SpectrumSource.SIDE))
        assertEquals(stereo.spectrum.max().toDouble(),side.spectrum.max().toDouble(),.01)
        assertNotNull(processor.process(tone(14400,.5,right=-1.0),configuration=options.copy(source=SpectrumSource.SIDE)).integrated)
    }

    @Test fun gatingExcludesQuietMaterialAndMaxRemainsHeldAfterTheSignalFalls() {
        val meter=LoudnessMeter()
        fun add(seconds: Int, amplitude: Double) { repeat(seconds*48000) { n -> val s=amplitude*sin(2*PI*1000*n/48000);meter.add(s,s) } }
        add(4,.1); val before=meter.integrated!!; val max=meter.momentaryMax!!
        add(4,.00001)
        assertEquals(before,meter.integrated!!,.2)
        assertEquals(max,meter.momentaryMax!!,.001)
        assertTrue(meter.momentary!! < max-60)
    }

    @Test fun changingVisualOptionsPreservesIntegrationAndBoundsHistory() {
        val processor=RealtimeProcessor(startMs=2500)
        var frame=RealtimeFrame()
        repeat(75) { frame=processor.process(tone(it*4800),configuration=RealtimeOptions(historySeconds=6)) }
        assertEquals(60,frame.history.size)
        val before=frame.integrated!!
        frame=processor.process(tone(75*4800),configuration=RealtimeOptions(fftSize=8192,historySeconds=6,slopeDbOct=3))
        assertEquals(1,frame.history.size)
        assertEquals(7600L,frame.measuredMs)
        assertEquals(10100L,frame.positionMs)
        assertEquals(before,frame.integrated!!,.01)
    }

    @Test fun referenceTargetsDoNotTreatMissingDataAsCompliant() {
        assertEquals(0,LoudnessTarget.EBU.compare(null))
        assertEquals(1,LoudnessTarget.EBU.compare(-23.1))
        assertEquals(-1,LoudnessTarget.EBU.compare(-22.0))
        assertEquals(-2.0,LoudnessTarget.SPOTIFY.peakLimit(-10.0)!!,0.0)
        assertEquals(-1.0,LoudnessTarget.SPOTIFY.peakLimit(-16.0)!!,0.0)
        assertEquals(-24.0,LoudnessTarget.ATSC.lufs!!,0.0)
    }

    @Test fun gatedDynamicProgrammeMatchesIndependentFfmpegReference() {
        val meter=LoudnessMeter()
        val amplitudes=doubleArrayOf(.04,.2,.07,.32,0.0)
        repeat(36*48000) {n ->
            val s=amplitudes[(n/(8*48000)).coerceAtMost(4)]*sin(2*PI*1000*n/48000)
            meter.add(s,s)
        }
        // Reproducible recipe: tools/realtime_reference.py, FFmpeg 7.1 ebur128=peak=true.
        assertEquals(-13.1,meter.integrated!!,.11)
        assertEquals(18.1,meter.lra!!,.12)
        assertEquals(-9.9,meter.truePeak!!,.15)
    }
}
