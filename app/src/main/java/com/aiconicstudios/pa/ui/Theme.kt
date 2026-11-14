package com.aiconicstudios.pa.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF3D3FA8)
private val IndigoLight = Color(0xFFB8B9FF)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E0FF),
    onPrimaryContainer = Color(0xFF0E0F5C),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFE85D75),
    background = Color(0xFFF7F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEDF4),
    outlineVariant = Color(0xFFDCDCE6),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Color(0xFF16176E),
    primaryContainer = Color(0xFF2B2D8F),
    onPrimaryContainer = Color(0xFFE1E0FF),
    secondary = Color(0xFF5EEAD4),
    tertiary = Color(0xFFFF8FA3),
    background = Color(0xFF121218),
    surface = Color(0xFF1A1A22),
    surfaceVariant = Color(0xFF26262F),
    outlineVariant = Color(0xFF34343F),
)

@Composable
fun PaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Block colours for the timetable: (light container, dark container, accent). */
data class BlockColor(val light: Color, val dark: Color, val accent: Color)

val BlockPalette = listOf(
    BlockColor(Color(0xFFFFE9B0), Color(0xFF4A3B12), Color(0xFFE0A100)), // amber   — studies
    BlockColor(Color(0xFFC9F5EC), Color(0xFF123F38), Color(0xFF0D9488)), // teal    — exercise
    BlockColor(Color(0xFFDCE3FF), Color(0xFF242E5C), Color(0xFF4F63D9)), // blue    — work
    BlockColor(Color(0xFFF3DDFF), Color(0xFF3F2552), Color(0xFF9C4DCC)), // violet  — reading
    BlockColor(Color(0xFFD9F2D0), Color(0xFF233D1B), Color(0xFF4C9A2A)), // green   — mindful
    BlockColor(Color(0xFFFFDDE4), Color(0xFF52222D), Color(0xFFE0475F)), // pink    — creative
    BlockColor(Color(0xFFFFE2CC), Color(0xFF4D2D14), Color(0xFFE8711A)), // orange  — meals
    BlockColor(Color(0xFFE2E5EC), Color(0xFF2C2F38), Color(0xFF66708A)), // slate   — rest
    BlockColor(Color(0xFFD6F0FF), Color(0xFF173B52), Color(0xFF1E88C7)), // sky     — custom
    BlockColor(Color(0xFFFFF4C2), Color(0xFF47400F), Color(0xFFB59A00)), // lemon
)

@Composable
fun blockColor(index: Int): Pair<Color, Color> {
    val c = BlockPalette[index.mod(BlockPalette.size)]
    return (if (isSystemInDarkTheme()) c.dark else c.light) to c.accent
}
