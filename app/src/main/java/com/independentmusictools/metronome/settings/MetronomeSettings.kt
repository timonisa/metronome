package com.independentmusictools.metronome.settings

import android.content.Context
import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.core.TimeSignature

/**
 * Минимальный интерфейс хранилища настроек: реальная реализация —
 * SharedPreferences, в unit-тестах подменяется in-memory картой.
 */
interface Prefs {
    fun getInt(key: String, def: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, def: String?): String?
    fun putString(key: String, value: String)
}

class SharedPrefsPrefs(context: Context) : Prefs {
    private val prefs = context.getSharedPreferences("metronome_settings", Context.MODE_PRIVATE)
    override fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getString(key: String, def: String?): String? = prefs.getString(key, def)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}

/**
 * Локальные настройки метронома (только это устройство).
 * Темп, длина тренировочного цикла, размер, звук — независимые настройки,
 * не перезаписывающие друг друга.
 */
class MetronomeSettings(private val prefs: Prefs) {

    var bpm: Int
        get() = prefs.getInt(KEY_BPM, DEFAULT_BPM).coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM)
        set(value) = prefs.putInt(KEY_BPM, value.coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM))

    var cycleBeats: Int
        get() = prefs.getInt(KEY_CYCLE, DEFAULT_CYCLE).coerceIn(MIN_CYCLE_BEATS, MAX_CYCLE_BEATS)
        set(value) = prefs.putInt(KEY_CYCLE, value.coerceIn(MIN_CYCLE_BEATS, MAX_CYCLE_BEATS))

    var timeSignature: TimeSignature
        get() = TimeSignature.parse(prefs.getString(KEY_TS, null) ?: TimeSignature.DEFAULT.persistString)
            ?: TimeSignature.DEFAULT
        set(value) = prefs.putString(KEY_TS, value.persistString)

    var sound: ClickSynthesizer.Sound
        get() = prefs.getString(KEY_SOUND, null)
            ?.let { name -> ClickSynthesizer.Sound.entries.firstOrNull { it.name == name } }
            ?: ClickSynthesizer.Sound.WOOD
        set(value) = prefs.putString(KEY_SOUND, value.name)

    companion object {
        const val MIN_CYCLE_BEATS = 4
        const val MAX_CYCLE_BEATS = 36
        const val DEFAULT_BPM = 100
        const val DEFAULT_CYCLE = 8

        private const val KEY_BPM = "bpm"
        private const val KEY_CYCLE = "cycle_beats"
        private const val KEY_TS = "time_signature"
        private const val KEY_SOUND = "sound"
    }
}
