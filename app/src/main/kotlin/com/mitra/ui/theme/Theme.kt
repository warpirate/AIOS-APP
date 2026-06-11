package com.mitra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The Material 3 theme. Wires light/dark color schemes from [MitraTokens], plus the locked
 * [MitraTypography] and the radius-token-mapped shape set. Semantic colors (success/warning/info)
 * are exposed through a CompositionLocal because Material 3 has no slots for them — read via
 * `Mitra.semantic.*` inside composables.
 *
 * Do not introduce a `dynamicColor` path. Mitra is clay; system-derived accents drift the brand.
 * See docs/design/tokens.md section 9 ("What not to add").
 */
private val MitraShapes =
    Shapes(
        extraSmall = MitraTokens.radiusSm,
        small = MitraTokens.radiusMd,
        medium = MitraTokens.radiusMd,
        large = MitraTokens.radiusLg,
        extraLarge = MitraTokens.radiusXl,
    )

private val LightColors =
    lightColorScheme(
        primary = MitraTokens.Light.primary,
        onPrimary = MitraTokens.Light.onPrimary,
        primaryContainer = MitraTokens.Light.primaryContainer,
        onPrimaryContainer = MitraTokens.Light.onPrimaryContainer,
        secondary = MitraTokens.Light.primary,
        onSecondary = MitraTokens.Light.onPrimary,
        tertiary = MitraTokens.Light.info,
        onTertiary = MitraTokens.Light.onPrimary,
        background = MitraTokens.Light.bg,
        onBackground = MitraTokens.Light.onSurface,
        surface = MitraTokens.Light.surface,
        onSurface = MitraTokens.Light.onSurface,
        surfaceVariant = MitraTokens.Light.surfaceVariant,
        onSurfaceVariant = MitraTokens.Light.onSurfaceVariant,
        outline = MitraTokens.Light.outline,
        error = MitraTokens.Light.danger,
        onError = MitraTokens.Light.onPrimary,
    )

private val DarkColors =
    darkColorScheme(
        primary = MitraTokens.Dark.primary,
        onPrimary = MitraTokens.Dark.onPrimary,
        primaryContainer = MitraTokens.Dark.primaryContainer,
        onPrimaryContainer = MitraTokens.Dark.onPrimaryContainer,
        secondary = MitraTokens.Dark.primary,
        onSecondary = MitraTokens.Dark.onPrimary,
        tertiary = MitraTokens.Dark.info,
        onTertiary = MitraTokens.Dark.bg,
        background = MitraTokens.Dark.bg,
        onBackground = MitraTokens.Dark.onSurface,
        surface = MitraTokens.Dark.surface,
        onSurface = MitraTokens.Dark.onSurface,
        surfaceVariant = MitraTokens.Dark.surfaceVariant,
        onSurfaceVariant = MitraTokens.Dark.onSurfaceVariant,
        outline = MitraTokens.Dark.outline,
        error = MitraTokens.Dark.danger,
        onError = MitraTokens.Dark.bg,
    )

private val LightSemantics =
    MitraSemanticColors(
        success = MitraTokens.Light.success,
        warning = MitraTokens.Light.warning,
        info = MitraTokens.Light.info,
    )

private val DarkSemantics =
    MitraSemanticColors(
        success = MitraTokens.Dark.success,
        warning = MitraTokens.Dark.warning,
        info = MitraTokens.Dark.info,
    )

@Composable
fun MitraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val semantic = if (darkTheme) DarkSemantics else LightSemantics
    CompositionLocalProvider(LocalMitraSemanticColors provides semantic) {
        MaterialTheme(
            colorScheme = colors,
            typography = MitraTypography,
            shapes = MitraShapes,
            content = content,
        )
    }
}
