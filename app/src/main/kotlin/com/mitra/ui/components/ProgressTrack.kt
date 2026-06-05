package com.mitra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Determinate horizontal progress track from `mitra.css §Progress bar`. 6dp tall, 3dp radius.
 *
 * **Audit fix locked in here:** the CSS version was a decorative `<div>` with no SR exposure —
 * "1.43 GB of 2.59 GB" was an orphan caption. This port carries [progress] in `0f..1f`,
 * announces `progressBarRangeInfo`, and takes a REQUIRED [label] for `contentDescription`.
 *
 * For indeterminate state, fall back to Material 3's `LinearProgressIndicator()` — this
 * component is determinate-only by design (model download is the only V1 consumer).
 */
@Composable
fun MitraProgressTrack(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
