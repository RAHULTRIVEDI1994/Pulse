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

private val PodPulseDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = OnCyanDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = EmeraldSecondary,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = AmberTertiary,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = RoseError,
    onError = Color(0xFF4C0519),
    errorContainer = RoseContainerDark,
    onErrorContainer = Color(0xFFFFE4E6),
    background = ObsidianBackground,
    onBackground = IceWhite,
    surface = SlateSurface,
    onSurface = IceWhite,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = MutedSlate,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline
)

private val PodPulseLightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = Color(0xFF082F49),
    secondary = EmeraldSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = Color(0xFF022C22),
    tertiary = AmberTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF451A03),
    error = RoseErrorLight,
    onError = Color.White,
    errorContainer = RoseContainerLight,
    onErrorContainer = Color(0xFF4C0519),
    background = CeramicBackground,
    onBackground = DeepInk,
    surface = CeramicSurface,
    onSurface = DeepInk,
    surfaceVariant = CeramicSurfaceElevated,
    onSurfaceVariant = SlateInk,
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = CeramicOutline
)

val PodPulseShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PodPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PodPulseDarkColorScheme
        else -> PodPulseLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PodPulseTypography,
        shapes = PodPulseShapes,
        content = content
    )
}
