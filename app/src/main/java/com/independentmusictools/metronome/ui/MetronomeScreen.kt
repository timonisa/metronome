package com.independentmusictools.metronome.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.independentmusictools.metronome.R
import com.independentmusictools.metronome.audio.ClickSynthesizer
import com.independentmusictools.metronome.core.TempoMath
import com.independentmusictools.metronome.state.MetronomeViewModel
import kotlin.math.roundToInt

/**
 * Главный экран метронома: звук (вверху), темп (по центру: цифры с TAP
 * по нажатию, слайдер, ±5), удары в такте 1–8 и старт/стоп.
 */
@Composable
fun MetronomeScreen(
    viewModel: MetronomeViewModel,
    onOpenSettings: () -> Unit,
) {
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
        // Скролл снаружи, центрирование — внутри: на большом экране блок
        // темпа в середине, на маленьком контент прокручивается.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Шестерёнка — переход в настройки (верхний правый угол).
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Звук — в самом верху; выбор = прослушивание примера
                val soundOptions = ClickSynthesizer.Sound.entries.map { it to soundLabel(it) }
                LabeledChips(
                    label = stringResource(R.string.sound_section),
                    options = soundOptions.map { it.second },
                    selected = soundLabel(state.sound),
                ) { label ->
                    soundOptions.firstOrNull { it.second == label }
                        ?.first?.let { viewModel.selectSound(it, preview = true) }
                }

                Spacer(Modifier.height(44.dp))

                // Темп: −5 · цифры (TAP по нажатию со вспышкой и щелчком) · +5
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val stepCd = stringResource(R.string.bpm_step_cd, "−5")
                    StepButton("−5", stepCd) { viewModel.adjustBpm(-5) }
                    val tapCd = stringResource(R.string.tap_cd)
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
                            .semantics { contentDescription = tapCd },
                    )
                    val stepCdPlus = stringResource(R.string.bpm_step_cd, "+5")
                    StepButton("+5", stepCdPlus) { viewModel.adjustBpm(5) }
                }
                Text(
                    stringResource(R.string.bpm_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // Точный регулятор темпа — слайдер
                val sliderCd = stringResource(R.string.bpm_slider_cd)
                Slider(
                    value = state.bpm.toFloat(),
                    onValueChange = { viewModel.setBpm(it.roundToInt()) },
                    valueRange = TempoMath.MIN_BPM.toFloat()..TempoMath.MAX_BPM.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = sliderCd },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline,
                    ),
                )

                Spacer(Modifier.height(40.dp))

                // Ударов в такте (1–8); первый удар такта звучит выше
                Text(
                    stringResource(R.string.beats_section),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val lowerCd = stringResource(R.string.beats_lower_cd)
                    SmallStepButton("−", lowerCd) { viewModel.adjustBeatsPerBar(-1) }
                    Text(
                        text = "${state.beatsPerBar}",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                    val raiseCd = stringResource(R.string.beats_raise_cd)
                    SmallStepButton("+", raiseCd) { viewModel.adjustBeatsPerBar(1) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.beats_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(40.dp))

                // Старт/стоп
                val playCd = if (state.playing) {
                    stringResource(R.string.stop_cd)
                } else {
                    stringResource(R.string.start_cd)
                }
                FilledIconButton(
                    onClick = viewModel::toggle,
                    modifier = Modifier
                        .size(76.dp)
                        .semantics { contentDescription = playCd },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (state.playing) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Icon(
                        imageVector = if (state.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.background_stop_note),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Локализованное имя звука. */
@Composable
private fun soundLabel(sound: ClickSynthesizer.Sound): String = stringResource(
    when (sound) {
        ClickSynthesizer.Sound.WOOD -> R.string.sound_wood
        ClickSynthesizer.Sound.TICK -> R.string.sound_tick
        ClickSynthesizer.Sound.PULSE -> R.string.sound_pulse
        ClickSynthesizer.Sound.MECH -> R.string.sound_mech
    },
)

@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .widthIn(min = 52.dp)
            .semantics { contentDescription = description },
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
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
