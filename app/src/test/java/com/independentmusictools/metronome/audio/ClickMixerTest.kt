package com.independentmusictools.metronome.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ClickMixerTest {

    private val windowSize = 100

    @Test
    fun `click fully inside window plays entirely in one window`() {
        val m = ClickMixer(windowSize, maxClickSamples = 50)
        val click = shortClick(length = 40, amplitude = 100)
        m.mix(10, click)
        val out = ShortArray(windowSize)
        m.takeWindow(out)
        assertEquals(0, out[9].toInt())
        for (i in 0 until 40) assertEquals(click[i], out[10 + i])
        assertEquals(0, out[50].toInt())
    }

    @Test
    fun `click crossing window boundary continues into next window`() {
        val m = ClickMixer(windowSize, maxClickSamples = 50)
        val click = shortClick(length = 50, amplitude = 100)
        m.mix(90, click) // в первое окно влезает только 10 сэмплов
        val first = ShortArray(windowSize)
        m.takeWindow(first)
        for (i in 0 until 10) assertEquals(click[i], first[90 + i])
        // хвост — в начале следующего окна
        val second = ShortArray(windowSize)
        m.takeWindow(second)
        for (i in 0 until 40) assertEquals(click[10 + i], second[i])
        assertEquals(0, second[40].toInt())
    }

    @Test
    fun `beat near very end of window is not lost`() {
        // доля на последнем сэмпле окна: в первом окне — 1 сэмпл, далее хвост
        val m = ClickMixer(windowSize, maxClickSamples = 50)
        val click = shortClick(length = 50, amplitude = 1000)
        m.mix(99, click)
        val first = ShortArray(windowSize)
        m.takeWindow(first)
        assertEquals(click[0], first[99])
        val second = ShortArray(windowSize)
        m.takeWindow(second)
        for (i in 0 until 49) assertEquals(click[1 + i], second[i])
    }

    @Test
    fun `overlapping clicks accumulate`() {
        val m = ClickMixer(windowSize, maxClickSamples = 50)
        val click = shortClick(length = 10, amplitude = 100)
        m.mix(0, click)
        m.mix(0, click)
        val out = ShortArray(windowSize)
        m.takeWindow(out)
        assertEquals(200, out[0].toInt())
    }

    @Test
    fun `accumulation saturates instead of overflowing`() {
        val m = ClickMixer(windowSize, maxClickSamples = 50)
        val click = shortClick(length = 5, amplitude = 30000)
        repeat(10) { m.mix(0, click) }
        val out = ShortArray(windowSize)
        m.takeWindow(out)
        assertEquals(32767, out[0].toInt())
    }

    @Test
    fun `click exceeding tail zone is truncated at accumulator bound`() {
        // окно 100 + зона хвоста 20 = аккумулятор 120:
        // щелчок длиной 60 с offset 90 занимает позиции 90..119 и усекается
        val m = ClickMixer(windowSize, maxClickSamples = 20)
        val click = shortClick(length = 60, amplitude = 100)
        m.mix(90, click)
        val first = ShortArray(windowSize)
        m.takeWindow(first)
        for (i in 0 until 10) assertEquals(click[i], first[90 + i])
        val second = ShortArray(windowSize)
        m.takeWindow(second)
        for (i in 0 until 20) assertEquals(click[10 + i], second[i])
        assertEquals(0, second[20].toInt())
        val third = ShortArray(windowSize)
        m.takeWindow(third)
        assertEquals(0, third[0].toInt())
    }

    @Test
    fun `reset clears pending tail`() {
        val m = ClickMixer(windowSize, maxClickSamples = 50)
        m.mix(90, shortClick(50, 100))
        m.reset()
        val second = ShortArray(windowSize)
        m.takeWindow(second)
        assertEquals(0, second[0].toInt())
    }

    private fun shortClick(length: Int, amplitude: Int) =
        ShortArray(length) { (amplitude * (if (it % 2 == 0) 1 else -1)).toShort() }
}
