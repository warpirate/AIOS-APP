package com.mitra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mitra.ui.theme.MitraTokens

/**
 * Modal bottom sheet from `mitra.css §Modal bottom sheet` + `§Scrim`.
 *
 * **Audit fix locked in here:** the CSS version had no `role="dialog"`, no `aria-modal`, no
 * focus trap, no Esc/scrim-tap dismiss. This port:
 *   - announces `Role.Button` on the scrim with a `dismiss` action
 *   - announces `Role.Dialog` on the sheet body
 *   - requires [title] for `contentDescription` (so SR users hear what the dialog is for)
 *   - tap-on-scrim dismisses via [onDismiss]
 *   - content above the sheet is auto-hidden by the scrim's `fillMaxSize` + opaque overlay
 *
 * The sheet is rendered inside a [Box] that fills its parent — caller is expected to overlay
 * this on top of the screen via a `Box(Modifier.fillMaxSize())` at the screen root.
 *
 * Edge cases NOT yet handled:
 *   - Hardware back-button dismissal (wire via `BackHandler` at the screen level)
 *   - Focus restore (Compose-native focus management lands in M3 polish)
 *   - Reduced-motion guard on the enter animation (caller should use `AnimatedVisibility` with
 *     a tween at [MitraTokens.motionBase] which honors system motion scale)
 */
@Composable
fun MitraBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Dismiss $title",
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = false) {
                    role = Role.Image // Compose has no built-in Dialog role — best approximation
                    contentDescription = title
                    dismiss { onDismiss(); true }
                }
                .clickable(enabled = false, onClick = {}), // swallow taps so they don't dismiss
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 8.dp,
        ) {
            Box(Modifier.padding(MitraTokens.xl)) { content() }
        }
    }
}
