package com.gitune.prototype

import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TEMPO_FRAME = 0.02f
private const val TEMPO_HISTORY = 320 // 6.4 seconds of onsets
private const val TEMPO_MIN_HISTORY = 100 // need at least two seconds before guessing
private const val TEMPO_MIN_LAG = 14 // 0.28 s, about 214 BPM
private const val TEMPO_MAX_LAG = 60 // 1.20 s, about 50 BPM
private const val TEMPO_MIN_SCORE = 0.20f
private const val TEMPO_MIN_ONSET = 0.0006f
private const val TEMPO_ANALYSIS_EVERY = 5

/** Estimates tempo by autocorrelating attacks in ~20 ms blocks of full-band RMS. */
internal class TempoDetector {
    private val onsets = FloatArray(TEMPO_HISTORY)
    private val scores = FloatArray(TEMPO_MAX_LAG - TEMPO_MIN_LAG + 1)
    private var count = 0
    private var smoothed = 0.0
    private var lastSeconds = Float.NaN
    private var lastOnsetSeconds = Float.NaN
    private var bpm: Int? = null
    private var candidate: Int? = null
    private var candidateFrames = 0
    private var misses = 0
    private var tick = 0

    fun reset() {
        count = 0
        smoothed = 0.0
        lastSeconds = Float.NaN
        lastOnsetSeconds = Float.NaN
        bpm = null
        candidate = null
        candidateFrames = 0
        misses = 0
        tick = 0
    }

    fun update(seconds: Float, rms: Double): Int? {
        if (lastSeconds.isNaN() || seconds < lastSeconds || seconds - lastSeconds > 0.1f) reset()
        lastSeconds = seconds
        // A slow follower turns sustained loudness into a flat baseline, so only
        // rises (drum hits, claps, syllables) enter the correlation.
        val attack = (rms - smoothed).coerceAtLeast(0.0)
        smoothed += (rms - smoothed) * 0.12
        if (attack > TEMPO_MIN_ONSET) lastOnsetSeconds = seconds
        // Once the music stops, old onsets must not keep the last BPM alive.
        if (!lastOnsetSeconds.isNaN() && seconds - lastOnsetSeconds > 1.5f) {
            bpm = null
            candidate = null
            candidateFrames = 0
            misses = 0
            count = 0
        }
        if (count == TEMPO_HISTORY) {
            onsets.copyInto(onsets, 0, 1)
            count = TEMPO_HISTORY - 1
        }
        onsets[count++] = attack.toFloat()
        if (++tick % TEMPO_ANALYSIS_EVERY != 0) return bpm

        val estimate = estimate()
        if (estimate == null) {
            if (++misses >= 5) {
                bpm = null
                candidate = null
                candidateFrames = 0
            }
        } else {
            misses = 0
            if (candidate == estimate) candidateFrames++ else {
                candidate = estimate
                candidateFrames = 1
            }
            if (candidateFrames >= 3) bpm = candidate
        }
        return bpm
    }

    private fun estimate(): Int? {
        if (count < TEMPO_MIN_HISTORY) return null
        var loudest = 0f
        for (i in 0 until count) loudest = maxOf(loudest, onsets[i])
        if (loudest < TEMPO_MIN_ONSET) return null

        var bestLag = -1
        var bestScore = 0f
        for (index in scores.indices) {
            val lag = TEMPO_MIN_LAG + index
            val samples = count - lag
            var sumX = 0f
            var sumY = 0f
            var sumXY = 0f
            var sumXX = 0f
            var sumYY = 0f
            for (i in 0 until samples) {
                val x = onsets[i]
                val y = onsets[i + lag]
                sumX += x
                sumY += y
                sumXY += x * y
                sumXX += x * x
                sumYY += y * y
            }
            // Pearson correlation: a positive baseline would otherwise make every lag look alike.
            val covariance = sumXY - sumX * sumY / samples
            val varianceX = sumXX - sumX * sumX / samples
            val varianceY = sumYY - sumY * sumY / samples
            val score = if (varianceX > 0f && varianceY > 0f) covariance / sqrt(varianceX * varianceY) else 0f
            scores[index] = score
            // Strictly greater keeps the shorter lag when a pattern repeats at 2x.
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestScore < TEMPO_MIN_SCORE) return null

        // Percussion often correlates at double or half speed. Canonicalise towards
        // the centre of the musical range when the neighbouring period is nearly as strong.
        var chosenLag = bestLag
        var chosenScore = bestScore
        while (60f / (chosenLag * TEMPO_FRAME) > 155f && chosenLag * 2 <= TEMPO_MAX_LAG) {
            val slower = scores[chosenLag * 2 - TEMPO_MIN_LAG]
            if (slower < chosenScore * 0.70f) break
            chosenLag *= 2
            chosenScore = slower
        }
        while (60f / (chosenLag * TEMPO_FRAME) < 80f && chosenLag / 2 >= TEMPO_MIN_LAG) {
            val faster = scores[chosenLag / 2 - TEMPO_MIN_LAG]
            if (faster < chosenScore * 0.70f) break
            chosenLag /= 2
            chosenScore = faster
        }

        val index = chosenLag - TEMPO_MIN_LAG
        val left = if (index > 0) scores[index - 1] else 0f
        val right = if (index < scores.lastIndex) scores[index + 1] else 0f
        val curvature = left - 2 * chosenScore + right
        val shift = if (curvature > 1e-6f) (0.5f * (left - right) / curvature).coerceIn(-1f, 1f) else 0f
        return (60f / ((chosenLag + shift) * TEMPO_FRAME)).roundToInt().coerceIn(40, 240)
    }
}
