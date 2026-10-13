package com.independentmusictools.metronome.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSignatureTest {

    @Test
    fun `all preset signatures parse from their labels`() {
        for (ts in TimeSignature.ALL) {
            assertEquals(ts, TimeSignature.parse(ts.label))
        }
    }

    @Test
    fun `groups sum to numerator for all presets`() {
        for (ts in TimeSignature.ALL) {
            assertEquals(ts.numerator, ts.groups.sum())
        }
    }

    @Test
    fun `invalid labels return null`() {
        assertNull(TimeSignature.parse("4-4"))
        assertNull(TimeSignature.parse("xy"))
        assertNull(TimeSignature.parse(""))
        assertNull(TimeSignature.parse("4//8"))
        assertNull(TimeSignature.parse("0/4"))
        assertNull(TimeSignature.parse("4/0"))
    }

    @Test
    fun `four four accents first and third beat`() {
        // 4/4 = [2,2]: сильная 1, средняя 3
        assertEquals(AccentLevel.STRONG, TimeSignature.FOUR_FOUR.accentAt(0, 4))
        assertEquals(AccentLevel.WEAK, TimeSignature.FOUR_FOUR.accentAt(1, 4))
        assertEquals(AccentLevel.MEDIUM, TimeSignature.FOUR_FOUR.accentAt(2, 4))
        assertEquals(AccentLevel.WEAK, TimeSignature.FOUR_FOUR.accentAt(3, 4))
    }

    @Test
    fun `six eight accents two dotted-quarter groups`() {
        // 6/8 = [3,3]: сильная 1, средняя 4
        val expected = listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK, AccentLevel.WEAK)
        for (i in 0 until 6) {
            assertEquals("доля ${i + 1}", expected[i], TimeSignature.SIX_EIGHT.accentAt(i, 6))
        }
    }

    @Test
    fun `seven eight groups two two three`() {
        // 7/8 = [2,2,3]: сильная 1, средние 3 и 5
        val expected = listOf(
            AccentLevel.STRONG, AccentLevel.WEAK,
            AccentLevel.MEDIUM, AccentLevel.WEAK,
            AccentLevel.MEDIUM, AccentLevel.WEAK, AccentLevel.WEAK,
        )
        for (i in 0 until 7) {
            assertEquals(expected[i], TimeSignature.SEVEN_EIGHT.accentAt(i, 7))
        }
    }

    @Test
    fun `twelve eight has four groups of three`() {
        // 12/8 = 4×3: сильная 1, средние 4, 7, 10
        for (i in 0 until 12) {
            val expected = when (i) {
                0 -> AccentLevel.STRONG
                3, 6, 9 -> AccentLevel.MEDIUM
                else -> AccentLevel.WEAK
            }
            assertEquals("доля ${i + 1}", expected, TimeSignature.TWELVE_EIGHT.accentAt(i, 12))
        }
    }

    @Test
    fun `accent pattern tiles over longer cycle`() {
        // Цикл 10 долей с 7/8: паттерн повторяется каждые 7 долей
        val ts = TimeSignature.SEVEN_EIGHT
        assertEquals(AccentLevel.STRONG, ts.accentAt(7, 10)) // 7 % 7 == 0
        assertEquals(AccentLevel.MEDIUM, ts.accentAt(9, 10)) // 9 % 7 == 2 — старт группы
        assertEquals(AccentLevel.WEAK, ts.accentAt(8, 10))
    }

    @Test
    fun `accent pattern truncates over shorter cycle`() {
        // Цикл 4 доли с 12/8: j = i % 12 → 0..3; старты групп 12/8: 0,3,6,9
        val ts = TimeSignature.TWELVE_EIGHT
        assertEquals(AccentLevel.STRONG, ts.accentAt(0, 4))
        assertEquals(AccentLevel.WEAK, ts.accentAt(1, 4))
        assertEquals(AccentLevel.WEAK, ts.accentAt(2, 4))
        assertEquals(AccentLevel.MEDIUM, ts.accentAt(3, 4))
    }

    @Test
    fun `cycle boundaries keep first beat strong`() {
        val ts = TimeSignature.THREE_FOUR
        assertEquals(AccentLevel.STRONG, ts.accentAt(0, 36))
        assertEquals(AccentLevel.STRONG, ts.accentAt(3, 4))
        assertEquals(AccentLevel.STRONG, ts.accentAt(36, 36))
    }

    @Test
    fun `unknown signature falls back to documented default`() {
        // 11/8 не из списка: 11 не кратно 3 → одна группа на весь такт
        val ts = TimeSignature.parse("11/8")
        assertEquals(TimeSignature(11, 8, listOf(11)), ts)
        assertEquals(AccentLevel.STRONG, ts!!.accentAt(0, 11))
        assertEquals(AccentLevel.WEAK, ts.accentAt(5, 11))

        // 15/8: кратно 3 → группы по 3
        val compound = TimeSignature.parse("15/8")
        assertEquals(TimeSignature(15, 8, List(5) { 3 }), compound)
        assertEquals(AccentLevel.MEDIUM, compound!!.accentAt(3, 15))
    }

    @Test
    fun `five four is three plus two`() {
        // 5/4 = [3,2]: сильная 1, средняя 4
        val ts = TimeSignature.FIVE_FOUR
        assertEquals(AccentLevel.STRONG, ts.accentAt(0, 5))
        assertEquals(AccentLevel.MEDIUM, ts.accentAt(3, 5))
        assertFalse(ts.accentAt(1, 5) == AccentLevel.STRONG)
        assertTrue(ts.groups == listOf(3, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `groups must sum to numerator`() {
        TimeSignature(4, 4, listOf(2, 3))
    }
}
