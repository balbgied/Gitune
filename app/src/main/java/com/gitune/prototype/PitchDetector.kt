package com.gitune.prototype

/** YIN-style period estimator. Input is normalized, uniformly sampled mono PCM. */
internal class PitchDetector(private val rate: Int) {
    private val maxLag = rate / 36
    private val minLag = rate / 1150
    private val difference = DoubleArray(maxLag + 2)
    private val rawDifference = DoubleArray(maxLag + 2)

    fun detect(samples: FloatArray): Float? {
        val length = samples.size - maxLag - 1
        if (length <= 0) return null
        var total = 0.0
        difference[0] = 1.0
        for (lag in 1..maxLag + 1) {
            var sum = 0.0
            for (i in 0 until length) {
                val delta = samples[i] - samples[i + lag]
                sum += delta * delta
            }
            rawDifference[lag] = sum
            total += sum
            difference[lag] = if (total > 0) sum * lag / total else 1.0
        }
        var lag = minLag
        while (lag < maxLag && difference[lag] >= 0.16) lag++
        if (lag >= maxLag) return null
        while (lag < maxLag && difference[lag + 1] < difference[lag]) lag++
        // Use the raw local shape for interpolation: the cumulative normalization
        // is useful for period selection but can skew the sub-sample estimate.
        val left = rawDifference[lag - 1]
        val center = rawDifference[lag]
        val right = rawDifference[lag + 1]
        val curvature = left - 2 * center + right
        val correction = if (curvature > 0) (0.5 * (left - right) / curvature).coerceIn(-1.0, 1.0) else 0.0
        return (rate / (lag + correction)).toFloat()
    }
}
