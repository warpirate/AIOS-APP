package com.mitra.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitra.ui.theme.Mitra
import com.mitra.ui.theme.MitraTokens

/**
 * State pill rendered on action cards. Five variants:
 *   CONFIRM   — neutral, surfaceVariant fill + outline border in light mode
 *   RUNNING   — info fill
 *   DONE      — success fill
 *   CANCELLED — neutral, same as CONFIRM
 *   FAILED    — danger fill
 *
 * **Audit fix locked in here:** semantic-color fills take DARK text — never `#FFFFFF`. White
 * on the dark-mode semantic fills drops to 2.2–3.2:1 and fails AA. See
 * `docs/design/a11y-audit-2026-06-05.md` + the color-usage rule added to `tokens.md`.
 */
enum class PillKind { CONFIRM, RUNNING, DONE, CANCELLED, FAILED }

@Composable
fun MitraStatePill(
    kind: PillKind,
    label: String,
    modifier: Modifier = Modifier,
) {
    val isLight = MaterialTheme.colorScheme.background == MitraTokens.Light.bg
    val semantic = Mitra.semantic
    val onSemantic = if (isLight) MitraTokens.Light.onSurface else MitraTokens.Dark.bg

    val fill: Color
    val fg: Color
    val border: Color
    when (kind) {
        PillKind.CONFIRM, PillKind.CANCELLED -> {
            fill = MaterialTheme.colorScheme.surfaceVariant
            fg = MaterialTheme.colorScheme.onSurfaceVariant
            border = if (isLight) MaterialTheme.colorScheme.outline else Color.Transparent
        }
        PillKind.RUNNING -> {
            fill = semantic.info
            fg = onSemantic
            border = Color.Transparent
        }
        PillKind.DONE -> {
            fill = semantic.success
            fg = onSemantic
            border = Color.Transparent
        }
        PillKind.FAILED -> {
            fill = MaterialTheme.colorScheme.error
            fg = onSemantic
            border = Color.Transparent
        }
    }

    Surface(
        modifier = modifier.defaultMinSize(minHeight = 22.dp),
        color = fill,
        contentColor = fg,
        shape = RoundedCornerShape(11.dp),
        border = if (border == Color.Transparent) null else BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                ),
        )
    }
}
