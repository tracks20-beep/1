package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val VeloDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = CoralRose,
    onSecondary = OnCoralRose,
    secondaryContainer = CoralRoseContainer,
    onSecondaryContainer = Color(0xFFFFD5DC),
    tertiary = AmberGlow,
    onTertiary = Color(0xFF281A00),
    tertiaryContainer = AmberGlowContainer,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = ObsidianBackground,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = SlateOutline,
    error = CoralRose
)

private val VeloLightColorScheme = lightColorScheme(
    primary = DeepCyan,
    onPrimary = Color.White,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = Color(0xFF083344),
    secondary = DeepCoral,
    onSecondary = Color.White,
    secondaryContainer = DeepCoralContainer,
    onSecondaryContainer = Color(0xFF4C0519),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = DaylightBackground,
    onBackground = DaylightTextPrimary,
    surface = DaylightSurface,
    onSurface = DaylightTextPrimary,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = DaylightTextSecondary,
    outline = DaylightOutline
)

val VeloPlayShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VeloPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VeloDarkColorScheme
        else -> VeloLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VeloPlayTypography,
        shapes = VeloPlayShapes,
        content = content
    )
}
