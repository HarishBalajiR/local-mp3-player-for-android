package com.musicplayer.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class AppTheme { NAVY, EARTHY, GOLDEN, COTTON, ORIGINAL }

private val NavyColorScheme = darkColorScheme(
    primary = Color(0xFF5BC0BE), 
    onPrimary = Color(0xFF0B132B),
    primaryContainer = Color(0xFF5BC0BE),
    onPrimaryContainer = Color(0xFF0B132B),
    secondary = Color(0xFF5BC0BE),
    onSecondary = Color(0xFF0B132B),
    secondaryContainer = Color(0xFF1C2541),
    onSecondaryContainer = Color(0xFFE6F1F8),
    tertiary = Color(0xFF3A506B),
    onTertiary = Color(0xFFE6F1F8),
    background = Color(0xFF0B132B), 
    onBackground = Color(0xFFE6F1F8), 
    surface = Color(0xFF1C2541), 
    onSurface = Color(0xFFE6F1F8), 
    surfaceVariant = Color(0xFF3A506B), 
    onSurfaceVariant = Color(0xFF90A4AE),
    outline = Color(0xFF3A506B),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFFE6F1F8)
)

private val EarthyColorScheme = lightColorScheme(
    primary = Color(0xFF2E8B57), 
    onPrimary = Color(0xFFF5F1E8),
    primaryContainer = Color(0xFFF5F1E8), 
    onPrimaryContainer = Color(0xFF708238),
    secondary = Color(0xFF8A9A5B),
    onSecondary = Color(0xFFF5F1E8),
    secondaryContainer = Color(0xFFA3B18A),
    onSecondaryContainer = Color.DarkGray,
    background = Color(0xFFC89B6D), 
    onBackground = Color(0xFF3B2613), 
    surface = Color(0xFF708238), 
    onSurface = Color(0xFFF5F1E8), 
    surfaceVariant = Color(0xFFA3B18A), 
    onSurfaceVariant = Color(0xFFF5F1E8).copy(alpha = 0.7f),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFFF5F1E8)
)

private val GoldenColorScheme = darkColorScheme(
    primary = Color(0xFFD4A055),
    onPrimary = Color(0xFF2E1F0F),
    primaryContainer = Color(0xFFA0724A),
    onPrimaryContainer = Color(0xFFF5E8D0),
    secondary = Color(0xFFD4A055),
    onSecondary = Color(0xFF2E1F0F),
    secondaryContainer = Color(0xFFA0724A),
    onSecondaryContainer = Color(0xFFF5E8D0),
    background = Color(0xFF2E1F0F),
    onBackground = Color(0xFFF5E8D0),
    surface = Color(0xFF2E1F0F), 
    onSurface = Color(0xFFF5E8D0),
    surfaceVariant = Color(0xFF3E2B1A), 
    onSurfaceVariant = Color(0xFFA0724A),
    outline = Color(0xFFA0724A),
    error = Color(0xFFFF5449),
    onError = Color(0xFFF5E8D0)
)

// Removed Olive theme

private val CottonColorScheme = lightColorScheme(
    primary = Color(0xFFB298E7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DAF5), 
    onPrimaryContainer = Color(0xFF2A1E24),
    secondary = Color(0xFFF5B8D5),
    onSecondary = Color(0xFF2A1E24),
    secondaryContainer = Color(0xFFF9BEDD),
    onSecondaryContainer = Color(0xFF2A1E24),
    background = Color(0xFFB8E3E9),
    onBackground = Color(0xFF2A1E24),
    surface = Color(0xFFDFF1F4), 
    onSurface = Color(0xFF2A1E24),
    surfaceVariant = Color(0xFFCBEAF0), 
    onSurfaceVariant = Color(0xFF2A1E24),
    outline = Color(0xFF6B5A63),
    error = Color(0xFFFF5449),
    onError = Color.White
)

private val OriginalColorScheme = darkColorScheme(
    primary = Color(0xFFFFB599),
    onPrimary = Color(0xFF120E0D),
    primaryContainer = Color(0xFFE89A7E),
    onPrimaryContainer = Color(0xFF120E0D),
    secondary = Color(0xFFE89A7E),
    onSecondary = Color(0xFF120E0D),
    background = Color(0xFF120E0D),
    onBackground = Color(0xFFF2E6E1),
    surface = Color(0xFF1E1715),
    onSurface = Color(0xFFF2E6E1),
    surfaceVariant = Color(0xFF2C2220),
    onSurfaceVariant = Color(0xFFA89A96),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF120E0D)
)

// Premium Typography
private val PremiumTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun MusicPlayerTheme(
    theme: AppTheme = AppTheme.NAVY,
    content: @Composable () -> Unit
) {
    val colorScheme = when(theme) {
        AppTheme.NAVY -> NavyColorScheme
        AppTheme.EARTHY -> EarthyColorScheme
        AppTheme.GOLDEN -> GoldenColorScheme
        AppTheme.COTTON -> CottonColorScheme
        AppTheme.ORIGINAL -> OriginalColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PremiumTypography,
        content = content
    )
}