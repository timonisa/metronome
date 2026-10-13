package com.independentmusictools.metronome.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.core.AccentLevel
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.core.TimeSignature
import com.independentmusictools.metronome.settings.MetronomeSettings
import com.independentmusictools.metronome.state.MetronomeViewModel
import com.independentmusictools.metronome.ui.theme.CharcoalMuted
import com.independentmusictools.metronome.ui.theme.Hairline
import com.independentmusictools.metronome.ui.theme.Teal
import com.independentmusictools.metronome.ui.theme.Vermilion
import kotlin.math.roundToInt

/**
 * Главный экран метронома: темп (число, слайдер, шаги, TAP),
 * индикатор долей цикла, размер такта, длина цикла, звук, старт/стоп.
 */
@Composable
fun MetronomeScreen(viewModel: MetronomeViewModel) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("МЕТРОНОМ", style = MaterialTheme.typography.labelSmall, color = CharcoalMuted)
            Spacer(Modifier.height(8.dp))

            // Большой дисплей темпа
            Text(
                text = "${state.bpm}",
                style = TextStyle(
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text("BPM · ударов в минуту", style = MaterialTheme.typography.labelSmall, color = CharcoalMuted)

            Spacer(Modifier.height(8.dp))

            // Точный регулятор темпа — слайдер
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { viewModel.setBpm(it.roundToInt()) },
                valueRange = TempoMath.MIN_BPM.toFloat()..TempoMath.MAX_BPM.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Регулятор темпа" },
                colors = SliderDefaults.colors(
                    thumbColor = Teal,
                    activeTrackColor = Teal,
                    inactiveTrackColor = Hairline,
                ),
            )

            Spacer(Modifier.height(4.dp))

            // Шаговые кнопки и tap tempo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepButton("−5") { viewModel.adjustBpm(-5) }
                StepButton("−1") { viewModel.adjustBpm(-1) }
                StepButton("TAP", wide = true) { viewModel.tapTempo() }
                StepButton("+1") { viewModel.adjustBpm(1) }
                StepButton("+5") { viewModel.adjustBpm(5) }
            }

            Spacer(Modifier.height(24.dp))

            // Индикатор долей тренировочного цикла
            BeatIndicator(
                cycleBeats = state.cycleBeats,
                currentBeat = state.currentBeat,
                currentAccent = state.currentAccent,
            )
            Spacer(Modifier.height(8.dp))
            val beatLabel = state.currentBeat
                ?.let { "доля ${it + 1} из ${state.cycleBeats}" }
                ?: "цикл из ${state.cycleBeats} долей"
            Text(beatLabel, style = MaterialTheme.typography.labelSmall, color = CharcoalMuted)

            Spacer(Modifier.height(20.dp))

            // Размер такта (акцентные группировки)
            LabeledChips(
                label = "РАЗМЕР · акценты",
                options = TimeSignature.ALL.map { it.label },
                selected = state.timeSignature.label,
            ) { label ->
                TimeSignature.parse(label)?.let { viewModel.setTimeSignature(it) }
            }

            Spacer(Modifier.height(16.dp))

            // Длина тренировочного цикла (4–36) — независимая настройка
            Text("ЦИКЛ ТРЕНИРОВКИ", style = MaterialTheme.typography.labelSmall, color = CharcoalMuted)
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SmallStepButton("−", "Уменьшить длину цикла") { viewModel.adjustCycleBeats(-1) }
                Text(
                    text = "${state.cycleBeats}",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                )
                SmallStepButton("+", "Увеличить длину цикла") { viewModel.adjustCycleBeats(1) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Размер задаёт группировку акцентов внутри такта.\n" +
                    "Цикл — сколько долей в тренировочном круге (4–36), независимо от размера.",
                fontSize = 11.sp,
                color = CharcoalMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // Звук: выбор = прослушивание примера
            LabeledChips(
                label = "ЗВУК · нажмите для примера",
                options = ClickSynthesizer.Sound.entries.map { it.label },
                selected = state.sound.label,
            ) { label ->
                ClickSynthesizer.Sound.entries.firstOrNull { it.label == label }
                    ?.let { viewModel.selectSound(it, preview = true) }
            }

            Spacer(Modifier.height(24.dp))

            // Старт/стоп
            FilledIconButton(
                onClick = viewModel::toggle,
                modifier = Modifier
                    .size(76.dp)
                    .semantics {
                        contentDescription = if (state.playing) "Остановить метроном" else "Запустить метроном"
                    },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (state.playing) Vermilion else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = if (state.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Воспроизведение останавливается при сворачивании приложения",
                fontSize = 10.sp,
                color = CharcoalMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StepButton(label: String, wide: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = if (wide) 64.dp else 48.dp)
            .semantics { contentDescription = "Кнопка $label" },
        contentPadding = PaddingValues(horizontal = if (wide) 18.dp else 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallStepButton(symbol: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 48.dp)
            .semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(symbol, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Индикатор долей: точки построчно (до 12 в ряд). Первая доля цикла —
 * крупнее, с кольцом; текущая доля подсвечена (сильный акцент — вермильон).
 */
@Composable
private fun BeatIndicator(
    cycleBeats: Int,
    currentBeat: Int?,
    currentAccent: AccentLevel?,
) {
    val perRow = 12
    val rows = (cycleBeats + perRow - 1) / perRow
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val start = r * perRow
                val end = minOf(start + perRow, cycleBeats)
                for (i in start until end) {
                    val active = currentBeat == i
                    val dot = if (i == 0) 22.dp else 18.dp
                    Box(
                        modifier = Modifier
                            .size(dot)
                            .semantics { contentDescription = if (i == 0) "Первая доля" else "Доля ${i + 1}" },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension / 2f
                            when {
                                active && (currentAccent == AccentLevel.STRONG || i == 0) -> {
                                    drawCircle(Vermilion.copy(alpha = 0.30f), radius * 1.45f)
                                    drawCircle(Vermilion, radius)
                                }
                                active -> drawCircle(Teal, radius)
                                i == 0 -> drawCircle(
                                    Teal.copy(alpha = 0.55f),
                                    radius,
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                                else -> drawCircle(Hairline, radius * 0.75f)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledChips(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CharcoalMuted)
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}
