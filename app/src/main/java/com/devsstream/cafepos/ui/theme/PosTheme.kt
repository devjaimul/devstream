package com.devsstream.cafepos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Coffee colours: espresso for actions, caramel for "in progress", mint for "done".
private val Light = lightColorScheme(
    primary = Color(0xFF6B4226),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E1CF),
    onPrimaryContainer = Color(0xFF2C1608),
    secondary = Color(0xFFB0632A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3C7),
    onSecondaryContainer = Color(0xFF3D1F00),
    tertiary = Color(0xFF2F7D5A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F0E1),
    onTertiaryContainer = Color(0xFF00261A),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF8F4),
    onBackground = Color(0xFF1F1B17),
    surface = Color(0xFFFCF8F4),
    onSurface = Color(0xFF1F1B17),
    surfaceVariant = Color(0xFFF0E6DC),
    onSurfaceVariant = Color(0xFF51453B),
    outline = Color(0xFF84756A),
    outlineVariant = Color(0xFFE2D6CB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F1EA),
    surfaceContainer = Color(0xFFF3EBE3),
    surfaceContainerHigh = Color(0xFFEDE4DB),
    surfaceContainerHighest = Color(0xFFE7DED5),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE7BC98),
    onPrimary = Color(0xFF46280F),
    primaryContainer = Color(0xFF5E3C21),
    onPrimaryContainer = Color(0xFFF5E1CF),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF4C2700),
    secondaryContainer = Color(0xFF6C3A0A),
    onSecondaryContainer = Color(0xFFFFE3C7),
    tertiary = Color(0xFF8FD6B2),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF0D5139),
    onTertiaryContainer = Color(0xFFD3F0E1),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17120E),
    onBackground = Color(0xFFECE0D7),
    surface = Color(0xFF17120E),
    onSurface = Color(0xFFECE0D7),
    surfaceVariant = Color(0xFF51453B),
    onSurfaceVariant = Color(0xFFD7C9BD),
    outline = Color(0xFF9F8E82),
    outlineVariant = Color(0xFF51453B),
    surfaceContainerLowest = Color(0xFF120D0A),
    surfaceContainerLow = Color(0xFF1F1A16),
    surfaceContainer = Color(0xFF241E1A),
    surfaceContainerHigh = Color(0xFF2E2824),
    surfaceContainerHighest = Color(0xFF39322E),
)

private val base = Typography()

private val PosTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val PosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = PosTypography,
        shapes = PosShapes,
        content = content,
    )
}
