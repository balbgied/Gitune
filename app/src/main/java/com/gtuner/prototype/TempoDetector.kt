package com.gtuner.prototype

import kotlin.math.abs
import kotlin.math.roundToInt

/** Tracks repeated onsets in ~20 ms blocks of full-band audio RMS. */
internal class TempoDetector {
    private val beats = ArrayDeque<Float>()
    private var envelope = 0.0
    private var floor = 0.0
    private var peak = 0.0
    private var armed = true
    private var bpm: Int? = null

    fun reset() {
        beats.clear()
        envelope = 0.0
        floor = 0.0
        peak = 0.0
        armed = true
        bpm = null
    }

    fun update(seconds: Float, rms: Double): Int? {
        val timeout = bpm?.let { maxOf(1.2f, 150f / it) } ?: 2.5f
        if (beats.lastOrNull()?.let { seconds - it > timeout } == true) {
            beats.clear()
            bpm = null
        }

        // Fast attack catches claps, slower release bridges the gaps inside a syllable.
        envelope = maxOf(rms, envelope * 0.65)
        val threshold = maxOf(0.00075, floor * 1.8, floor + 0.00035)
        if (!armed) {
            peak = maxOf(peak, envelope)
            if (envelope < maxOf(threshold * 0.7, peak * 0.32)) armed = true
        } else if (envelope >= threshold) {
            armed = false
            peak = envelope
            if (beats.lastOrNull()?.let { seconds - it >= 0.24f } != false) {
                beats.addLast(seconds)
                if (beats.size > 6) beats.removeFirst()
                bpm = estimate()
            }
        }
        // Follow drops relatively quickly, but don't let a beat raise the noise floor much.
        val rate = if (envelope < floor) 0.15 else 0.015
        floor += (envelope - floor) * rate
        return bpm
    }

    private fun estimate(): Int? {
        if (beats.size < 4) return null
        val intervals = beats.zipWithNext { a, b -> b - a }
        val candidates = intervals.filter { it in 0.27f..1.25f }.sorted()
        if (candidates.isEmpty()) return null
        val period = (candidates[candidates.lastIndex / 2] + candidates[candidates.size / 2]) / 2
        val tolerance = maxOf(0.045f, period * 0.16f)
        val direct = intervals.filter { abs(it - period) <= tolerance }
        val missed = intervals.filter { abs(it - 2 * period) <= tolerance }.take(1)
        val matched = direct.size + missed.size
        if (direct.size < 2 || matched < 3 || matched < intervals.size - 1) return null
        return (60f * (direct.size + 2 * missed.size) / (direct.sum() + missed.sum())).roundToInt()
    }
}
