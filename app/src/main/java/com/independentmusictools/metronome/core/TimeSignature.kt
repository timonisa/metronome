package com.independentmusictools.metronome.core

import kotlin.math.log2

/**
 * Тактовый размер с акцентной группировкой.
 *
 * numerator/denominator + группы: например 7/8 = [2,2,3].
 * Группы задают, какие доли внутри такта акцентированы.
 * Чистый Kotlin, покрывается unit-тестами.
 */
data class TimeSignature(
    val numerator: Int,
    val denominator: Int,
    val groups: List<Int>,
) {
    init {
        require(numerator in 1..32) { "numerator должен быть 1..32: $numerator" }
        require(denominator in 1..64) { "denominator должен быть 1..64: $denominator" }
        require(groups.isNotEmpty() && groups.all { it > 0 }) { "группы должны быть положительными: $groups" }
        require(groups.sum() == numerator) { "сумма групп ($groups) должна равняться numerator ($numerator)" }
    }

    val label: String = "$numerator/$denominator"

    /** Начальные позиции групп внутри такта (0 — всегда сильная доля). */
    private val groupStarts: Set<Int> = buildSet {
        var acc = 0
        add(acc)
        for (g in groups.dropLast(1)) {
            acc += g
            add(acc)
        }
    }

    /**
     * Акцент доли [beatIndexInCycle] внутри тренировочного цикла длиной [cycleBeats].
     * Паттерн такта «плиткой» накрывает цикл любой длины: позиция в цикле
     * проецируется на позицию в такте по модулю numerator.
     */
    fun accentAt(beatIndexInCycle: Int, cycleBeats: Int): AccentLevel {
        require(cycleBeats > 0) { "cycleBeats должен быть положительным" }
        require(beatIndexInCycle >= 0) { "beatIndexInCycle должен быть неотрицательным" }
        val j = beatIndexInCycle % cycleBeats % numerator
        return when {
            j == 0 -> AccentLevel.STRONG
            j in groupStarts -> AccentLevel.MEDIUM
            else -> AccentLevel.WEAK
        }
    }

    companion object {
        val TWO_FOUR = TimeSignature(2, 4, listOf(2))
        val THREE_FOUR = TimeSignature(3, 4, listOf(3))
        val FOUR_FOUR = TimeSignature(4, 4, listOf(2, 2))
        val FIVE_FOUR = TimeSignature(5, 4, listOf(3, 2))
        val SIX_EIGHT = TimeSignature(6, 8, listOf(3, 3))
        val SEVEN_EIGHT = TimeSignature(7, 8, listOf(2, 2, 3))
        val NINE_EIGHT = TimeSignature(9, 8, listOf(3, 3, 3))
        val TWELVE_EIGHT = TimeSignature(12, 8, listOf(3, 3, 3, 3))
        val FIVE_EIGHT = TimeSignature(5, 8, listOf(2, 3))

        /** Все предустановленные размеры; список расширяется добавлением элемента. */
        val ALL = listOf(
            TWO_FOUR, THREE_FOUR, FOUR_FOUR, FIVE_FOUR,
            SIX_EIGHT, SEVEN_EIGHT, NINE_EIGHT, TWELVE_EIGHT, FIVE_EIGHT,
        )

        val DEFAULT = FOUR_FOUR

        /**
         * Разбор текста вида "6/8". Для известного размера возвращается
         * каноническая группировка; для прочих валидных — документированный
         * стандарт по умолчанию: кратные тройке восьмые группируются по 3,
         * всё остальное — одна группа на весь такт.
         */
        fun parse(text: String): TimeSignature? {
            val m = Regex("^(\\d+)/(\\d+)$").find(text.trim()) ?: return null
            val n = m.groupValues[1].toIntOrNull() ?: return null
            val d = m.groupValues[2].toIntOrNull() ?: return null
            if (n !in 1..32 || d !in 1..64) return null
            return ALL.firstOrNull { it.numerator == n && it.denominator == d }
                ?: defaultFor(n, d)
        }

        fun defaultFor(numerator: Int, denominator: Int): TimeSignature =
            if (denominator == 8 && numerator % 3 == 0) {
                TimeSignature(numerator, denominator, List(numerator / 3) { 3 })
            } else {
                TimeSignature(numerator, denominator, List(1) { numerator })
            }
    }
}

/** Уровень акцента доли. */
enum class AccentLevel { STRONG, MEDIUM, WEAK }
