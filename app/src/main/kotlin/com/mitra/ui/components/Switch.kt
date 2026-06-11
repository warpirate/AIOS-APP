package com.mitra.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.mitra.ui.theme.MitraTokens

/**
 * Toggle switch from `mitra.css §Toggle`. 52x32 visual, wrapped in a 48dp-min hit area.
 *
 * **Audit fix locked in here:** the CSS version was a `<span>` — not focusable, not announced.
 * This Compose port:
 *   - is a real interactive element (clickable)
 *   - announces `Role.Switch` + `toggleableState` so TalkBack reads "on" / "off"
 *   - takes [label] as the screen-reader name — REQUIRED, no default
 *   - wraps the 52x32 chassis in a 56x48 hit area (WCAG 2.5.5)
 *
 * Motion: thumb shift respects [MitraTokens.motionFast] (120ms). System reduced-motion is
 * handled by Compose's `tween` honoring `Settings.Global.ANIMATOR_DURATION_SCALE`.
 */
@Composable
fun MitraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val anim = tween<androidx.compose.ui.graphics.Color>(MitraTokens.motionFast)
    val trackBg by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = anim,
        label = "MitraSwitch.trackBg",
    )
    val borderColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = anim,
        label = "MitraSwitch.border",
    )
    val thumbColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
        animationSpec = anim,
        label = "MitraSwitch.thumb",
    )
    val thumbSize by animateDpAsState(
        if (checked) 20.dp else 16.dp,
        animationSpec = tween(MitraTokens.motionFast),
        label = "MitraSwitch.thumbSize",
    )
    val thumbX by animateDpAsState(
        if (checked) 26.dp else 6.dp,
        animationSpec = tween(MitraTokens.motionFast),
        label = "MitraSwitch.thumbX",
    )
    val thumbY by animateDpAsState(
        if (checked) 4.dp else 6.dp,
        animationSpec = tween(MitraTokens.motionFast),
        label = "MitraSwitch.thumbY",
    )

    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                .semantics {
                    role = Role.Switch
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    contentDescription = label
                }.clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(width = 52.dp, height = 32.dp),
            color = trackBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, borderColor),
        ) {
            Box {
                Box(
                    modifier =
                        Modifier
                            .offset(x = thumbX, y = thumbY)
                            .size(thumbSize)
                            .clip(CircleShape)
                            .background(thumbColor),
                )
            }
        }
    }
}
