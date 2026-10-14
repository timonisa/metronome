package com.independentmusictools.metronome.state

import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.audio.MetronomePlayer
import com.independentmusictools.metronome.core.TapAverager
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.settings.MetronomeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние и логика метронома. Плеер и tapper инъектируются фабриками —
 * в unit-тестах подменяются фейками, в приложении используется
 * AudioTrackMetronomePlayer с реальным звуком.
 *
 * tapCount растёт с каждым тапом по цифрам темпа — UI использует его
 * как триггер вспышки.
 */
class MetronomeViewModel(
    private val settings: MetronomeSettings,
    playerFactory: (onInterrupted: () -> Unit) -> MetronomePlayer,
    tapperFactory: () -> TapAverager = { TapAverager() },
) {

    data class UiState(
        val playing: Boolean = false,
        val bpm: Int = MetronomeSettings.DEFAULT_BPM,
        val beatsPerBar: Int = MetronomeSettings.DEFAULT_BEATS,
        val sound: ClickSynthesizer.Sound = ClickSynthesizer.Sound.WOOD,
        val tapCount: Int = 0,
    )

    private val _state = MutableStateFlow(
        UiState(
            bpm = settings.bpm,
            beatsPerBar = settings.beatsPerBar,
            sound = settings.sound,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val player: MetronomePlayer = playerFactory(::onInterruptedInternal)
    private val tapper = tapperFactory()

    init {
        syncPlayer()
    }

    private fun syncPlayer() {
        val s = _state.value
        player.bpm = s.bpm
        player.beatsPerBar = s.beatsPerBar
        player.sound = s.sound
    }

    fun toggle() {
        if (_state.value.playing) {
            stopPlayback()
        } else {
            player.start()
            _state.value = _state.value.copy(playing = true)
        }
    }

    /** Политика: без фонового воспроизведения — при уходе из приложения останавливаемся. */
    fun stopForBackground() {
        if (_state.value.playing) stopPlayback()
    }

    private fun stopPlayback() {
        player.stop()
        tapper.reset()
        _state.value = _state.value.copy(playing = false)
    }

    private fun onInterruptedInternal() {
        _state.value = _state.value.copy(playing = false)
    }

    fun setBpm(value: Int) {
        val v = value.coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM)
        settings.bpm = v
        player.bpm = v
        _state.value = _state.value.copy(bpm = v)
    }

    fun adjustBpm(delta: Int) = setBpm(_state.value.bpm + delta)

    /** Ударов в такте 1–8; первый удар такта звучит выше. */
    fun setBeatsPerBar(value: Int) {
        val v = value.coerceIn(MetronomeSettings.MIN_BEATS_PER_BAR, MetronomeSettings.MAX_BEATS_PER_BAR)
        settings.beatsPerBar = v
        player.beatsPerBar = v
        _state.value = _state.value.copy(beatsPerBar = v)
    }

    fun adjustBeatsPerBar(delta: Int) = setBeatsPerBar(_state.value.beatsPerBar + delta)

    /** Выбор звука; preview=true проигрывает пример (сильная + обычная доля). */
    fun selectSound(sound: ClickSynthesizer.Sound, preview: Boolean) {
        settings.sound = sound
        player.sound = sound
        _state.value = _state.value.copy(sound = sound)
        if (preview) player.preview(sound)
    }

    /** Tap tempo: усреднение интервалов тапов; false — пока недостаточно тапов. */
    fun tapTempo(): Boolean {
        val bpm = tapper.tap() ?: run {
            _state.value = _state.value.copy(tapCount = _state.value.tapCount + 1)
            return false
        }
        setBpm(bpm)
        _state.value = _state.value.copy(tapCount = _state.value.tapCount + 1)
        return true
    }

    fun dispose() {
        player.stop()
    }
}
