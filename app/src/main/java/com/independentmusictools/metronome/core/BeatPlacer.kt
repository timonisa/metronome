package com.independentmusictools.metronome.core

/**
 * Расчёт позиций долей внутри аудио-окна. Чистый Kotlin, тестируется unit-тестами.
 *
 * Моменты долей задаются в сэмплах от начала воспроизведения
 * (beatSample[i] = i * samplesPerBeat). Дробная часть сэмпла округляется —
 * накопления ошибки нет, т.к. позиция считается от абсолютного индекса доли,
 * а не инкрементально.
 */
object BeatPlacer {

    /**
     * Доли, попадающие в полуинтервал [windowStart, windowEnd) по сэмплам.
     *
     * @param windowStart абсолютный индекс первого сэмпла окна
     * @param windowEnd абсолютный индекс за последним сэмплом окна
     * @param samplesPerBeat длительность доли в сэмплах (дробная)
     * @param beatsPerBar долей в такте (для флага сильной доли)
     */
    fun beatsInWindow(
        windowStart: Long,
        windowEnd: Long,
        samplesPerBeat: Double,
        beatsPerBar: Int,
    ): List<PlacedBeat> {
        val out = ArrayList<PlacedBeat>()
        fillInto(out, windowStart, windowEnd, samplesPerBeat, beatsPerBar)
        return out
    }

    /**
     * То же, но без аллокаций: заполняет переиспользуемый список [out]
     * (очищает его). Используется в аудио-потоке.
     */
    fun fillInto(
        out: MutableList<PlacedBeat>,
        windowStart: Long,
        windowEnd: Long,
        samplesPerBeat: Double,
        beatsPerBar: Int,
    ) {
        out.clear()
        require(windowEnd >= windowStart) { "окно нулевой или отрицательной длины" }
        require(samplesPerBeat > 0) { "samplesPerBeat должен быть положительным" }
        require(beatsPerBar > 0) { "beatsPerBar должен быть положительным" }
        if (windowEnd <= windowStart) return

        val firstBeat = Math.ceil(windowStart / samplesPerBeat).toInt().coerceAtLeast(0)
        var i = firstBeat
        while (true) {
            val pos = i * samplesPerBeat
            if (pos >= windowEnd) break
            if (pos >= windowStart) {
                out.add(PlacedBeat(i.toLong(), Math.round(pos).toLong() - windowStart, i % beatsPerBar == 0))
            }
            i++
        }
    }

    data class PlacedBeat(val beatIndex: Long, val offsetInWindow: Long, val isDownbeat: Boolean)
}
