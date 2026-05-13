package com.musicplayer.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.musicplayer.app.ui.screens.ThemeMode

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9B59B6),
    secondary = androidx.compose.ui.graphics.Color(0xFF3498DB),
    tertiary = androidx.compose.ui.graphics.Color(0xFF1ABC9C)
)

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF7D3C98),
    secondary = androidx.compose.ui.graphics.Color(0xFF2980B9),
    tertiary = androidx.compose.ui.graphics.Color(0xFF16A085)
)

@Composable
fun MusicPlayerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}