package com.independentmusictools.metronome.state

import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.audio.MetronomePlayer
import com.independentmusictools.metronome.core.AccentLevel
import com.independentmusictools.metronome.core.TapAverager
import com.independentmusictools.metronome.core.TimeSignature
import com.independentmusictools.metronome.settings.MetronomeSettings
import com.independentmusictools.metronome.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeViewModelTest {

    private class FakePlayer : MetronomePlayer {
        override var bpm: Int = 0
        override var cycleBeats: Int = 0
        override var timeSignature: TimeSignature = TimeSignature.DEFAULT
        override var sound: ClickSynthesizer.Sound = ClickSynthesizer.Sound.WOOD
        var started = 0
        var stopped = 0
        var previews = 0
        override val isRunning: Boolean get() = started > stopped
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun preview(sound: ClickSynthesizer.Sound) { previews++ }
        lateinit var onBeat: (Int, AccentLevel) -> Unit
        lateinit var onInterrupted: () -> Unit
    }

    private class MapPrefs : Prefs {
        val map = mutableMapOf<String, Any>()
        override fun getInt(key: String, def: Int): Int = map[key] as? Int ?: def
        override fun putInt(key: String, value: Int) { map[key] = value }
        override fun getString(key: String, def: String?): String? = map[key] as? String ?: def
        override fun putString(key: String, value: String) { map[key] = value }
    }

    private fun viewModel(
        fake: FakePlayer,
        prefs: Prefs = MapPrefs(),
        tapper: TapAverager = TapAverager(),
    ): MetronomeViewModel = MetronomeViewModel(
        settings = MetronomeSettings(prefs),
        playerFactory = { onBeat, onInterrupted ->
            fake.onBeat = onBeat
            fake.onInterrupted = onInterrupted
            fake
        },
        tapperFactory = { tapper },
    )

    @Test
    fun `initial state comes from settings defaults`() {
        val vm = viewModel(FakePlayer())
        assertEquals(100, vm.state.value.bpm)
        assertEquals(8, vm.state.value.cycleBeats)
        assertEquals(TimeSignature.FOUR_FOUR, vm.state.value.timeSignature)
        assertEquals(ClickSynthesizer.Sound.WOOD, vm.state.value.sound)
        assertFalse(vm.state.value.playing)
    }

    @Test
    fun `toggle starts and stops playback`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.toggle()
        assertTrue(vm.state.value.playing)
        assertEquals(1, fake.started)
        vm.toggle()
        assertFalse(vm.state.value.playing)
        assertEquals(1, fake.stopped)
    }

    @Test
    fun `stop clears current beat`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.toggle()
        fake.onBeat(3, AccentLevel.WEAK)
        assertEquals(3, vm.state.value.currentBeat)
        vm.toggle()
        assertNull(vm.state.value.currentBeat)
        assertNull(vm.state.value.currentAccent)
    }

    @Test
    fun `audio interruption stops playback in state`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.toggle()
        fake.onInterrupted()
        assertFalse(vm.state.value.playing)
        assertNull(vm.state.value.currentBeat)
    }

    @Test
    fun `background stop only acts when playing`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.stopForBackground()
        assertEquals(0, fake.stopped)
        vm.toggle()
        vm.stopForBackground()
        assertEquals(1, fake.stopped)
        assertFalse(vm.state.value.playing)
    }

    @Test
    fun `bpm is clamped and persisted`() {
        val prefs = MapPrefs()
        val vm = viewModel(FakePlayer(), prefs)
        vm.setBpm(1000)
        assertEquals(300, vm.state.value.bpm)
        vm.setBpm(1)
        assertEquals(30, vm.state.value.bpm)
        vm.setBpm(137)
        // Новая VM с теми же настройками читает сохранённое значение
        assertEquals(137, viewModel(FakePlayer(), prefs).state.value.bpm)
    }

    @Test
    fun `adjustBpm steps within range`() {
        val vm = viewModel(FakePlayer())
        vm.setBpm(299)
        vm.adjustBpm(5)
        assertEquals(300, vm.state.value.bpm)
        vm.adjustBpm(-500)
        assertEquals(30, vm.state.value.bpm)
    }

    @Test
    fun `cycle beats clamp to range 4-36 and persist independently of bpm`() {
        val prefs = MapPrefs()
        val vm = viewModel(FakePlayer(), prefs)
        vm.setBpm(120)
        vm.setCycleBeats(2)
        assertEquals(4, vm.state.value.cycleBeats)
        vm.setCycleBeats(99)
        assertEquals(36, vm.state.value.cycleBeats)
        vm.setCycleBeats(12)
        // bpm не тронут
        assertEquals(120, vm.state.value.bpm)
        val reloaded = viewModel(FakePlayer(), prefs)
        assertEquals(12, reloaded.state.value.cycleBeats)
        assertEquals(120, reloaded.state.value.bpm)
    }

    @Test
    fun `adjustCycleBeats steps by one`() {
        val vm = viewModel(FakePlayer())
        vm.setCycleBeats(8)
        vm.adjustCycleBeats(1)
        assertEquals(9, vm.state.value.cycleBeats)
        vm.adjustCycleBeats(-3)
        assertEquals(6, vm.state.value.cycleBeats)
    }

    @Test
    fun `time signature persists without touching cycle`() {
        val prefs = MapPrefs()
        val vm = viewModel(FakePlayer(), prefs)
        vm.setCycleBeats(7)
        vm.setTimeSignature(TimeSignature.SEVEN_EIGHT)
        assertEquals(TimeSignature.SEVEN_EIGHT, vm.state.value.timeSignature)
        assertEquals(7, vm.state.value.cycleBeats)
        val reloaded = viewModel(FakePlayer(), prefs)
        assertEquals(TimeSignature.SEVEN_EIGHT, reloaded.state.value.timeSignature)
        assertEquals(7, reloaded.state.value.cycleBeats)
    }

    @Test
    fun `time signature variant persists and reaches the player`() {
        val fake = FakePlayer()
        val prefs = MapPrefs()
        val vm = viewModel(fake, prefs)
        val variant = TimeSignature.parse("5/4|2+3")!!
        vm.setTimeSignature(variant)
        assertEquals(variant, vm.state.value.timeSignature)
        assertEquals(variant, fake.timeSignature)
        // Новая VM с теми же настройками восстанавливает именно вариант
        assertEquals(variant, viewModel(FakePlayer(), prefs).state.value.timeSignature)
    }

    @Test
    fun `sound selection persists and preview is played`() {
        val fake = FakePlayer()
        val prefs = MapPrefs()
        val vm = viewModel(fake, prefs)
        vm.selectSound(ClickSynthesizer.Sound.PULSE, preview = true)
        assertEquals(ClickSynthesizer.Sound.PULSE, vm.state.value.sound)
        assertEquals(1, fake.previews)
        vm.selectSound(ClickSynthesizer.Sound.TICK, preview = false)
        assertEquals(1, fake.previews)
        val reloaded = viewModel(FakePlayer(), prefs)
        assertEquals(ClickSynthesizer.Sound.TICK, reloaded.state.value.sound)
    }

    @Test
    fun `tap tempo sets bpm once intervals are known`() {
        var clock = 0L
        val tapper = TapAverager(now = { clock })
        val vm = viewModel(FakePlayer(), tapper = tapper)
        assertFalse(vm.tapTempo()) // первый тап — интервала ещё нет
        clock = 500
        assertTrue(vm.tapTempo()) // 120 BPM
        assertEquals(120, vm.state.value.bpm)
    }

    @Test
    fun `dispose stops the player`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.toggle()
        vm.dispose()
        assertEquals(1, fake.stopped)
    }

    @Test
    fun `player receives all settings before start`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.setBpm(180)
        vm.setCycleBeats(16)
        vm.setTimeSignature(TimeSignature.SIX_EIGHT)
        vm.selectSound(ClickSynthesizer.Sound.TICK, preview = false)
        vm.toggle()
        assertEquals(180, fake.bpm)
        assertEquals(16, fake.cycleBeats)
        assertEquals(TimeSignature.SIX_EIGHT, fake.timeSignature)
        assertEquals(ClickSynthesizer.Sound.TICK, fake.sound)
    }
}
