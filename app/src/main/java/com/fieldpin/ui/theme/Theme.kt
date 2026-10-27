package com.fieldpin.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Teal400,
    onPrimary = Color(0xFF00201C),
    primaryContainer = TealDeep,
    onPrimaryContainer = Color(0xFFB8F5EC),
    secondary = Green400,
    onSecondary = Color(0xFF00210D),
    secondaryContainer = GreenDeep,
    onSecondaryContainer = Color(0xFFC3F6D3),
    tertiary = White,
    onTertiary = Black,
    background = Black,
    onBackground = Snow,
    surface = Ink,
    onSurface = Snow,
    surfaceVariant = InkHigh,
    onSurfaceVariant = Mist,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Ink,
    surfaceContainer = InkMid,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = InkTop,
    outline = Line,
    outlineVariant = LineSoft,
    error = ErrorRed,
    onError = Black,
    inverseSurface = Snow,
    inverseOnSurface = Black,
    scrim = Black,
)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = White,
    primaryContainer = Color(0xFFCCF5EF),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Green700,
    onSecondary = White,
    secondaryContainer = Color(0xFFD3F5DE),
    onSecondaryContainer = Color(0xFF00210D),
    tertiary = Black,
    onTertiary = White,
    background = White,
    onBackground = Color(0xFF0A0F0E),
    surface = White,
    onSurface = Color(0xFF0A0F0E),
    surfaceVariant = Color(0xFFEAF2F0),
    onSurfaceVariant = Slate,
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF7FBFA),
    surfaceContainer = Color(0xFFF1F7F6),
    surfaceContainerHigh = Color(0xFFE8F1EF),
    surfaceContainerHighest = Color(0xFFDDEAE7),
    outline = Color(0xFFBCCFCB),
    outlineVariant = Color(0xFFDCE8E5),
    error = ErrorRedLight,
    onError = White,
    inverseSurface = Black,
    inverseOnSurface = White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun FieldPinTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content,
    )
}
