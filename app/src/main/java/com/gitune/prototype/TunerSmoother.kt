package com.gitune.prototype

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow

/** Smooths the tuner readout without changing the pitch samples plotted in Jam. */
internal class TunerSmoother {
    private var older = 0.0
    private var newer = 0.0
    private var displayed: Double? = null
    private var misses = 0

    fun reset() {
        displayed = null
        misses = 0
    }

    fun update(hz: Float?): Float? {
        if (hz == null) {
            // Bridge a brief dropout, but clear the note once the sound stops.
            if (++misses >= 3) reset()
            return displayed?.let { 2.0.pow(it).toFloat() }
        }

        misses = 0
        val pitch = log2(hz.toDouble())
        val last = displayed
        val next = if (last == null) {
            older = pitch
            pitch
        } else {
            // A median of three rejects a single bad frame (including octave jumps).
            val median = pitch.coerceIn(minOf(older, newer), maxOf(older, newer))
            val confirmed = abs(pitch - median) * 1200 < 50 && abs(newer - median) * 1200 < 50
            older = newer
            // Smooth small changes in cents; don't glide between distinct notes.
            if (abs(median - last) * 1200 >= 50) {
                if (confirmed) median else last
            } else last + 0.30 * (median - last)
        }
        newer = pitch
        displayed = next
        return 2.0.pow(next).toFloat()
    }
}
