package com.independentmusictools.metronome.core

/**
 * Математика темпа и метра. Чистый Kotlin без зависимостей от Android,
 * покрывается unit-тестами.
 */
object TempoMath {

    const val MIN_BPM = 30
    const val MAX_BPM = 300

    /** Длительность доли в миллисекундах при данном темпе. */
    fun beatIntervalMs(bpm: Int): Double {
        require(bpm in MIN_BPM..MAX_BPM) { "bpm должно быть в $MIN_BPM..$MAX_BPM, получено $bpm" }
        return 60_000.0 / bpm
    }

    /** Индекс доли (0-based) на позиции [beat] в такте из [beatsPerBar] долей. */
    fun beatInBar(beat: Long, beatsPerBar: Int): Int {
        require(beatsPerBar > 0) { "beatsPerBar должно быть положительным" }
        return (beat % beatsPerBar).toInt()
    }

    /** Сильная ли это доля (первая доля такта). */
    fun isDownbeat(beat: Long, beatsPerBar: Int): Boolean =
        beatInBar(beat, beatsPerBar) == 0
}
