package com.gitune.prototype

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePitchTrackerTest {
    private val tracker = LivePitchTracker(8000, 800)

    private fun tone(hz: Double, sample: Int) = sin(2 * PI * hz * sample / 8000)

    private fun detect(samples: FloatArray): LivePitch {
        val mean = samples.average()
        val rms = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size)
        return tracker.detect(samples, rms)
    }

    @Test fun quietLowNoteReachesBothViews() {
        val pitch = detect(FloatArray(800) { (0.00027 * tone(65.406, it)).toFloat() })
        assertEquals(65.406, pitch.tunerHz!!.toDouble(), 0.2)
        assertTrue(pitch.jamHints.isEmpty())
    }

    @Test fun mixedNotesProduceJamEstimatesAtDifferentLevels() {
        for (gain in listOf(0.002, 0.3)) {
            val pitch = detect(FloatArray(800) {
                (gain * (tone(146.83, it) + 0.76 * tone(196.0, it) + 0.69 * tone(311.0, it) + 0.64 * tone(466.0, it))).toFloat()
            })
            assertNull(pitch.tunerHz)
            assertEquals(3, pitch.jamHints.size)
            for ((expected, actual) in listOf(146.83, 196.0, 311.0).zip(pitch.jamHints)) {
                assertEquals(expected, actual.toDouble(), 3.0)
            }
        }
    }

    @Test fun overtonesAreShownEvenWhenTheTunerLocksOntoTheFundamental() {
        val pitch = detect(FloatArray(800) {
            (0.25 * tone(110.0, it) + 0.20 * tone(220.0, it) + 0.15 * tone(330.0, it)).toFloat()
        })
        assertEquals(110.0, pitch.tunerHz!!.toDouble(), 0.2)
        assertEquals(2, pitch.jamHints.size)
        assertEquals(220.0, pitch.jamHints[0].toDouble(), 1.0)
        assertEquals(330.0, pitch.jamHints[1].toDouble(), 1.0)
    }

    @Test fun loudRecentInputRaisesTheGateWithoutBlockingEveryQuietNote() {
        val loud = FloatArray(800) { (0.5 * tone(220.0, it)).toFloat() }
        repeat(20) { detect(loud) }
        assertEquals(LivePitch(), detect(FloatArray(800) { (0.00027 * tone(65.406, it)).toFloat() }))
        assertEquals(65.406, detect(FloatArray(800) { (0.004 * tone(65.406, it)).toFloat() }).tunerHz!!.toDouble(), 0.2)
        repeat(150) { detect(FloatArray(800)) }
        assertEquals(65.406, detect(FloatArray(800) { (0.00027 * tone(65.406, it)).toFloat() }).tunerHz!!.toDouble(), 0.2)
    }

    @Test fun silenceAndBroadbandNoiseAreNotShownAsNotes() {
        assertEquals(LivePitch(), detect(FloatArray(800)))
        val random = Random(9)
        val results = List(100) { detect(FloatArray(800) { (random.nextFloat() - 0.5f) * 0.5f }) }
        assertTrue(results.all { it.tunerHz == null })
        assertTrue(results.count { it.jamHints.isNotEmpty() } <= 2)
        assertTrue(results.all { result -> result.jamHints.all { it.isFinite() } })
    }

    @Test fun constantInputIsNotANote() {
        val pitch = detect(FloatArray(800) { 0.3f })
        assertEquals(LivePitch(), pitch)
    }

    @Test fun dcOffsetDoesNotShiftTheDetectedPitch() {
        val pitch = detect(FloatArray(800) { (0.4 + 0.25 * tone(220.0, it)).toFloat() })
        assertEquals(220.0, pitch.tunerHz!!.toDouble(), 0.2)
        assertTrue(pitch.jamHints.isEmpty())
    }

    @Test fun highNotesNearTheTopOfTheRangeAreDetected() {
        val pitch = detect(FloatArray(800) { (0.2 * tone(1000.0, it)).toFloat() })
        assertEquals(1000.0, pitch.tunerHz!!.toDouble(), 1.0)
        assertTrue(pitch.jamHints.isEmpty())
    }

    @Test fun noisySingleNoteReachesTheTunerAfterConfirmation() {
        val random = Random(11)
        val samples = FloatArray(800) { (0.20 * tone(220.0, it) + 0.50 * (random.nextFloat() - 0.5)).toFloat() }
        val results = List(6) { detect(samples) }
        assertNull(results[0].tunerHz)
        assertNull(results[1].tunerHz)
        assertEquals(220.0, results[2].tunerHz!!.toDouble(), 3.0)
        assertEquals(220.0, results[5].tunerHz!!.toDouble(), 3.0)
    }
}
