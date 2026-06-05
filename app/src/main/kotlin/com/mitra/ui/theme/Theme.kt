package com.mitra.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Local & tactile" dark palette (the locked design direction): earthy charcoal-green ground,
// pine = trust/confirm/primary, burnt-orange ember = caution/irreversible. Calm, not corporate.
internal val Pine = Color(0xFF77B083)
private val PineDim = Color(0xFF34503D)
internal val Ember = Color(0xFFCE6F3E)
private val Ground = Color(0xFF0E1311)
private val Surface1 = Color(0xFF161C18)
private val Surface2 = Color(0xFF1F2622)
private val OnGround = Color(0xFFE7ECE6)
private val Muted = Color(0xFF9BA89E)
private val Outline = Color(0xFF38423B)

private val MitraColors = darkColorScheme(
    primary = Pine,
    onPrimary = Ground,
    primaryContainer = PineDim,
    onPrimaryContainer = OnGround,
    secondary = Pine,
    onSecondary = Ground,
    error = Ember,
    onError = Ground,
    background = Ground,
    onBackground = OnGround,
    surface = Surface1,
    onSurface = OnGround,
    surfaceVariant = Surface2,
    onSurfaceVariant = Muted,
    outline = Outline,
)

// Humanist system sans, calm scale with generous line height.
private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val MitraType = Typography(
    headlineLarge = sans(32, 40, FontWeight.SemiBold),
    headlineMedium = sans(26, 32, FontWeight.SemiBold),
    titleLarge = sans(22, 28, FontWeight.SemiBold),
    titleMedium = sans(18, 24, FontWeight.Medium),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 20),
    bodySmall = sans(13, 18),
    labelLarge = sans(15, 20, FontWeight.SemiBold),
    labelMedium = sans(13, 16, FontWeight.Medium),
)

private val MitraShapes = Shapes(
    extraSmall = RoundedCornerShape(8),
    small = RoundedCornerShape(12),
    medium = RoundedCornerShape(18),
    large = RoundedCornerShape(26),
    extraLarge = RoundedCornerShape(32),
)

@Composable
fun MitraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MitraColors,
        typography = MitraType,
        shapes = MitraShapes,
        content = content,
    )
}
