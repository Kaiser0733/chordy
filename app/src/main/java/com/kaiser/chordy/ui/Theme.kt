package com.kaiser.chordy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Chordy lives in a dark pocket of the screen; settings match.
// One accent (mint), flat surfaces, no gradients — per DECISIONS.md.
private val Mint = Color(0xFF7FD4A8)
private val Bg = Color(0xFF101014)
private val SurfaceCol = Color(0xFF1C1C24)
private val Outline = Color(0xFF33333F)

private val ChordyScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Bg,
    background = Bg,
    onBackground = Color(0xFFEDEDEF),
    surface = SurfaceCol,
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = SurfaceCol,
    onSurfaceVariant = Color(0xFFB3B3BC),
    outline = Outline
)

@Composable
fun ChordyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChordyScheme,
        content = content
    )
}
