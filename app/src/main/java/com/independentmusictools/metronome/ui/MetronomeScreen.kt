package com.independentmusictools.metronome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.state.MetronomeViewModel
import com.independentmusictools.metronome.ui.theme.CharcoalMuted
import com.independentmusictools.metronome.ui.theme.Hairline
import com.independentmusictools.metronome.ui.theme.Teal
import com.independentmusictools.metronome.ui.theme.Vermilion
import kotlin.math.roundToInt

/**
 * Главный экран метронома: темп (цифры с TAP по нажатию, слайдер, ±5),
 * удары в такте 1–8 (первый удар выше звуком), звук, старт/стоп.
 */
@Composable
fun MetronomeScreen(viewModel: MetronomeViewModel) {
    val state by viewModel.state.collectAsState()

    // Вспышка цифр темпа при каждом тапе: плавно гаснет от светлого к обычному.
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.tapCount) {
        if (state.tapCount > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, animationSpec = tween(durationMillis = 250))
        }
    }

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

            // Темп: −5 · цифры (TAP по нажатию со вспышкой) · +5
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StepButton("−5") { viewModel.adjustBpm(-5) }
                Text(
                    text = "${state.bpm}",
                    style = TextStyle(
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                    color = lerp(
                        MaterialTheme.colorScheme.onBackground,
                        Color.White,
                        flash.value,
                    ),
                    modifier = Modifier
                        .clickable { viewModel.tapTempo() }
                        .semantics { contentDescription = "Тап-темпо: нажмите на цифры темпа" },
                )
                StepButton("+5") { viewModel.adjustBpm(5) }
            }
            Text(
                "BPM · ударов в минуту · тап по цифрам = TAP",
                style = MaterialTheme.typography.labelSmall,
                color = CharcoalMuted,
            )

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

            Spacer(Modifier.height(28.dp))

            // Ударов в такте (1–8); первый удар такта звучит выше
            Text("УДАРОВ В ТАКТЕ", style = MaterialTheme.typography.labelSmall, color = CharcoalMuted)
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SmallStepButton("−", "Уменьшить количество ударов в такте") { viewModel.adjustBeatsPerBar(-1) }
                Text(
                    text = "${state.beatsPerBar}",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                    ),
                )
                SmallStepButton("+", "Увеличить количество ударов в такте") { viewModel.adjustBeatsPerBar(1) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Первый удар такта — выше звуком",
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
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 52.dp)
            .semantics { contentDescription = "Кнопка $label" },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
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
