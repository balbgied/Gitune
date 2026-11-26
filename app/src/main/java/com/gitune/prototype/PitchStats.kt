package com.gitune.prototype

import kotlin.math.log2
import kotlin.math.roundToInt

internal fun midi(hz: Float, a4: Int): Double = 69 + 12 * log2(hz.toDouble() / a4)

/**
 * Per-semitone detection counts for the last [window] seconds. A note counts at most
 * once per analysed frame, so simultaneous hints and a tuner reading don't inflate it.
 */
internal fun countNotes(points: List<PitchPoint>, end: Float, a4: Int, window: Float = 10f): IntArray {
    val counts = IntArray(12)
    var frameTime = Float.NaN
    var seenNotes = 0
    for (point in points.asReversed()) {
        if (point.seconds > end) continue
        if (point.seconds < end - window) break
        if (point.seconds != frameTime) {
            frameTime = point.seconds
            seenNotes = 0
        }
        val hz = point.hz ?: continue
        val note = Math.floorMod(midi(hz, a4).roundToInt(), 12)
        val bit = 1 shl note
        if (seenNotes and bit == 0) {
            counts[note]++
            seenNotes = seenNotes or bit
        }
    }
    return counts
}
