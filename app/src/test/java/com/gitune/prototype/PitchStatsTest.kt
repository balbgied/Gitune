package com.gitune.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchStatsTest {
    private fun point(seconds: Float, hz: Float) = PitchPoint(seconds, hz)

    @Test fun countsEachPitchClassOncePerFrame() {
        val points = listOf(
            point(9.9f, 329.63f), // E
            point(10f, 146.83f), point(10f, 220f), point(10f, 146.83f), // D, A, duplicate D
            point(10.04f, 146.83f), point(10.04f, 220f) // D, A
        )
        val counts = countNotes(points, 10.04f, 440)
        assertEquals(2, counts[2]) // D
        assertEquals(2, counts[9]) // A
        assertEquals(1, counts[4]) // E
    }

    @Test fun ignoresPointsOutsideTheWindow() {
        val points = listOf(point(-1f, 146.83f), point(9f, 220f), point(11f, 329.63f))
        val counts = countNotes(points, 10f, 440)
        assertEquals(0, counts[2]) // too old
        assertEquals(1, counts[9])
        assertEquals(0, counts[4]) // still in the future
    }

    @Test fun silenceKeepsAllTwelveSlotsAtZero() {
        val counts = countNotes(emptyList(), 10f, 440)
        assertEquals(12, counts.size)
        assertEquals(List(12) { 0 }, counts.toList())
    }
}
