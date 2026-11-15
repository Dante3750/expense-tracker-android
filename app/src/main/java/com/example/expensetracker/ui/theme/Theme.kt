package com.example.expensetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C5CAB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE2FB),
    onPrimaryContainer = Color(0xFF0D366B),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC5F4),
    onPrimary = Color(0xFF0D366B),
    primaryContainer = Color(0xFF184F95),
    onPrimaryContainer = Color(0xFFCDE2FB),
    secondary = Color(0xFFBBC7DB),
    tertiary = Color(0xFFD6BEE4),
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Material You wallpaper colours on Android 12+
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Fixed categorical palette for charts: eight hues chosen to stay distinguishable under the
 * common colour-vision deficiencies, with a separately stepped set for dark surfaces.
 *
 * A category stores a slot index, so its colour follows the category (never its rank in a
 * chart) and stays the same hue in both themes. Because three of the light colours are below
 * 3:1 contrast on a light surface, every chart that uses them also shows a labelled legend.
 */
object ChartPalette {
    private val light = listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
    )
    private val dark = listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF0CA30C), Color(0xFF9085E9), Color(0xFFE66767),
    )

    fun color(slot: Int, darkTheme: Boolean): Color {
        val colors = if (darkTheme) dark else light
        return colors[slot.mod(colors.size)]
    }
}

@Composable
@ReadOnlyComposable
fun categoryColor(slot: Int): Color = ChartPalette.color(slot, isSystemInDarkTheme())
