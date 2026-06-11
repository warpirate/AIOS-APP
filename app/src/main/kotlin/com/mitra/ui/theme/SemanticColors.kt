package com.mitra.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3's ColorScheme doesn't carry semantic slots for success / warning / info — only
 * `primary`, `secondary`, `tertiary`, and `error`. We use those for the brand palette and
 * surface success / warning / info through a CompositionLocal so screens can read them without
 * hard-coding hex.
 *
 * Usage: `Mitra.semantic.success` inside a composable.
 */
data class MitraSemanticColors(
    val success: Color,
    val warning: Color,
    val info: Color,
)

internal val LocalMitraSemanticColors =
    staticCompositionLocalOf<MitraSemanticColors> {
        error("MitraSemanticColors not provided — wrap your content in MitraTheme.")
    }

/**
 * Theme-extension accessor. Named `Mitra` (not `MitraTheme`) to avoid a name clash with the
 * `MitraTheme(...)` composable function. Read at call-site: `Mitra.semantic.success`.
 */
object Mitra {
    val semantic: MitraSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMitraSemanticColors.current
}
