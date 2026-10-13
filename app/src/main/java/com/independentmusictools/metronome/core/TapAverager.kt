package com.independentmusictools.metronome.core

/**
 * Усреднение тапов для определения темпа (tap tempo).
 * Чистый Kotlin, тестируется unit-тестами.
 */
class TapAverager(
    private val maxTaps: Int = 5,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val times = ArrayDeque<Long>()

    /**
     * Зарегистрировать тап.
     * @return темп BPM по среднему интервалу последних тапов, или null, если интервалов ещё нет.
     */
    fun tap(): Int? {
        val t = now()
        if (times.isNotEmpty() && t - times.last() > RESET_MS) times.clear()
        times.addLast(t)
        if (times.size > maxTaps) times.removeFirst()
        if (times.size < 2) return null
        val span = times.last() - times.first()
        val intervals = times.size - 1
        val avgMs = span.toDouble() / intervals
        if (avgMs <= 0) return null
        return (60_000.0 / avgMs).toInt().coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM)
    }

    /** Забыть все тапы (например, при остановке метронома). */
    fun reset() = times.clear()

    companion object {
        /** Пауза между тапами, после которой считаем последовательность начатой заново. */
        const val RESET_MS = 2500L
    }
}
