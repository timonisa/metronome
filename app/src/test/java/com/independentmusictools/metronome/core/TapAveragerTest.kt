package com.independentmusictools.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapAveragerTest {

    @Test
    fun `single tap yields no bpm`() {
        val t = TapAverager(now = { 1000L })
        assertNull(t.tap())
    }

    @Test
    fun `two taps 500ms apart give 120 bpm`() {
        var clock = 0L
        val t = TapAverager(now = { clock })
        t.tap()
        clock = 500
        assertEquals(120, t.tap())
    }

    @Test
    fun `averages several taps`() {
        var clock = 0L
        val t = TapAverager(now = { clock })
        t.tap()
        clock = 600
        t.tap()
        clock = 1200
        t.tap()
        clock = 1800
        // средний интервал 600 мс → 100 BPM
        assertEquals(100, t.tap())
    }

    @Test
    fun `long gap resets sequence`() {
        var clock = 0L
        val t = TapAverager(now = { clock })
        t.tap()
        clock = 500
        t.tap()
        clock = 500 + TapAverager.RESET_MS + 1
        // после сброса снова нужен минимум один интервал
        assertNull(t.tap())
    }

    @Test
    fun `bpm clamped to valid range`() {
        var clock = 0L
        val t = TapAverager(now = { clock })
        t.tap()
        clock = 10 // 100 мс между тапами = 600 BPM → клампится в максимум
        assertEquals(TempoMath.MAX_BPM, t.tap())
    }

    @Test
    fun `reset forgets history`() {
        var clock = 0L
        val t = TapAverager(now = { clock })
        t.tap()
        clock = 500
        t.tap()
        t.reset()
        clock = 600
        assertNull(t.tap())
    }
}
