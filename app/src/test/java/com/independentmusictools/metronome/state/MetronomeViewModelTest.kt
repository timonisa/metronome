package com.independentmusictools.metronome.state

import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.audio.MetronomePlayer
import com.independentmusictools.metronome.core.TapAverager
import com.independentmusictools.metronome.settings.MetronomeSettings
import com.independentmusictools.metronome.settings.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeViewModelTest {

    private class FakePlayer : MetronomePlayer {
        override var bpm: Int = 0
        override var beatsPerBar: Int = 0
        override var sound: ClickSynthesizer.Sound = ClickSynthesizer.Sound.WOOD
        var started = 0
        var stopped = 0
        var previews = 0
        var tapClicks = 0
        override val isRunning: Boolean get() = started > stopped
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun preview(sound: ClickSynthesizer.Sound) { previews++ }
        override fun tapClick() { tapClicks++ }
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
        playerFactory = { onInterrupted ->
            fake.onInterrupted = onInterrupted
            fake
        },
        tapperFactory = { tapper },
    )

    @Test
    fun `initial state comes from settings defaults`() {
        val vm = viewModel(FakePlayer())
        assertEquals(100, vm.state.value.bpm)
        assertEquals(4, vm.state.value.beatsPerBar)
        assertEquals(ClickSynthesizer.Sound.WOOD, vm.state.value.sound)
        assertFalse(vm.state.value.playing)
        assertEquals(0, vm.state.value.tapCount)
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
    fun `audio interruption stops playback in state`() {
        val fake = FakePlayer()
        val vm = viewModel(fake)
        vm.toggle()
        fake.onInterrupted()
        assertFalse(vm.state.value.playing)
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
    fun `beats per bar clamps to 1-8 persists and reaches the player`() {
        val fake = FakePlayer()
        val prefs = MapPrefs()
        val vm = viewModel(fake, prefs)
        vm.setBeatsPerBar(0)
        assertEquals(1, vm.state.value.beatsPerBar)
        vm.setBeatsPerBar(99)
        assertEquals(8, vm.state.value.beatsPerBar)
        vm.setBeatsPerBar(6)
        assertEquals(6, fake.beatsPerBar)
        // bpm не тронут, новое VM читает сохранённое значение
        vm.setBpm(120)
        val reloaded = viewModel(FakePlayer(), prefs)
        assertEquals(6, reloaded.state.value.beatsPerBar)
        assertEquals(120, reloaded.state.value.bpm)
    }

    @Test
    fun `adjustBeatsPerBar steps by one`() {
        val vm = viewModel(FakePlayer())
        vm.setBeatsPerBar(4)
        vm.adjustBeatsPerBar(1)
        assertEquals(5, vm.state.value.beatsPerBar)
        vm.adjustBeatsPerBar(-3)
        assertEquals(2, vm.state.value.beatsPerBar)
        vm.adjustBeatsPerBar(-5)
        assertEquals(1, vm.state.value.beatsPerBar)
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
    fun `every tap increments tapCount for the flash and plays click feedback`() {
        var clock = 0L
        val tapper = TapAverager(now = { clock })
        val fake = FakePlayer()
        val vm = viewModel(fake, tapper = tapper)
        vm.tapTempo()
        assertEquals(1, vm.state.value.tapCount)
        clock = 500
        vm.tapTempo()
        clock = 1000
        vm.tapTempo() // интервалы по 500 мс → 120 BPM
        assertEquals(3, vm.state.value.tapCount)
        assertEquals(120, vm.state.value.bpm)
        assertEquals(3, fake.tapClicks)
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
        vm.setBeatsPerBar(7)
        vm.selectSound(ClickSynthesizer.Sound.TICK, preview = false)
        vm.toggle()
        assertEquals(180, fake.bpm)
        assertEquals(7, fake.beatsPerBar)
        assertEquals(ClickSynthesizer.Sound.TICK, fake.sound)
    }
}
