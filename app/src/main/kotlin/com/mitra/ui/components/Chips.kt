package com.mitra.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitra.ui.theme.MitraTokens

/**
 * Chip (non-selectable) and FilterChip (selectable) from `mitra.css §Chips`.
 *
 * **Audit fix locked in here:** both shapes carry a `min-height = 48dp` hit area even though
 * the visible chip is 36dp tall — the extra padding is invisible but reaches the WCAG 2.5.5
 * floor. The CSS used a flat 36dp hit area which failed the audit.
 *
 * FilterChip announces selection via `Role.Button` + `semantics.selected = true` so screen
 * readers don't have to read "selected" from color alone. Adds an `aria-pressed`-equivalent.
 */
@Composable
fun MitraChip(
    label: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val isLight = MaterialTheme.colorScheme.background == MitraTokens.Light.bg
    val chip: @Composable () -> Unit = {
        Surface(
            modifier = modifier.defaultMinSize(minHeight = 48.dp),
            color = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(18.dp),
            border = if (isLight) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        ) {
            ChipBody(label, leading)
        }
    }
    if (onClick != null) {
        androidx.compose.foundation.layout
            .Box(Modifier.clickable(role = Role.Button, onClick = onClick)) { chip() }
    } else {
        chip()
    }
}

@Composable
fun MitraFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    androidx.compose.foundation.layout.Box(
        modifier =
            modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    role = Role.Button
                    this.selected = selected
                }.clickable(onClick = onClick),
    ) {
        Surface(
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, border),
            modifier = Modifier.defaultMinSize(minHeight = 36.dp),
        ) {
            ChipBody(label, leading)
        }
    }
}

@Composable
private fun ChipBody(label: String, leading: @Composable (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        if (leading != null) leading()
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
