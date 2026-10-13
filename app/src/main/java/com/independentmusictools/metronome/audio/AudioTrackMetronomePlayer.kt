package com.independentmusictools.metronome.audio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.independentmusictools.metronome.core.AccentLevel
import com.independentmusictools.metronome.core.BeatPlacer
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.core.TimeSignature
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Аудио-движок метронома на AudioTrack (потоковый режим).
 *
 * Тактирование полностью аудио-центрично: аудио-поток заранее
 * (с опережением буфера) готовит окна по ~50 мс, а позиции долей
 * считаются от абсолютного сэмпла с начала воспроизведения —
 * кумулятивного дрейфа нет. Handler.postDelayed используется ТОЛЬКО
 * для визуальной индикации (с компенсацией глубины очереди AudioTrack),
 * не как механизм тактирования.
 *
 * Смена темпа/размера/звука/длины цикла применяется при формировании
 * очередного окна — без остановки и щелчков.
 *
 * Обработка прерываний: запрос аудио-фокуса (потеря → остановка),
 * ACTION_AUDIO_BECOMING_NOISY (отключение наушников → остановка).
 */
class AudioTrackMetronomePlayer(
    @Suppress("unused") private val context: Context,
    private val onBeat: (positionInCycle: Int, level: AccentLevel) -> Unit,
    private val onInterrupted: () -> Unit,
) : MetronomePlayer {

    override var bpm: Int = 100
        set(value) { field = value.coerceIn(TempoMath.MIN_BPM, TempoMath.MAX_BPM) }

    override var cycleBeats: Int = 8
        set(value) { field = value.coerceIn(MetronomePlayerDefaults.MIN_CYCLE_BEATS, MetronomePlayerDefaults.MAX_CYCLE_BEATS) }

    override var timeSignature: TimeSignature = TimeSignature.DEFAULT

    override var sound: ClickSynthesizer.Sound = ClickSynthesizer.Sound.WOOD

    override val isRunning: Boolean get() = running.get()

    private val running = AtomicBoolean(false)
    private val totalWritten = AtomicLong(0)
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered = false

    private val focusListener = object : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(change: Int) {
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                mainHandler.post {
                    stop()
                    onInterrupted()
                }
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            mainHandler.post {
                stop()
                onInterrupted()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // флаг задаётся через ContextCompat ниже
    override fun start() {
        if (!running.compareAndSet(false, true)) return
        if (!requestFocus()) {
            running.set(false)
            mainHandler.post(onInterrupted)
            return
        }
        registerNoisy()
        val t = buildTrack()
        track = t
        totalWritten.set(0)
        mixer.reset()
        // play() вызывается после первой записи данных — первый щелчок
        // не попадает в стартовый underrun.
        thread = Thread({ loop(t) }, "metronome-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        thread?.join(600)
        thread = null
        mainHandler.removeCallbacksAndMessages(null)
        track?.let { t ->
            runCatching { t.pause(); t.flush(); t.stop(); t.release() }
        }
        track = null
        abandonFocus()
        unregisterNoisy()
    }

    override fun preview(sound: ClickSynthesizer.Sound) {
        Thread({
            val strong = clickFor(sound, AccentLevel.STRONG)
            val weak = clickFor(sound, AccentLevel.WEAK)
            val gap = (0.32 * SAMPLE_RATE).toInt()
            val buf = ShortArray(strong.size + gap + weak.size)
            System.arraycopy(strong, 0, buf, 0, strong.size)
            System.arraycopy(weak, 0, buf, strong.size + gap, weak.size)
            val t = buildTrack()
            runCatching {
                t.write(buf, 0, buf.size)
                t.play()
                val durMs = buf.size * 1000L / SAMPLE_RATE + 200
                mainHandler.postDelayed({
                    runCatching { t.stop(); t.release() }
                }, durMs)
            }
        }, "metronome-preview").start()
    }

    /**
     * Основной цикл: окна по 50 мс, щелчки микшируются в точных сэмпловых
     * позициях, «хвосты» щелчков переносятся через границу окна (ClickMixer).
     * Буферы и список долей переиспользуются — аллокаций в горячем пути нет.
     */
    private fun loop(t: AudioTrack) {
        val out = ShortArray(SAMPLES_PER_WINDOW)
        val placed = ArrayList<BeatPlacer.PlacedBeat>(8)
        var played = false
        while (running.get()) {
            val windowStart = totalWritten.get()
            BeatPlacer.fillInto(
                placed,
                windowStart,
                windowStart + SAMPLES_PER_WINDOW,
                SAMPLE_RATE * 60.0 / bpm,
                4, // параметр beatsPerBar влияет только на флаг PlacedBeat.isDownbeat, движком не используется
            )
            val cycle = cycleBeats.coerceAtLeast(1)
            val ts = timeSignature
            val snd = sound
            for (b in placed) {
                val posInCycle = (b.beatIndex % cycle).toInt()
                val level = ts.accentAt(posInCycle, cycle)
                val click = clickFor(snd, level)
                mixer.mix(b.offsetInWindow.toInt(), click)
                android.util.Log.d(
                    TAG,
                    "beat=${b.beatIndex} pos=$posInCycle lvl=$level off=${b.offsetInWindow} clickLen=${click.size} peak=${click.maxOf { abs(it.toInt()) }}",
                )
                // Визуальное событие — на момент фактического звучания сэмпла.
                val delayMs = ((windowStart + b.offsetInWindow - queuedSamples()) * 1000.0 / SAMPLE_RATE)
                    .roundToLong().coerceIn(0, 5_000)
                val pos = posInCycle
                val lv = level
                mainHandler.postDelayed({ if (running.get()) onBeat(pos, lv) }, delayMs)
            }
            mixer.takeWindow(out)
            var written = 0
            while (written < SAMPLES_PER_WINDOW && running.get()) {
                val n = t.write(out, written, SAMPLES_PER_WINDOW - written)
                if (n <= 0) {
                    Thread.sleep(5)
                    continue
                }
                written += n
            }
            totalWritten.addAndGet(written.toLong())
            if (!played && written > 0 && running.get()) {
                played = true
                runCatching { t.play() }
            }
        }
    }

    /** Глубина очереди AudioTrack в сэмплах (для компенсации задержки UI). */
    private fun queuedSamples(): Long {
        val t = track ?: return 0
        return maxOf(0L, totalWritten.get() - t.playbackHeadPosition.toLong())
    }

    private val clickCache = Array(ClickSynthesizer.Sound.entries.size) {
        Array<ShortArray?>(AccentLevel.entries.size) { null }
    }

    /** Максимальная длина щелчка по всем звукам и акцентам (WOOD ~4498 сэмплов). */
    private val mixer = ClickMixer(SAMPLES_PER_WINDOW, maxClickSamples = 4600)

    @Synchronized
    private fun clickFor(sound: ClickSynthesizer.Sound, level: AccentLevel): ShortArray {
        var c = clickCache[sound.ordinal][level.ordinal]
        if (c == null) {
            c = ClickSynthesizer.synthesize(sound, level, SAMPLE_RATE)
            clickCache[sound.ordinal][level.ordinal] = c
        }
        return c
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf * 2, SAMPLES_PER_WINDOW * 4))
            .build()
    }

    private fun requestFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusListener, mainHandler)
                .build()
            focusRequest = req
            return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Suppress("DEPRECATION")
        return audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private fun registerNoisy() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyReceiverRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    object MetronomePlayerDefaults {
        const val MIN_CYCLE_BEATS = 4
        const val MAX_CYCLE_BEATS = 36
    }

    companion object {
        private const val TAG = "MetronomeEngine"
        const val SAMPLE_RATE = 44100

        /** Окно ~50 мс при 44,1 кГц. */
        const val SAMPLES_PER_WINDOW = 2205
    }
}
