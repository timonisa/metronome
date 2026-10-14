package com.independentmusictools.metronome.audio

import com.independentmusictools.metronome.core.AccentLevel
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickSynthesizerTest {

    private val rate = 44100

    private fun rms(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) {
            val v = s / 32767.0
            sum += v * v
        }
        return Math.sqrt(sum / samples.size)
    }

    /** Оценка доминирующей частоты по пересечениям нуля. */
    private fun dominantHz(samples: ShortArray): Double {
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i - 1] >= 0) != (samples[i] >= 0)) crossings++
        }
        val seconds = samples.size.toDouble() / rate
        return crossings / 2.0 / seconds
    }

    @Test
    fun `all sounds and accent levels produce non-silent output`() {
        for (sound in ClickSynthesizer.Sound.entries.filter { it.synthesized }) {
            for (level in AccentLevel.entries) {
                val c = ClickSynthesizer.synthesize(sound, level, rate)
                assertTrue("$sound/$level тихий: rms=${rms(c)}", rms(c) > 0.05)
            }
        }
    }

    @Test
    fun `perceived loudness is consistent across sounds`() {
        for (level in AccentLevel.entries) {
            val rmsValues = ClickSynthesizer.Sound.entries.filter { it.synthesized }
                .map { rms(ClickSynthesizer.synthesize(it, level, rate)) }
            val spread = rmsValues.max() - rmsValues.min()
            assertTrue("разброс громкости для $level: $spread", spread < 0.06)
        }
    }

    @Test
    fun `strong accent is louder than weak`() {
        for (sound in ClickSynthesizer.Sound.entries.filter { it.synthesized }) {
            val strong = rms(ClickSynthesizer.synthesize(sound, AccentLevel.STRONG, rate))
            val weak = rms(ClickSynthesizer.synthesize(sound, AccentLevel.WEAK, rate))
            assertTrue("$sound: strong=$strong weak=$weak", strong > weak)
        }
    }

    @Test
    fun `clicks have short controlled decay`() {
        for (sound in ClickSynthesizer.Sound.entries.filter { it.synthesized }) {
            val c = ClickSynthesizer.synthesize(sound, AccentLevel.STRONG, rate)
            val third = c.size / 3
            val head = rms(c.copyOfRange(0, third))
            val tail = rms(c.copyOfRange(2 * third, c.size))
            assertTrue("$sound нет затухания: head=$head tail=$tail", head > tail * 2)
        }
    }

    @Test
    fun `output stays within full scale without clipping`() {
        for (sound in ClickSynthesizer.Sound.entries.filter { it.synthesized }) {
            val c = ClickSynthesizer.synthesize(sound, AccentLevel.STRONG, rate)
            val peak = c.maxOf { abs(it.toInt()) }
            // допуск +1 — округление roundToInt может дать ровно на отсчёт больше
            assertTrue("$sound пик $peak превышает 0.95 FS", peak <= 0.95 * 32767 + 1)
        }
    }

    @Test
    fun `sounds are clearly distinguishable by dominant frequency`() {
        val freqs = ClickSynthesizer.Sound.entries.filter { it.synthesized }.associateWith {
            dominantHz(ClickSynthesizer.synthesize(it, AccentLevel.WEAK, rate))
        }
        val wood = freqs.getValue(ClickSynthesizer.Sound.WOOD)
        val tick = freqs.getValue(ClickSynthesizer.Sound.TICK)
        val pulse = freqs.getValue(ClickSynthesizer.Sound.PULSE)
        assertTrue("WOOD≈TICK: $wood / $tick", abs(wood - tick) / minOf(wood, tick) > 0.15)
        assertTrue("WOOD≈PULSE: $wood / $pulse", abs(wood - pulse) / minOf(wood, pulse) > 0.15)
        assertTrue("TICK≈PULSE: $tick / $pulse", abs(tick - pulse) / minOf(tick, pulse) > 0.15)
    }

    @Test
    fun `strong accent is pitched higher than weak`() {
        for (sound in ClickSynthesizer.Sound.entries.filter { it.synthesized }) {
            val strongHz = dominantHz(ClickSynthesizer.synthesize(sound, AccentLevel.STRONG, rate))
            val weakHz = dominantHz(ClickSynthesizer.synthesize(sound, AccentLevel.WEAK, rate))
            assertTrue("$sound: strong=$strongHz weak=$weakHz", strongHz > weakHz)
        }
    }

    @Test
    fun `total click length is short`() {
        // даже «низкий» тик должен укладываться в ~120 мс
        for (sound in ClickSynthesizer.Sound.entries.filter { it.synthesized }) {
            val c = ClickSynthesizer.synthesize(sound, AccentLevel.STRONG, rate)
            val ms = c.size * 1000.0 / rate
            assertTrue("$sound длительность ${ms}мс", ms < 120)
        }
    }
}
