package com.gitune.prototype

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoDetectorTest {
    private fun feed(detector: TempoDetector, frames: Int, rms: (Int) -> Double, startFrame: Int = 0): Int? {
        var result: Int? = null
        for (i in 0 until frames) {
            val frame = startFrame + i
            result = detector.update(frame * 0.02f, rms(frame))
        }
        return result
    }

    @Test fun evenlySpacedClapsProduceTempo() {
        val detector = TempoDetector()
        val bpm = feed(detector, 400, { if (it % 25 == 0) 0.20 else 0.0 })
        assertEquals(120, bpm!!)
    }

    @Test fun steadyKickWithHiHatsFindsTheBeat() {
        val detector = TempoDetector()
        val bpm = feed(detector, 500, { i ->
            when {
                i % 25 == 0 -> 0.25 // kick every half second
                i % 12 == 0 -> 0.05 // quieter hi-hats between kicks
                else -> 0.0
            }
        })
        assertEquals(120, bpm!!)
    }

    @Test fun continuousBackgroundDoesNotHideTheBeat() {
        val detector = TempoDetector()
        val bpm = feed(detector, 500, { i -> 0.02 + if (i % 20 == 0) 0.20 else 0.0 })
        assertEquals(150, bpm!!)
    }

    @Test fun lowContrastDrumsProduceTempo() {
        val detector = TempoDetector()
        val bpm = feed(detector, 500, { if (it % 25 == 0) 0.115 else 0.10 })
        assertEquals(120, bpm!!)
    }

    @Test fun noiseDoesNotProduceTempo() {
        val detector = TempoDetector()
        val random = Random(5)
        val bpm = feed(detector, 400, { random.nextDouble() * 0.2 })
        assertNull(bpm)
    }

    @Test fun tempoChangeIsFollowed() {
        val detector = TempoDetector()
        feed(detector, 250, { if (it % 25 == 0) 0.20 else 0.0 })
        val bpm = feed(detector, 300, { if (it % 20 == 0) 0.20 else 0.0 }, startFrame = 250)
        assertEquals(150, bpm!!)
    }

    @Test fun silenceClearsTheLastTempo() {
        val detector = TempoDetector()
        feed(detector, 300, { if (it % 25 == 0) 0.20 else 0.0 })
        val bpm = feed(detector, 200, { 0.0 }, startFrame = 300)
        assertNull(bpm)
    }
}
