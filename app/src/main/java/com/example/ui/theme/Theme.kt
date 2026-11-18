package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val StudioDarkColorScheme = darkColorScheme(
    primary = CoralFlame,
    onPrimary = Color(0xFF1A0703),
    primaryContainer = CoralContainer,
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF031E2E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = Color(0xFFC9EEFF),
    tertiary = AmberGold,
    onTertiary = Color(0xFF241600),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFFFFE2B3),
    background = ObsidianBg,
    onBackground = CrispWhite,
    surface = SlateSurface,
    onSurface = CrispWhite,
    surfaceVariant = SlateSurfaceHigh,
    onSurfaceVariant = MutedSlate,
    outline = SlateOutline,
    outlineVariant = Color(0xFF1E263B),
    error = SoftError,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFEE2E2)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CoralDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3E0E03),
    secondary = CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F0FF),
    onSecondaryContainer = Color(0xFF00273D),
    tertiary = AmberGold,
    onTertiary = Color(0xFF241600),
    tertiaryContainer = Color(0xFFFFECC9),
    onTertiaryContainer = Color(0xFF362100),
    background = DaylightBg,
    onBackground = DeepInk,
    surface = DaylightSurface,
    onSurface = DeepInk,
    surfaceVariant = DaylightSurfaceHigh,
    onSurfaceVariant = SubtleInk,
    outline = DaylightOutline,
    outlineVariant = Color(0xFFE2E8F0)
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ReelCraftTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StudioDarkColorScheme else StudioLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ReelCraftTypography,
        shapes = StudioShapes,
        content = content
    )
}
