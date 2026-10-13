package com.independentmusictools.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatPlacerTest {

    // 60 BPM при 44100 Гц: ровно 44100 сэмплов на долю
    private val spb = 44100.0

    @Test
    fun `beats land on exact sample positions at 60 bpm`() {
        val beats = BeatPlacer.beatsInWindow(0, 88200, spb, beatsPerBar = 4)
        assertEquals(listOf(0L, 1L), beats.map { it.beatIndex })
        assertEquals(listOf(0L, 44100L), beats.map { it.offsetInWindow })
    }

    @Test
    fun `first beat of bar is downbeat`() {
        // окно в шесть долей: бары 3/4 → сильные на 1-й и 4-й доле
        val beats = BeatPlacer.beatsInWindow(0, 6 * 44100, spb, beatsPerBar = 3)
        assertEquals(listOf(true, false, false, true, false, false), beats.map { it.isDownbeat })
    }

    @Test
    fun `window starting mid-beat does not duplicate beats`() {
        // окно [22050, 66150): внутри только доля 1 (на 44100)
        val beats = BeatPlacer.beatsInWindow(22050, 66150, spb, beatsPerBar = 4)
        assertEquals(listOf(1L), beats.map { it.beatIndex })
        assertEquals(listOf(22050L), beats.map { it.offsetInWindow })
    }

    @Test
    fun `fractional samples per beat stay stable over long time`() {
        // 103 BPM: 25689,32 сэмпла на долю; окно начинается ровно на доле 1000.
        // Позиции считаются от абсолютного индекса — дрейфа нет.
        val samplesPerBeat = 44100.0 * 60 / 103
        val lateWindowStart = (samplesPerBeat * 1000).toLong()
        val beats = BeatPlacer.beatsInWindow(lateWindowStart, lateWindowStart + 26460, samplesPerBeat, 4)
        val expected1001Offset = Math.round(1001 * samplesPerBeat).toLong() - lateWindowStart
        assertEquals(listOf(1000L, 1001L), beats.map { it.beatIndex })
        assertEquals(listOf(0L, expected1001Offset), beats.map { it.offsetInWindow })
    }

    @Test
    fun `empty window returns no beats`() {
        assertTrue(BeatPlacer.beatsInWindow(1000, 1000, spb, 4).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative window rejected`() {
        BeatPlacer.beatsInWindow(100, 50, spb, 4)
    }

    @Test
    fun `fillInto matches beatsInWindow and reuses list`() {
        val a = BeatPlacer.beatsInWindow(0, 132300, spb, 4)
        val reused = ArrayList<BeatPlacer.PlacedBeat>()
        BeatPlacer.fillInto(reused, 0, 132300, spb, 4)
        assertEquals(a, reused)
        // повторный вызов не накапливает элементы
        BeatPlacer.fillInto(reused, 0, 44100, spb, 4)
        assertEquals(1, reused.size)
    }
}
