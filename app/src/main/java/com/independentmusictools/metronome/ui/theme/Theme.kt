package com.independentmusictools.metronome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Единственная тема — светлая «кремовая» (см. DESIGN_SYSTEM.md, «Тёмная тема»).
private val InstrumentColors = lightColorScheme(
    primary = Teal,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = PetrolDark,
    onSecondary = androidx.compose.ui.graphics.Color.White,
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

@Composable
fun MetronomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InstrumentColors,
        typography = InstrumentTypography,
        content = content,
    )
}
