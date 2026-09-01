package dev.intervaltablet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF77E1FF),
    onPrimary = Color(0xFF002D38),
    primaryContainer = Color(0xFF004E60),
    onPrimaryContainer = Color(0xFFB8ECF8),
    secondary = Color(0xFFFFC66D),
    onSecondary = Color(0xFF442A00),
    secondaryContainer = Color(0xFF5E3D00),
    onSecondaryContainer = Color(0xFFFFDDA6),
    tertiary = Color(0xFFC8B8FF),
    tertiaryContainer = Color(0xFF40316F),
    onTertiaryContainer = Color(0xFFE8DEFF),
    background = Color(0xFF090D11),
    onBackground = Color(0xFFE4E8ED),
    surface = Color(0xFF0E1318),
    onSurface = Color(0xFFE4E8ED),
    surfaceVariant = Color(0xFF293138),
    onSurfaceVariant = Color(0xFFBBC5CD),
    surfaceContainerLowest = Color(0xFF070A0D),
    surfaceContainer = Color(0xFF141A20),
    surfaceContainerHigh = Color(0xFF1B232A),
    surfaceContainerHighest = Color(0xFF242D35),
    outline = Color(0xFF849099),
    outlineVariant = Color(0xFF39434B),
    error = Color(0xFFFF6F76),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF68000C),
    onErrorContainer = Color(0xFFFFDAD9),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00677E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8ECF8),
    onPrimaryContainer = Color(0xFF001F27),
    secondary = Color(0xFF765A20),
    secondaryContainer = Color(0xFFFFDDA6),
    tertiary = Color(0xFF63558F),
    tertiaryContainer = Color(0xFFE8DEFF),
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFF7F9FB),
    surfaceContainer = Color(0xFFECEFF2),
    surfaceContainerHigh = Color(0xFFE1E5E9),
    surfaceContainerHighest = Color(0xFFD8DDE1),
    outline = Color(0xFF58636B),
)

@Composable
fun IntervalTabletTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
