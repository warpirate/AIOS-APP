package com.mitra.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The app bar from `mitra.css §App bar` + the icon button from `§iconbtn`.
 *
 * **Audit fix locked in here:** every icon button REQUIRES a [contentDescription]-equivalent
 * (`label`) parameter — no default — because the CSS version had bare `<button>`s with no
 * `aria-label` that read as "button button" to SR users.
 *
 * Use [MitraTopAppBar] for the screen header; assemble `actions` from [MitraIconButton]s.
 */
@Composable
fun MitraTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(start = 16.dp, end = 4.dp)
                .semantics {
                    role = Role.Image
                    contentDescription = title
                },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (navigationIcon != null) navigationIcon()
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = if (navigationIcon == null) 0.dp else 4.dp).weight(1f),
            )
            actions()
        }
    }
}

/**
 * 48x48 icon button — the floor for WCAG 2.5.5. Required [label] becomes the `contentDescription`
 * so TalkBack reads "Back" / "Audit history" / etc. instead of "button".
 */
@Composable
fun MitraIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .size(48.dp)
                .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
                .semantics { contentDescription = label },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        shape = CircleShape,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { content() }
    }
}
