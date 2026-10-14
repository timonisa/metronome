package com.independentmusictools.metronome.settings

import android.content.Context
import android.content.SharedPreferences

/** Тема оформления. */
enum class ThemeMode {
    LIGHT,
    DARK;

    companion object {
        fun from(raw: String?): ThemeMode = if (raw == DARK.name) DARK else LIGHT
    }
}

/**
 * Настройки приложения (SharedPreferences, только это устройство):
 * тема оформления и язык интерфейса. По умолчанию язык — русский,
 * независимо от языка системы (как в tuner).
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("metronome_app_settings", Context.MODE_PRIVATE)

    /** Тема оформления. */
    var themeMode: ThemeMode
        get() = ThemeMode.from(prefs.getString(KEY_THEME, null))
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    /** Язык интерфейса: ISO-код ("ru", "en", "es", "de", "zh"). */
    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, null) ?: DEFAULT_LANGUAGE
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language_tag"
        const val DEFAULT_LANGUAGE = "ru"
    }
}
