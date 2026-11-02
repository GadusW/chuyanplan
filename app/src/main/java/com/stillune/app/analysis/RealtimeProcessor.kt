package com.stillune.app.analysis

import kotlin.math.*

/** Single-owner worker-thread DSP. Snapshots own their arrays; Canvas never sees a mutable ring. */
class RealtimeProcessor(private val sourceChannels: Int = 2, private val startMs: Long = 0) {
    companion object {
        const val RATE = 48000; const val HOP = 4800; const val BINS = 128
        fun frequency(index: Int): Double = 25.0*800.0.pow(index.toDouble()/(BINS-1))
    }
    private val loudness = LoudnessMeter(sourceChannels == 1)
    private val ringL = DoubleArray(8192); private val ringR = DoubleArray(8192)
    private var head = 0; private var frames = 0L
    private var options = RealtimeOptions()
    private var average = DoubleArray(BINS)
    private val history = ArrayDeque<FloatArray>()
    private val points = ArrayDeque<FloatArray>()
    private var holdL = 0.0; private var holdR = 0.0
    private var holdUntilL = 0L; private var holdUntilR = 0L

    fun process(pcm: FloatArray, frameCount: Int = pcm.size/2, configuration: RealtimeOptions = options): RealtimeFrame {
        require(frameCount > 0 && frameCount*2 <= pcm.size)
        if (configuration != options) {
            // Targets / camera / vectorscope mode never enter this path or reset loudness.
            if (configuration.source != options.source || configuration.fftSize != options.fftSize || configuration.averageMs != options.averageMs || configuration.slopeDbOct != options.slopeDbOct) {
                average = DoubleArray(BINS); history.clear()
            }
            options = configuration
        }
        var peakL = 0.0; var peakR = 0.0; var ll = 0.0; var rr = 0.0; var lr = 0.0; var sl = 0.0; var sr = 0.0
        val scatter = FloatArray(min(384, frameCount)*2)
        val polarPower = DoubleArray(48); val polarN = IntArray(48)
        for (i in 0 until frameCount) {
            // Do not clip intersample/decoded floating-point overs; non-finite corrupt samples are silence.
            val l = pcm[2*i].toDouble().let { if (it.isFinite()) it.coerceIn(-8.0, 8.0) else 0.0 }
            val r = pcm[2*i+1].toDouble().let { if (it.isFinite()) it.coerceIn(-8.0, 8.0) else 0.0 }
            loudness.add(l, r); ringL[head] = l; ringR[head] = r; head = (head+1)%ringL.size
            peakL = max(peakL, abs(l)); peakR = max(peakR, abs(r)); ll += l*l; rr += r*r; lr += l*r; sl += l; sr += r
            var mid = (l+r)/sqrt(2.0); var side = (r-l)/sqrt(2.0)
            if (mid < 0) { mid = -mid; side = -side }
            val bin = ((atan2(mid, side)/PI)*48).toInt().coerceIn(0,47)
            polarPower[bin] += (l*l+r*r)/2; polarN[bin]++
        }
        for (p in 0 until scatter.size/2) {
            // Stratification with an irregular sub-offset avoids coherent-tone decimation aliases.
            val i = (p.toLong()*frameCount/(scatter.size/2)+(p*7%11)).toInt().coerceAtMost(frameCount-1)
            scatter[2*p] = pcm[2*i].let { if (it.isFinite()) it else 0f }
            scatter[2*p+1] = pcm[2*i+1].let { if (it.isFinite()) it else 0f }
        }
        points.addLast(scatter)
        while (points.size > 4) points.removeFirst()
        val recentPoints=FloatArray(points.sumOf {it.size})
        var pointOffset=0
        points.forEach {it.copyInto(recentPoints,pointOffset);pointOffset+=it.size}
        frames += frameCount
        fun hold(peak: Double, previous: Double, until: Long): Pair<Double,Long> = when {
            peak >= previous -> peak to (frames+RATE*1.5).toLong()
            frames <= until -> previous to until
            else -> max(peak, previous*10.0.pow(-20.0*(frameCount.toDouble()/RATE)/20)) to until
        }
        hold(peakL, holdL, holdUntilL).let { holdL=it.first; holdUntilL=it.second }
        hold(peakR, holdR, holdUntilR).let { holdR=it.first; holdUntilR=it.second }
        val spectrum = spectrum(frameCount)
        history.addLast(spectrum)
        while (history.size > options.historySeconds*10) history.removeFirst()
        val varianceL = ll-sl*sl/frameCount; val varianceR = rr-sr*sr/frameCount
        return RealtimeFrame(status="正在测量", running=true, startMs=startMs, positionMs=startMs+frames*1000/RATE,
            measuredMs=loudness.measuredMs, sourceChannels=sourceChannels,
            momentary=loudness.momentary, momentaryMax=loudness.momentaryMax, shortTerm=loudness.shortTerm,
            integrated=loudness.integrated, lra=loudness.lra, truePeak=loudness.truePeak,
            levels=LiveLevels(amplitudeDb(peakL), amplitudeDb(peakR), powerDb(ll/frameCount), powerDb(rr/frameCount), amplitudeDb(holdL), amplitudeDb(holdR)),
            spectrum=spectrum, history=history.toList(), scatter=recentPoints,
            polar=FloatArray(48) { if (polarN[it]>0) sqrt(polarPower[it]/polarN[it]).toFloat() else 0f },
            correlation=if (varianceL>1e-16 && varianceR>1e-16) ((lr-sl*sr/frameCount)/sqrt(varianceL*varianceR)).coerceIn(-1.0,1.0) else null,
            balance=if (ll+rr>1e-16) ((rr-ll)/(ll+rr)).coerceIn(-1.0,1.0) else null, source=options.source)
    }

    private fun spectrum(frameCount: Int): FloatArray {
        val n = options.fftSize
        if (frames < n) return FloatArray(BINS) { -100f }
        val l = DoubleArray(n) { ringL[(head-n+it+ringL.size)%ringL.size] }
        val r = DoubleArray(n) { ringR[(head-n+it+ringR.size)%ringR.size] }
        val first: DoubleArray
        val second: DoubleArray?
        when (options.source) {
            SpectrumSource.STEREO -> { first=Fft.power(l); second=Fft.power(r) }
            SpectrumSource.LEFT -> { first=Fft.power(l); second=null }
            SpectrumSource.RIGHT -> { first=Fft.power(r); second=null }
            SpectrumSource.MID -> { first=Fft.power(DoubleArray(n) { (l[it]+r[it])/2 }); second=null }
            SpectrumSource.SIDE -> { first=Fft.power(DoubleArray(n) { (l[it]-r[it])/2 }); second=null }
        }
        val weight = if (options.averageMs == 0) 1.0 else 1-exp(-frameCount*1000.0/RATE/options.averageMs)
        return FloatArray(BINS) { band ->
            val frequency = frequency(band)
            val low = (frequency/800.0.pow(.5/(BINS-1))*n/RATE).roundToInt().coerceIn(1,first.lastIndex)
            val high = (frequency*800.0.pow(.5/(BINS-1))*n/RATE).roundToInt().coerceIn(low,first.lastIndex)
            var power = 0.0
            for (bin in low..high) power=max(power,16*(if (second==null) first[bin] else (first[bin]+second[bin])/2))
            average[band] += weight*(power-average[band])
            ((powerDb(average[band]) ?: -100.0)+options.slopeDbOct*log2(frequency/1000)).coerceIn(-100.0,24.0).toFloat()
        }
    }
}
