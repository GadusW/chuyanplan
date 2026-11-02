package com.stillune.app.analysis

import kotlin.math.*

data class AudioMetrics(
    val durationMs: Long, val sampleStartMs: Long, val sampleDurationMs: Long,
    val drEstimate: Double?, val correlation: Double?, val width: Double?,
    val balanceDb: Double?, val spectrum: ByteArray, val scatter: ByteArray,
    val columns: Int = AudioAccumulator.COLUMNS, val bands: Int = AudioAccumulator.BANDS,
)

/** Bounded-memory offline reducer. No Android/UI dependencies, and no live-player FFT. */
class AudioAccumulator(private val durationMs: Long, private val rate: Int = 44100) {
    companion object { const val COLUMNS = 160; const val BANDS = 32; const val FFT_SIZE = 2048 }
    private val start = durationMs / 3
    private val finish = minOf(durationMs, start + 20000)
    private var frame = 0L
    private val fftInput = DoubleArray(FFT_SIZE)
    private val fftRight = DoubleArray(FFT_SIZE)
    private var fill = 0
    private var nextFftFrame = 0L
    private val energies = DoubleArray(COLUMNS * BANDS)
    private val counts = IntArray(COLUMNS)
    private var n = 0L
    private var sumL = 0.0; private var sumR = 0.0
    private var ll = 0.0; private var rr = 0.0; private var lr = 0.0
    private var midPower = 0.0; private var sidePower = 0.0
    private var blockN = 0; private var blockL = 0.0; private var blockR = 0.0
    private var peakL = 0.0; private var peakR = 0.0
    private val blocks = mutableListOf<DoubleArray>()
    private val points = ArrayList<Pair<Double, Double>>(256)
    private var candidates = 0
    private val random = java.util.Random(0x57111)

    fun add(left: Double, right: Double) {
        val l = if (left.isFinite()) left.coerceIn(-1.0, 1.0) else 0.0
        val r = if (right.isFinite()) right.coerceIn(-1.0, 1.0) else 0.0
        val ms = frame * 1000 / rate
        if (frame >= nextFftFrame) {
            fftInput[fill] = l; fftRight[fill] = r; fill++
            // Analyse channels separately when antiphase would cancel a mono downmix.
            if (fill == FFT_SIZE) {
                val column = ((ms.toDouble() / durationMs.coerceAtLeast(1)) * COLUMNS).toInt().coerceIn(0, COLUMNS - 1)
                val power = Fft.power(fftInput)
                val rightPower = Fft.power(fftRight)
                for (i in power.indices) power[i] = (power[i] + rightPower[i]) / 2
                for (band in 0 until BANDS) {
                    val low = maxOf(1, (20.0 * (1000.0).pow(band.toDouble() / BANDS) * FFT_SIZE / rate).toInt())
                    val high = maxOf(low + 1, (20.0 * (1000.0).pow((band + 1.0) / BANDS) * FFT_SIZE / rate).toInt()).coerceAtMost(power.size)
                    var energy = 0.0
                    for (i in low.coerceAtMost(power.lastIndex) until high) energy += power[i]
                    energies[column * BANDS + band] += energy / (high - low).coerceAtLeast(1)
                }
                counts[column]++
                val hop = minOf(rate / 4, (durationMs * rate / 1000 / COLUMNS).toInt().coerceAtLeast(1))
                nextFftFrame = frame + maxOf(hop - FFT_SIZE, 0)
                fill = 0
            }
        }
        if (ms >= start && ms < finish) {
            n++; sumL += l; sumR += r; ll += l*l; rr += r*r; lr += l*r
            midPower += (l+r)*(l+r)/4; sidePower += (l-r)*(l-r)/4
            blockN++; blockL += l*l; blockR += r*r
            peakL = maxOf(peakL, abs(l)); peakR = maxOf(peakR, abs(r))
            if (blockN >= rate * 3) flushBlock()
            if (n % 257L == 0L) {
                candidates++
                val point = Pair((l-r)/sqrt(2.0), (l+r)/sqrt(2.0))
                if (points.size < 256) points += point else {
                    val slot = random.nextInt(candidates)
                    if (slot < 256) points[slot] = point
                }
            }
        }
        frame++
    }

    private fun flushBlock() {
        if (blockN > 0) blocks += doubleArrayOf(blockL / blockN, blockR / blockN, peakL, peakR, blockN.toDouble())
        blockN = 0; blockL = 0.0; blockR = 0.0; peakL = 0.0; peakR = 0.0
    }

    fun finish(): AudioMetrics {
        if (blockN >= rate || blocks.isEmpty()) flushBlock()
        for (column in 0 until COLUMNS) if (counts[column] > 0) for (band in 0 until BANDS)
            energies[column * BANDS + band] /= counts[column]
        // Interpolate between measured whole-track FFT windows, never tile the representative excerpt.
        val measured = counts.indices.filter { counts[it] > 0 }
        for (column in 0 until COLUMNS) if (counts[column] == 0 && measured.isNotEmpty()) {
            val before = measured.lastOrNull { it < column } ?: measured.first()
            val after = measured.firstOrNull { it > column } ?: measured.last()
            val weight = if (before == after) 0.0 else (column-before).toDouble()/(after-before)
            for (band in 0 until BANDS) energies[column*BANDS+band] =
                energies[before*BANDS+band]*(1-weight) + energies[after*BANDS+band]*weight
        }
        val maximum = energies.maxOrNull()?.coerceAtLeast(1e-20) ?: 1e-20
        val spectrum = ByteArray(energies.size) { index ->
            if (energies[index] <= 1e-20) 0 else
                (((10 * log10(energies[index] / maximum) + 75) / 75).coerceIn(0.0, 1.0) * 255).roundToInt().toByte()
        }
        val varianceL = if (n > 0) ll - sumL*sumL/n else 0.0
        val varianceR = if (n > 0) rr - sumR*sumR/n else 0.0
        val correlation = if (varianceL > 1e-12 && varianceR > 1e-12)
            ((lr - sumL*sumR/n) / sqrt(varianceL*varianceR)).coerceIn(-1.0, 1.0) else null
        val width = if (midPower+sidePower > 1e-12) sqrt(sidePower/(midPower+sidePower)).coerceIn(0.0, 1.0) else null
        val drChannels = (0..1).mapNotNull { channel ->
            val eligible = blocks.filter { it[4] >= rate || blocks.size == 1 }
            val powers = eligible.map { it[channel] }.sortedDescending()
            val peaks = eligible.map { it[channel+2] }.sortedDescending()
            if (powers.isEmpty() || powers.first() <= 1e-12) null else {
                val top = powers.take(maxOf(1, ceil(powers.size * .2).toInt())).average()
                val peak = peaks.getOrElse(1) { peaks.first() }
                (20 * log10(peak.coerceAtLeast(1e-12) / sqrt(2 * top))).coerceAtLeast(0.0)
            }
        }
        val dr = drChannels.takeIf { n >= rate && it.isNotEmpty() }?.average()
        val scale = points.maxOfOrNull { maxOf(abs(it.first), abs(it.second)) }?.coerceAtLeast(1e-12) ?: 1.0
        val scatter = java.nio.ByteBuffer.allocate(points.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        points.forEach { (x,y) -> scatter.putShort((x/scale*32767).roundToInt().toShort()); scatter.putShort((y/scale*32767).roundToInt().toShort()) }
        return AudioMetrics(frame*1000/rate, start, n*1000/rate, dr, correlation, width,
            if (ll > 1e-12 && rr > 1e-12) 10*log10(ll/rr) else null, spectrum, scatter.array())
    }
}

object Fft {
    private data class Plan(val window: DoubleArray, val cos: DoubleArray, val sin: DoubleArray)
    private val plans = java.util.concurrent.ConcurrentHashMap<Int, Plan>()
    fun power(samples: DoubleArray): DoubleArray {
        val n = samples.size
        require(n > 1 && n and (n - 1) == 0)
        val plan = plans.getOrPut(n) { Plan(DoubleArray(n) { .5-.5*cos(2*PI*it/(n-1)) },
            DoubleArray(n/2) { cos(-2*PI*it/n) }, DoubleArray(n/2) { sin(-2*PI*it/n) }) }
        val re = DoubleArray(n) { samples[it] * plan.window[it] }
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var size = 2
        while (size <= n) {
            for (base in 0 until n step size) for (k in 0 until size/2) {
                val a = base+k; val b = a+size/2
                val c = plan.cos[k*n/size]; val s = plan.sin[k*n/size]
                val r = re[b]*c - im[b]*s; val v = re[b]*s + im[b]*c
                re[b] = re[a]-r; im[b] = im[a]-v; re[a] += r; im[a] += v
            }
            size *= 2
        }
        return DoubleArray(n/2) { (re[it]*re[it]+im[it]*im[it])/(n.toDouble()*n) }
    }
}
