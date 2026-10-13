package com.independentmusictools.metronome.state

import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.audio.MetronomePlayer
import com.independentmusictools.metronome.core.AccentLevel
import com.independentmusictools.metronome.core.TapAverager
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.core.TimeSignature
import com.independentmusictools.metronome.settings.MetronomeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние и логика метронома. Плеер и tapper инъектируются фабриками —
 * в unit-тестах подменяются фейками, в приложении используется
 * AudioTrackMetronomePlayer с реальным звуком.
 */
class MetronomeViewModel(
    private val settings: MetronomeSettings,
    playerFactory: (
        onBeat: (positionInCycle: Int, level: AccentLevel) -> Unit,
        onInterrupted: () -> Unit,
    ) -> MetronomePlayer,
    tapperFactory: () -> TapAverager = { TapAverager() },
) {

    data class UiState(
        val playing: Boolean = false,
        val bpm: Int = MetronomeSettings.DEFAULT_BPM,
        val cycleBeats: Int = MetronomeSettings.DEFAULT_CYCLE,
        val timeSignature: TimeSignature = TimeSignature.DEFAULT,
        val sound: ClickSynthesizer.Sound = ClickSynthesizer.Sound.WOOD,
        val currentBeat: Int? = null,
        val currentAccent: AccentLevel? = null,
    )

    private val _state = MutableStateFlow(
        UiState(
            bpm = settings.bpm,
            cycleBeats = settings.cycleBeats,
            timeSignature = settings.timeSignature,
            sound = settings.sound,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val player: MetronomePlayer = playerFactory(::onBeatInternal, ::onInterruptedInternal)
    private val tapper = tapperFactory()

    init {
        syncPlayer()
    }

    private fun syncPlayer() {
        val s = _state.value
        player.bpm = s.bpm
        player.cycleBeats = s.cycleBeats
        player.timeSignature = s.timeSignature
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
        _state.value = _state.value.copy(playing = false, currentBeat = null, currentAccent = null)
    }

    private fun onBeatInternal(positionInCycle: Int, level: AccentLevel) {
        _state.value = _state.value.copy(currentBeat = positionInCycle, currentAccent = level)
    }

    private fun onInterruptedInternal() {
        _state.value = _state.value.copy(playing = false, currentBeat = null, currentAccent = null)
    }

    fun setBpm(value: Int) {
        val v = value.coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM)
        settings.bpm = v
        player.bpm = v
        _state.value = _state.value.copy(bpm = v)
    }

    fun adjustBpm(delta: Int) = setBpm(_state.value.bpm + delta)

    fun setCycleBeats(value: Int) {
        val v = value.coerceIn(MetronomeSettings.MIN_CYCLE_BEATS, MetronomeSettings.MAX_CYCLE_BEATS)
        settings.cycleBeats = v
        player.cycleBeats = v
        _state.value = _state.value.copy(cycleBeats = v, currentBeat = null, currentAccent = null)
    }

    fun adjustCycleBeats(delta: Int) = setCycleBeats(_state.value.cycleBeats + delta)

    fun setTimeSignature(ts: TimeSignature) {
        settings.timeSignature = ts
        player.timeSignature = ts
        _state.value = _state.value.copy(timeSignature = ts)
    }

    /** Выбор звука; preview=true проигрывает пример (сильная + обычная доля). */
    fun selectSound(sound: ClickSynthesizer.Sound, preview: Boolean) {
        settings.sound = sound
        player.sound = sound
        _state.value = _state.value.copy(sound = sound)
        if (preview) player.preview(sound)
    }

    /** Tap tempo: усреднение интервалов тапов; false — пока недостаточно тапов. */
    fun tapTempo(): Boolean {
        val bpm = tapper.tap() ?: return false
        setBpm(bpm)
        return true
    }

    fun dispose() {
        player.stop()
    }
}
