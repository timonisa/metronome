package com.independentmusictools.metronome.audio

import com.independentmusictools.metronome.core.TimeSignature

/**
 * Плеер метронома. Интерфейс позволяет подменять реализацию в unit-тестах
 * ViewModel (AudioTrack требует устройства).
 */
interface MetronomePlayer {
    var bpm: Int
    var timeSignature: TimeSignature
    var sound: ClickSynthesizer.Sound

    val isRunning: Boolean

    fun start()
    fun stop()

    /** Одноразовое прослушивание звука (пара: сильная + обычная доля). */
    fun preview(sound: ClickSynthesizer.Sound)
}
