package com.independentmusictools.metronome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.independentmusictools.metronome.audio.AudioTrackMetronomePlayer
import com.independentmusictools.metronome.settings.MetronomeSettings
import com.independentmusictools.metronome.settings.SharedPrefsPrefs
import com.independentmusictools.metronome.state.MetronomeViewModel
import com.independentmusictools.metronome.ui.MetronomeScreen
import com.independentmusictools.metronome.ui.theme.MetronomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MetronomeTheme {
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

                MetronomeScreen(viewModel)
            }
        }
    }
}
