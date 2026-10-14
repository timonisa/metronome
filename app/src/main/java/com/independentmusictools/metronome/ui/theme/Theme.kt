package com.independentmusictools.metronome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.independentmusictools.metronome.settings.ThemeMode

// Светлая — «кремовая», тёмная — инверсия нейтральных с осветлёнными
// акцентами (палитра как в tuner).
private val LightInstrumentColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = PetrolDark,
    onSecondary = Color.White,
    tertiary = Vermilion,
    background = Ivory,
    onBackground = Charcoal,
    surface = CreamPanel,
    onSurface = Charcoal,
    surfaceVariant = CreamPanel,
    onSurfaceVariant = CharcoalMuted,
    outline = Hairline,
    error = Vermilion,
)

private val DarkInstrumentColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF0A1F1E),
    secondary = PetrolLight,
    onSecondary = Color(0xFF0A1F1E),
    tertiary = VermilionLight,
    background = NightBg,
    onBackground = NightText,
    surface = NightPanel,
    onSurface = NightText,
    surfaceVariant = NightPanel,
    onSurfaceVariant = NightTextMuted,
    outline = NightHairline,
    error = VermilionLight,
)

@Composable
fun MetronomeTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (themeMode == ThemeMode.DARK) DarkInstrumentColors else LightInstrumentColors,
        typography = InstrumentTypography,
        content = content,
    )
}
