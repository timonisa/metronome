package com.independentmusictools.metronome.audio

import android.content.Context
import com.independentmusictools.metronome.R
import com.independentmusictools.metronome.core.AccentLevel
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Щелчок реального механического метронома — сэмпл PCM16 моно 44,1 кГц
 * из res/raw (около 90 мс, CC0: Wikimedia Commons «Metronome 60bpm.wav»,
 * вырезан один удар). Загружается один раз и кешируется.
 *
 * Сэмпл поднят по высоте ресемплингом (×1.40): запись на 60 BPM звучит
 * слишком басовито для щелчка. Сильная доля — ещё выше (×1.65) и
 * громче: «на первый удар — выше звук».
 */
object SampleClicks {

    private const val RATE = 44100

    /** Базовое повышение тона: запись на 60 BPM звучит басовито. */
    private const val WEAK_PITCH = 1.40

    /** Сильная доля — ещё выше обычной, «первый удар — выше звуком». */
    private const val STRONG_PITCH = WEAK_PITCH * 1.18

    private var weakCache: ShortArray? = null
    private var strongCache: ShortArray? = null

    @Synchronized
    fun click(context: Context, level: AccentLevel): ShortArray {
        if (weakCache == null) {
            val raw = loadWavPcm16(context.resources.openRawResource(R.raw.mech_click).readBytes())
            weakCache = normalize(resample(raw, WEAK_PITCH), peakTarget = 0.78)
            strongCache = normalize(resample(raw, STRONG_PITCH), peakTarget = 0.92)
        }
        return if (level == AccentLevel.STRONG) strongCache!! else weakCache!!
    }

    /** Минимальный WAV-парсер: RIFF → чанк fmt (PCM16 моно) → чанк data. */
    private fun loadWavPcm16(bytes: ByteArray): ShortArray {
        val inStream = ByteArrayInputStream(bytes)
        val header = ByteArray(12)
        require(inStream.read(header) == 12 && String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") {
            "не WAV-файл"
        }
        var fmtSeen = false
        while (true) {
            val chunkHeader = ByteArray(8)
            if (inStream.read(chunkHeader) < 8) error("чанк data не найден")
            val id = String(chunkHeader, 0, 4)
            val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (id) {
                "fmt " -> {
                    val fmt = ByteArray(16)
                    require(inStream.read(fmt) == 16) { "битый чанк fmt" }
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    val audioFormat = bb.short.toInt()
                    val channels = bb.short.toInt()
                    bb.int // sampleRate
                    bb.int // byteRate
                    bb.short // blockAlign
                    val bitsPerSample = bb.short.toInt()
                    require(audioFormat == 1 && channels == 1 && bitsPerSample == 16) {
                        "ожидался PCM16 моно, получено: format=$audioFormat ch=$channels bits=$bitsPerSample"
                    }
                    fmtSeen = true
                }
                "data" -> {
                    require(fmtSeen) { "чанк fmt после data" }
                    val data = ByteArray(size)
                    require(inStream.read(data) == size) { "битый чанк data" }
                    return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().let { sb ->
                        ShortArray(sb.remaining()).also { sb.get(it) }
                    }
                }
                else -> inStream.skip(size.toLong()) // LIST и прочее
            }
        }
    }

    /** Линейная интерполяция: rate × [factor] = выше тон и короче сэмпл. */
    private fun resample(src: ShortArray, factor: Double): ShortArray {
        val outLen = (src.size / factor).toInt()
        return ShortArray(outLen) { j ->
            val pos = j * factor
            val i = pos.toInt()
            val frac = pos - i
            if (i + 1 < src.size) {
                (src[i] + (src[i + 1] - src[i]) * frac).toInt().toShort()
            } else {
                src[i.coerceAtMost(src.size - 1)]
            }
        }
    }

    private fun normalize(src: ShortArray, peakTarget: Double): ShortArray {
        var peak = 1
        for (s in src) peak = maxOf(peak, kotlin.math.abs(s.toInt()))
        val scale = peakTarget * 32767.0 / peak
        return ShortArray(src.size) {
            (src[it] * scale).toInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
