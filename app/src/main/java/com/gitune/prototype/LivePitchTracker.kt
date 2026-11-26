package com.gitune.prototype

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.sin

internal data class LivePitch(val tunerHz: Float? = null, val jamHints: List<Float> = emptyList())

private const val MAX_JAM_DOTS = 3
private const val FALLBACK_CENTS = 50.0

private data class SpectralPeak(val hz: Float, val power: Double)

/** Gates pitch analysis only; never changes mic gain or the recorded signal. */
internal class LivePitchTracker(rate: Int, windowSize: Int) {
    private val detector = PitchDetector(rate)
    private val spectrum = SpectralPitch(rate, windowSize)
    private var recentRms = 0.0
    private var fallbackHz: Float? = null
    private var fallbackFrames = 0

    fun reset() {
        recentRms = 0.0
        fallbackHz = null
        fallbackFrames = 0
    }

    fun detect(samples: FloatArray, rms: Double): LivePitch {
        // Track rising levels faster than falling ones, without letting a loud
        // passage block a quiet solo that immediately follows it.
        recentRms += (rms - recentRms) * if (rms > recentRms) 0.10 else 0.04
        val gate = (recentRms * 0.025).coerceIn(0.00012, 0.002)
        if (rms < gate) {
            resetFallback()
            return LivePitch()
        }
        val periodic = detector.detect(samples)
        val peaks = spectrum.detect(samples, periodic)
        if (periodic != null) resetFallback()
        // YIN is confident on clean notes but can miss an instrument in a noisy mix.
        // A lone spectral peak only drives the tuner once it repeats for a few frames.
        val tunerHz = periodic ?: confirmFallback(peaks)
        val hints = peaks.map { it.hz }.let { all ->
            if (periodic == null && tunerHz != null) all.filter { cents(it, tunerHz) >= FALLBACK_CENTS } else all
        }
        return LivePitch(tunerHz, hints)
    }

    private fun confirmFallback(peaks: List<SpectralPeak>): Float? {
        val strongest = peaks.firstOrNull()
        if (strongest == null || (peaks.size > 1 && strongest.power < peaks[1].power * 4)) {
            resetFallback()
            return null
        }
        val previous = fallbackHz
        fallbackHz = strongest.hz
        fallbackFrames = if (previous != null && cents(strongest.hz, previous) < FALLBACK_CENTS) fallbackFrames + 1 else 1
        return if (fallbackFrames >= 3) strongest.hz else null
    }

    private fun resetFallback() {
        fallbackHz = null
        fallbackFrames = 0
    }

    private fun cents(a: Float, b: Float) = abs(log2(a.toDouble() / b)) * 1200.0
}

/** Finds prominent tonal components, including overtones. */
private class SpectralPitch(private val rate: Int, windowSize: Int) {
    private val size = Integer.highestOneBit(windowSize - 1) shl 2
    private val bits = Integer.numberOfTrailingZeros(size)
    private val order = IntArray(size) { Integer.reverse(it) ushr (32 - bits) }
    private val window = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2 * PI * it / (windowSize - 1)) }
    private val cosine = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sine = DoubleArray(size / 2) { -sin(2 * PI * it / size) }
    private val real = DoubleArray(size)
    private val imag = DoubleArray(size)
    private val power = DoubleArray(size / 2)
    private val low = 36 * size / rate
    private val high = 1150 * size / rate
    private val shoulder = size * 2 / window.size
    private val sorted = DoubleArray(high - low + 1)

    fun detect(samples: FloatArray, periodicHz: Float?): List<SpectralPeak> {
        real.fill(0.0)
        imag.fill(0.0)
        val mean = samples.average()
        for (i in samples.indices) real[order[i]] = (samples[i] - mean) * window[i]
        for (stage in 1..bits) {
            val span = 1 shl stage
            val half = span / 2
            for (start in 0 until size step span) for (j in 0 until half) {
                val left = start + j
                val right = left + half
                val phase = j * (size / span)
                val r = cosine[phase] * real[right] - sine[phase] * imag[right]
                val q = sine[phase] * real[right] + cosine[phase] * imag[right]
                real[right] = real[left] - r
                imag[right] = imag[left] - q
                real[left] += r
                imag[left] += q
            }
        }

        var strongest = 0.0
        for (i in low - shoulder..high + shoulder) {
            power[i] = real[i] * real[i] + imag[i] * imag[i]
            if (i in low..high) {
                sorted[i - low] = power[i]
                strongest = maxOf(strongest, power[i])
            }
        }
        // Compare with the median bin rather than the mean, which the tones themselves inflate.
        sorted.sort()
        val minimum = maxOf(sorted[sorted.size / 2] * 16, strongest * 0.008)
        val limit = MAX_JAM_DOTS - if (periodicHz == null) 0 else 1
        val best = IntArray(limit) { -1 }
        for (i in low..high) {
            val peak = power[i]
            if (peak <= minimum || peak <= power[i - 1] || peak < power[i + 1]) continue
            if (peak < 4 * maxOf(power[i - shoulder], power[i + shoulder])) continue
            // Don't plot the same tone twice, but keep harmonics at other frequencies.
            if (periodicHz != null && abs(log2(frequency(i) / periodicHz.toDouble())) < 1.0 / 24) continue
            for (slot in best.indices) {
                if (best[slot] >= 0 && peak <= power[best[slot]]) continue
                for (j in best.lastIndex downTo slot + 1) best[j] = best[j - 1]
                best[slot] = i
                break
            }
        }
        return best.filter { it >= 0 }.map { SpectralPeak(frequency(it), power[it]) }
    }

    private fun frequency(bin: Int): Float {
        val left = ln(power[bin - 1].coerceAtLeast(1e-30))
        val center = ln(power[bin])
        val right = ln(power[bin + 1].coerceAtLeast(1e-30))
        val curvature = left - 2 * center + right
        val offset = if (abs(curvature) < 1e-12) 0.0 else
            (0.5 * (left - right) / curvature).coerceIn(-0.5, 0.5)
        return ((bin + offset) * rate / size).toFloat()
    }
}
