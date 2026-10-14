package com.independentmusictools.metronome.settings

import android.content.Context
import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.core.TempoMath

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
 * Темп, удары в такте, звук — независимые настройки, не перезаписывающие
 * друг друга.
 */
class MetronomeSettings(private val prefs: Prefs) {

    var bpm: Int
        get() = prefs.getInt(KEY_BPM, DEFAULT_BPM).coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM)
        set(value) = prefs.putInt(KEY_BPM, value.coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM))

    var beatsPerBar: Int
        get() = prefs.getInt(KEY_BEATS, DEFAULT_BEATS).coerceIn(MIN_BEATS_PER_BAR, MAX_BEATS_PER_BAR)
        set(value) = prefs.putInt(KEY_BEATS, value.coerceIn(MIN_BEATS_PER_BAR, MAX_BEATS_PER_BAR))

    var sound: ClickSynthesizer.Sound
        get() = prefs.getString(KEY_SOUND, null)
            ?.let { name -> ClickSynthesizer.Sound.entries.firstOrNull { it.name == name } }
            ?: ClickSynthesizer.Sound.WOOD
        set(value) = prefs.putString(KEY_SOUND, value.name)

    companion object {
        const val MIN_BEATS_PER_BAR = 1
        const val MAX_BEATS_PER_BAR = 8
        const val DEFAULT_BPM = 100
        const val DEFAULT_BEATS = 4

        private const val KEY_BPM = "bpm"
        private const val KEY_BEATS = "beats_per_bar"
        private const val KEY_SOUND = "sound"
    }
}
