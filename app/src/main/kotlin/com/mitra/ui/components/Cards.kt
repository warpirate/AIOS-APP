package com.mitra.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mitra.ui.theme.MitraTokens

/**
 * Surface cards from `mitra.css §Surfaces`. Light mode uses an outline border for separation
 * (audit-compliant: 1dp `outline` vs heavy shadow); dark mode loses the border and relies on
 * tint lift (`bg` → `surface`).
 *
 * Two variants:
 *   [MitraCard]        — `var(--surface)` background, the standard card
 *   [MitraCardVariant] — `var(--surfaceVariant)` background, for muted callouts (e.g. info card,
 *                        privacy nudge in onboarding)
 *
 * Both render with `radiusLg` (20dp). Override `shape` for one-off needs but prefer the token
 * default.
 */
@Composable
fun MitraCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    val isLight = MaterialTheme.colorScheme.background == MitraTokens.Light.bg
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MitraTokens.radiusLg,
        border = if (isLight) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        content = { Padded(content) },
    )
}

@Composable
fun MitraCardVariant(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MitraTokens.radiusLg,
        content = { Padded(content) },
    )
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.padding(MitraTokens.md)) { content() }
}
