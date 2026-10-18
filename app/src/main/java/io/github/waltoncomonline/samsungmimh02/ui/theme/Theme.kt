package io.github.waltoncomonline.samsungmimh02.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryText = Color(0xFF17211D)
val AppCanvas = Color(0xFFF5F3EE)
val AppSurface = Color(0xFFFFFDF8)
val AccentAmber = Color(0xFFF4B942)
val PrimaryGreen = Color(0xFF167D5A)
val InfoBlue = Color(0xFF2C6E9B)
val ErrorRed = Color(0xFFC84F4F)
val SecondaryText = Color(0xFF68736D)
val Outline = Color(0xFFD9DDD8)

private val AppColors = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9E2),
    onPrimaryContainer = PrimaryText,
    secondary = AccentAmber,
    onSecondary = PrimaryText,
    secondaryContainer = Color(0xFFFFE8AF),
    onSecondaryContainer = PrimaryText,
    tertiary = InfoBlue,
    tertiaryContainer = Color(0xFFD8EAF4),
    onTertiaryContainer = PrimaryText,
    error = ErrorRed,
    background = AppCanvas,
    onBackground = PrimaryText,
    surface = AppSurface,
    onSurface = PrimaryText,
    surfaceVariant = Color(0xFFE9EEE9),
    onSurfaceVariant = SecondaryText,
    outline = Outline,
)

@Composable
fun SamsungMimH02Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
