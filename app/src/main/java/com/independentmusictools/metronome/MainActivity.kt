package com.independentmusictools.metronome

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.independentmusictools.metronome.audio.AudioTrackMetronomePlayer
import com.independentmusictools.metronome.settings.AppSettings
import com.independentmusictools.metronome.settings.MetronomeSettings
import com.independentmusictools.metronome.settings.SharedPrefsPrefs
import com.independentmusictools.metronome.state.MetronomeViewModel
import com.independentmusictools.metronome.ui.MetronomeScreen
import com.independentmusictools.metronome.ui.SettingsScreen
import com.independentmusictools.metronome.ui.theme.MetronomeTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Язык выбирается в настройках и не зависит от языка системы:
        // локаль подменяется до создания интерфейса.
        val tag = AppSettings(newBase).languageTag
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appSettings = remember { AppSettings(applicationContext) }
            var themeMode by remember { mutableStateOf(appSettings.themeMode) }

            MetronomeTheme(themeMode = themeMode) {
                var screen by remember { mutableStateOf(Screen.METRONOME) }
                val appContext = applicationContext
                val viewModel = remember {
                    MetronomeViewModel(
                        settings = MetronomeSettings(SharedPrefsPrefs(appContext)),
                        playerFactory = { onInterrupted ->
                            AudioTrackMetronomePlayer(appContext, onInterrupted)
                        },
                    )
                }

                // Политика воспроизведения: фоновый режим не поддерживается —
                // при уходе приложения из переднего плана останавливаем звук.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) viewModel.stopForBackground()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        viewModel.dispose()
                    }
                }

                when (screen) {
                    Screen.METRONOME -> MetronomeScreen(
                        viewModel = viewModel,
                        onOpenSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.SETTINGS -> {
                        // Системный «назад» в настройках возвращает к метроному.
                        BackHandler { screen = Screen.METRONOME }
                        SettingsScreen(
                            themeMode = themeMode,
                            languageTag = appSettings.languageTag,
                            onThemeChange = {
                                appSettings.themeMode = it
                                themeMode = it
                            },
                            onLanguageChange = {
                                appSettings.languageTag = it
                                // Язык применяется через пересоздание активити.
                                recreate()
                            },
                            onBack = { screen = Screen.METRONOME },
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { METRONOME, SETTINGS }
