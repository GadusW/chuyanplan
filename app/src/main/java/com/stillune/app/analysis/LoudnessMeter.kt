package com.stillune.app.analysis

import kotlin.math.*

internal fun powerDb(value: Double): Double? = if (value > 1e-20 && value.isFinite()) 10 * log10(value) else null
internal fun amplitudeDb(value: Double): Double? = powerDb(value * value)
private fun loudness(power: Double): Double? = powerDb(power)?.minus(.691)

private class Biquad(private val b0: Double, private val b1: Double, private val b2: Double,
                     private val a1: Double, private val a2: Double) {
    private var s1 = 0.0; private var s2 = 0.0
    fun process(x: Double): Double {
        val y = b0*x+s1
        s1 = b1*x-a1*y+s2; s2 = b2*x-a2*y
        return y
    }
}

/** BS.1770-5 tables 1/2, at exactly 48 kHz. No generic RMS-to-LUFS conversion. */
private class KWeighting {
    private val shelf = Biquad(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, .73248077421585)
    private val highPass = Biquad(1.0, -2.0, 1.0, -1.99004745483398, .99007225036621)
    fun process(x: Double) = highPass.process(shelf.process(x))
}

/** Fixed 0.01 LU bins keep long measurements bounded; exact power sums are retained for gating. */
internal class LoudnessDistribution {
    private val count = LongArray(10001)
    private val sums = DoubleArray(count.size)
    private var total = 0L; private var sum = 0.0
    fun add(power: Double) {
        val lu = loudness(power) ?: return
        if (lu < -70) return
        val bin = ((lu+70)*100).roundToInt().coerceIn(0, count.lastIndex)
        count[bin]++; sums[bin] += power; total++; sum += power
    }
    private fun gate(relativeLu: Double): Int? {
        if (total == 0L) return null
        val threshold = max(-70.0, (loudness(sum/total) ?: return null)+relativeLu)
        return ceil((threshold+70)*100).toInt().coerceIn(0, count.lastIndex)
    }
    fun integrated(): Double? {
        val first = gate(-10.0) ?: return null
        var n = 0L; var power = 0.0
        for (i in first..count.lastIndex) { n += count[i]; power += sums[i] }
        return if (n > 0) loudness(power/n) else null
    }
    fun range(): Double? {
        val first = gate(-20.0) ?: return null
        var n = 0L
        for (i in first..count.lastIndex) n += count[i]
        if (n < 2) return null
        fun percentile(p: Double): Double {
            val rank = ((n-1)*p).roundToLong()+1
            var seen = 0L
            for (i in first..count.lastIndex) { seen += count[i]; if (seen >= rank) return i/100.0-70 }
            return 30.0
        }
        return (percentile(.95)-percentile(.1)).coerceAtLeast(0.0)
    }
}

/** Annex 2's four-phase / 48-tap interpolation FIR. Floating point needs no 12.04 dB headroom shift. */
internal class TruePeak4x {
    private val history = DoubleArray(12)
    private var head = 0
    fun add(x: Double): Double {
        head = (head+11)%12; history[head] = x
        var peak = abs(x)
        for (phase in COEFFICIENTS) {
            var y = 0.0; var cursor = head
            for (coefficient in phase) { y += coefficient*history[cursor]; cursor++; if (cursor == 12) cursor = 0 }
            peak = max(peak, abs(y))
        }
        return peak
    }
    companion object {
        private val COEFFICIENTS = arrayOf(
            doubleArrayOf(.001708984375, .010986328125, -.0196533203125, .033203125, -.0594482421875, .1373291015625, .97216796875, -.102294921875, .047607421875, -.026611328125, .014892578125, -.00830078125),
            doubleArrayOf(-.0291748046875, .029296875, -.0517578125, .089111328125, -.16650390625, .465087890625, .77978515625, -.2003173828125, .1015625, -.0582275390625, .0330810546875, -.0189208984375),
            doubleArrayOf(-.0189208984375, .0330810546875, -.0582275390625, .1015625, -.2003173828125, .77978515625, .465087890625, -.16650390625, .089111328125, -.0517578125, .029296875, -.0291748046875),
            doubleArrayOf(-.00830078125, .014892578125, -.026611328125, .047607421875, -.102294921875, .97216796875, .1373291015625, -.0594482421875, .033203125, -.0196533203125, .010986328125, .001708984375),
        )
    }
}

/** 100 ms hops, 400 ms M / integrated gates, 3 s S / LRA. Pausing adds no zero samples. */
class LoudnessMeter(private val mono: Boolean = false) {
    private val left = KWeighting(); private val right = KWeighting()
    private val tpL = TruePeak4x(); private val tpR = TruePeak4x()
    private val ring = DoubleArray(30)
    private val integratedDistribution = LoudnessDistribution(); private val lraDistribution = LoudnessDistribution()
    private var head = 0; private var hops = 0L; private var samples = 0; private var energy = 0.0
    private var truePeakAmplitude = 0.0
    var momentary: Double? = null; private set
    var shortTerm: Double? = null; private set
    var momentaryMax: Double? = null; private set
    val integrated: Double? get() = integratedDistribution.integrated()
    val lra: Double? get() = lraDistribution.range()
    val truePeak: Double? get() = amplitudeDb(truePeakAmplitude)
    val measuredMs: Long get() = hops*100+samples*1000L/48000
    fun add(l: Double, r: Double) {
        val kl = left.process(l); val kr = right.process(r)
        energy += kl*kl + if (mono) 0.0 else kr*kr
        truePeakAmplitude = max(truePeakAmplitude, max(tpL.add(l), tpR.add(r)))
        samples++
        if (samples == 4800) {
            ring[head] = energy/4800; head = (head+1)%30; hops++; samples = 0; energy = 0.0
            fun mean(n: Int): Double { var p = 0.0; for (i in 1..n) p += ring[(head-i+30)%30]; return p/n }
            if (hops >= 4) {
                val m = mean(4); momentary = loudness(m)
                momentary?.let { momentaryMax = max(momentaryMax ?: it, it) }
                integratedDistribution.add(m)
            }
            if (hops >= 30) { val s = mean(30); shortTerm = loudness(s); lraDistribution.add(s) }
        }
    }
}
