package com.independentmusictools.metronome.audio

/**
 * Потоковый микшер щелчков по окнам фиксированной длины. Чистый Kotlin,
 * тестируется unit-тестами.
 *
 * Ключевое свойство: щелчок длиннее окна НЕ обрезается — его «хвост»
 * переносится в начало следующего окна. Без этого доли, попадающие
 * близко к концу окна, звучат как крошечный огрызок атаки или молчат.
 *
 * @param windowSize размер окна в сэмплах
 * @param maxClickSamples верхняя граница длины щелчка; всё, что длиннее,
 *   корректно усекается (и не переносится за пределы зоны хвоста)
 */
class ClickMixer(
    private val windowSize: Int,
    maxClickSamples: Int,
) {
    init {
        require(windowSize > 0) { "windowSize должен быть положительным" }
        require(maxClickSamples > 0) { "maxClickSamples должен быть положительным" }
    }

    private val acc = IntArray(windowSize + maxClickSamples)

    /** Примиксовать щелчок к текущему окну (и, при выходе за границу, в зону хвоста). */
    fun mix(offsetInWindow: Int, click: ShortArray) {
        var p = offsetInWindow
        var i = 0
        while (p < 0 && i < click.size) { p++; i++ } // защита; по контракту offset >= 0
        while (i < click.size && p < acc.size) {
            acc[p] = (acc[p] + click[i]).coerceIn(-32768, 32767)
            p++; i++
        }
    }

    /**
     * Забрать готовое окно, сдвинуть хвост в начало аккумулятора
     * и очистить освободившуюся зону.
     */
    fun takeWindow(out: ShortArray) {
        require(out.size == windowSize) { "out должен иметь размер окна ($windowSize)" }
        for (i in 0 until windowSize) out[i] = acc[i].toShort()
        val tailLen = acc.size - windowSize
        System.arraycopy(acc, windowSize, acc, 0, tailLen)
        java.util.Arrays.fill(acc, tailLen, acc.size, 0)
    }

    fun reset() = java.util.Arrays.fill(acc, 0)
}
