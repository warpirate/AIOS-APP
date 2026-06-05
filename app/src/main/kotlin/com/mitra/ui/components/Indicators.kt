package com.mitra.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Loading affordances from `mitra.css §dotpulse` and `§spin`.
 *
 * **Audit fix locked in here:** both indicators announce themselves to screen readers via
 * `LiveRegionMode.Polite` + [contentDescription]. The CSS versions were decorative `<div>`s
 * with no SR exposure — users heard nothing during "Loading model" / "Checking permission" /
 * "Thinking".
 *
 * Reduced motion: Compose's `tween` honors `Settings.Global.ANIMATOR_DURATION_SCALE` — when
 * the user dials animations down to 0 the pulse / spin collapse to a static dot / ring.
 */
@Composable
fun MitraDotPulse(
    label: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "dotpulse")
    val alphas = (0..2).map { i ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = androidx.compose.animation.core.StartOffset(i * 200),
            ),
            label = "dotpulse.$i",
        ).value
    }
    Row(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = label
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        alphas.forEach { a ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .alpha(a)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

/**
 * Spinner ring from `mitra.css §spin`. Material 3's `CircularProgressIndicator` already covers
 * the visual; this wrapper just adds the live-region + content-description that the audit
 * required. 18dp visual, 2dp stroke per spec.
 */
@Composable
fun MitraSpinner(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = label
        },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
