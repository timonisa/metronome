package com.independentmusictools.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoMathTest {

    @Test
    fun `60 bpm means one beat per second`() {
        assertEquals(1000.0, TempoMath.beatIntervalMs(60), 1e-9)
    }

    @Test
    fun `120 bpm means half a second per beat`() {
        assertEquals(500.0, TempoMath.beatIntervalMs(120), 1e-9)
    }

    @Test
    fun `range extends to 300 bpm`() {
        assertEquals(200.0, TempoMath.beatIntervalMs(300), 1e-9)
    }

    @Test
    fun `range starts at 30 bpm`() {
        assertEquals(2000.0, TempoMath.beatIntervalMs(30), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bpm below range is rejected`() {
        TempoMath.beatIntervalMs(10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bpm above range is rejected`() {
        TempoMath.beatIntervalMs(301)
    }

    @Test
    fun `beat position wraps inside the bar`() {
        assertEquals(0, TempoMath.beatInBar(0, 4))
        assertEquals(3, TempoMath.beatInBar(3, 4))
        assertEquals(0, TempoMath.beatInBar(4, 4))
        assertEquals(2, TempoMath.beatInBar(10, 4))
    }

    @Test
    fun `only the first beat of each bar is a downbeat`() {
        assertTrue(TempoMath.isDownbeat(0, 3))
        assertTrue(TempoMath.isDownbeat(9, 3))
        assertFalse(TempoMath.isDownbeat(1, 3))
        assertFalse(TempoMath.isDownbeat(8, 3))
    }
}
