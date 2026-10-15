package com.pupil.hanzi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Colours for the character canvas.
 *
 * These are deliberately NOT part of the Material scheme: a tian zi ge is
 * ink on paper, so the grid, outline and glyph keep their own values in both
 * themes. Deriving them from the scheme made the strokes disappear in dark
 * mode, where a dark outline on a dark cell is invisible.
 */
data class CanvasColors(
    /** The 米字格 lines. */
    val grid: Color,
    /** The silhouette of strokes not yet written. */
    val outline: Color,
    /** Finished strokes. */
    val stroke: Color,
    /** The radical. */
    val radical: Color,
    /** The pen-path guide shown while animating. */
    val guide: Color,
    /** Background of an empty cell waiting for its strokes. */
    val cellBackground: Color,
)

val LightCanvasColors = CanvasColors(
    grid = Color(0xFFBDBDBD),
    /** cnchar's default outlineColor is #DDD, drawn at full opacity. */
    outline = Color(0xFFDDDDDD),
    stroke = Color(0xFF2B2B2B),
    radical = Color(0xFFE74C3C),
    guide = Color(0xFF4A90D9),
    cellBackground = Color(0xFFFFFFFF),
)

val DarkCanvasColors = CanvasColors(
    // Lighter than the Material dark surfaces so the grid stays visible.
    grid = Color(0xFF7A7A7A),
    /**
     * The dark-mode counterpart of #DDD. The shadow must sit clearly between
     * the cell and the ink: on a #2A2A2A cell that is a light grey, not a
     * dark one, or it disappears into the background instead of showing the
     * shape the pupil is about to write.
     */
    outline = Color(0xFF9E9E9E),
    // Near-white: ink on a dark sheet, not dark ink on a dark sheet.
    stroke = Color(0xFFF2F2F2),
    radical = Color(0xFFFF8A80),
    guide = Color(0xFF8AB4F8),
    cellBackground = Color(0xFF2A2A2A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3D6B8E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE4F5),
    onPrimaryContainer = Color(0xFF0B2537),
    secondary = Color(0xFF50606E),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    outlineVariant = Color(0xFFC1C7CE),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF191C1E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9CCBEC),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF1F4A66),
    onPrimaryContainer = Color(0xFFCFE4F5),
    secondary = Color(0xFFB7C9D8),
    surface = Color(0xFF1B1E20),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outlineVariant = Color(0xFF41484D),
    background = Color(0xFF121416),
    onBackground = Color(0xFFE2E2E5),
)

/** The canvas palette for the current theme. */
val LocalCanvasColors = androidx.compose.runtime.staticCompositionLocalOf {
    LightCanvasColors
}

@Composable
fun HanziliteracyandroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val canvas = if (darkTheme) DarkCanvasColors else LightCanvasColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (LocalContext.current as? Activity)?.window
        SideEffect {
            window?.statusBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window!!, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalCanvasColors provides canvas) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content,
        )
    }
}