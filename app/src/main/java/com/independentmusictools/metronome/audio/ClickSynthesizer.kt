package com.independentmusictools.metronome.audio

import com.independentmusictools.metronome.core.AccentLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Синтез щелчков метронома без внешних сэмплов — чистый Kotlin,
 * тестируется unit-тестами. Три звука генерируются локально
 * (затухающие синусы с парой частотных компонентов), поэтому
 * вопросов лицензий сэмплов нет.
 *
 * MECH — сэмпловый звук реального механического метронома
 * (см. SampleClicks), синтезу не подлежит.
 *
 * Громкость нормализуется по RMS к целевому уровню для каждого акцента —
 * воспринимаемая громкость звуков сопоставима.
 */
object ClickSynthesizer {

    enum class Sound(val label: String, val synthesized: Boolean = true) {
        WOOD("Дерево"),   // короткий щелчок дерева
        TICK("Тик"),      // тёплый сухой механический тик
        PULSE("Импульс"), // резкий электронный пульс
        MECH("Механика", synthesized = false), // сэмпл реального метронома
    }

    /**
     * Сгенерировать щелчок в PCM 16 бит.
     *
     * @param level уровень акцента: STRONG выше частотой и громче WEAK.
     */
    fun synthesize(sound: Sound, level: AccentLevel, sampleRate: Int): ShortArray {
        require(sound.synthesized) { "звук $sound сэмпловый — используйте SampleClicks" }
        val partials: List<Pair<Double, Double>> = when (sound) { // (частота Гц, вес)
            Sound.WOOD -> listOf(850.0 to 1.0, 2300.0 to 0.30)
            Sound.TICK -> listOf(700.0 to 1.0, 1400.0 to 0.10)
            Sound.PULSE -> listOf(1450.0 to 1.0, 2200.0 to 0.45)
            Sound.MECH -> error("недостижимо — проверено require выше")
        }
        val decaySec = when (sound) {
            Sound.WOOD -> 0.030
            Sound.TICK -> 0.020
            Sound.PULSE -> 0.026
            Sound.MECH -> error("недостижимо — проверено require выше")
        }
        val targetRms = when (level) {
            AccentLevel.STRONG -> 0.30
            AccentLevel.WEAK -> 0.19
        }
        val freqScale = if (level == AccentLevel.STRONG) 1.22 else 1.0

        val length = (decaySec * 3.4 * sampleRate).toInt().coerceAtLeast(64)
        val attack = (0.0004 * sampleRate).toInt().coerceAtLeast(2) // плавная атака против щелчка-«ступеньки»
        val weightSum = partials.sumOf { it.second }

        val f = FloatArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / sampleRate
            var v = 0.0
            for ((freqHz, w) in partials) {
                v += w * sin(2.0 * PI * freqHz * freqScale * t)
            }
            v = v / weightSum * exp(-t / decaySec)
            if (i < attack) v *= i.toDouble() / attack
            f[i] = v.toFloat()
        }

        // RMS-нормализация громкости с ограничением пика.
        var sumSq = 0.0
        for (s in f) sumSq += s * s
        val rms = sqrt(sumSq / f.size).coerceAtLeast(1e-9)
        var scale = targetRms / rms
        var peak = 0.0
        for (s in f) peak = maxOf(peak, abs(s.toDouble()))
        if (peak * scale > 0.95) scale = 0.95 / peak

        return ShortArray(f.size) {
            (f[it] * scale * 32767.0).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
