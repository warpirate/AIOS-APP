package com.mitra.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mitra.permissions.Permission
import com.mitra.permissions.PermissionStatus
import com.mitra.permissions.Permissions

/**
 * Entry mode for [PermissionsScreen].
 *
 * - [Onboarding]: first-run walk-through. Iterates ONLY the onboarding-relevant permissions
 *   (those with `Permission.isOnboarding = true`), starts at the first ungranted one, and calls
 *   [Onboarding.onContinue] as soon as the onboarding set is all-granted.
 *
 * - [Review]: revisit from Settings. Iterates every permission in the snapshot regardless of
 *   `isOnboarding`, always starts at index 0, shows an "Already granted" pill on granted entries
 *   instead of the grant button, and exits via the top-left back arrow (no auto-continue).
 */
sealed interface PermissionsEntryMode {
    data class Onboarding(val onContinue: () -> Unit) : PermissionsEntryMode

    data class Review(val onBack: () -> Unit) : PermissionsEntryMode
}

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
) {
    PermissionsScreen(mode = PermissionsEntryMode.Onboarding(onContinue))
}

@Composable
fun PermissionsScreen(
    mode: PermissionsEntryMode,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var snapshot by remember { mutableStateOf(Permissions.snapshot(context)) }

    val statuses: List<PermissionStatus> =
        when (mode) {
            is PermissionsEntryMode.Onboarding -> snapshot.onboardingStatuses
            is PermissionsEntryMode.Review -> snapshot.statuses
        }

    val initialIdx =
        when (mode) {
            // Onboarding starts at the first ungranted entry so users don't redo granted perms.
            is PermissionsEntryMode.Onboarding -> statuses.indexOfFirst { !it.granted }
            // Review walks through everything from the top so the screen position is stable.
            is PermissionsEntryMode.Review -> 0
        }

    var currentIdx by remember { mutableIntStateOf(initialIdx) }

    // System back: in Review mode return to Settings. In Onboarding mode let the system handle
    // (current behavior — drops out of app, which is fine for first-run).
    if (mode is PermissionsEntryMode.Review) {
        BackHandler(onBack = mode.onBack)
    }

    LaunchedEffect(Unit) {
        if (mode is PermissionsEntryMode.Onboarding && snapshot.onboardingAllGranted) {
            mode.onContinue()
        }
    }

    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    snapshot = Permissions.snapshot(context)
                    if (mode is PermissionsEntryMode.Onboarding) {
                        val refreshed = snapshot.onboardingStatuses
                        if (snapshot.onboardingAllGranted) {
                            mode.onContinue()
                        } else if (currentIdx in refreshed.indices && refreshed[currentIdx].granted) {
                            currentIdx = refreshed.indexOfFirst { !it.granted }
                        }
                    }
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {
            snapshot = Permissions.snapshot(context)
            when (mode) {
                is PermissionsEntryMode.Onboarding -> {
                    val refreshed = snapshot.onboardingStatuses
                    if (snapshot.onboardingAllGranted) {
                        mode.onContinue()
                    } else {
                        currentIdx = refreshed.indexOfFirst { !it.granted }
                    }
                }
                is PermissionsEntryMode.Review -> {
                    // Stay on the same card so the user can see "Already granted" flip in.
                }
            }
        }

    if (currentIdx !in statuses.indices) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }

    val current = statuses[currentIdx].permission
    val currentGranted = statuses[currentIdx].granted
    val totalSteps = statuses.size
    val currentStep = currentIdx + 1
    val isLastStep = currentIdx == statuses.size - 1

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Review-only: top toolbar with back arrow + title.
            if (mode is PermissionsEntryMode.Review) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = mode.onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Permissions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = 24.dp,
                            vertical = if (mode is PermissionsEntryMode.Review) 8.dp else 28.dp,
                        ),
            ) {
                StepDots(total = totalSteps, current = currentStep, statuses = statuses)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Step $currentStep of $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                PermissionPreview(current)
                Spacer(Modifier.height(24.dp))

                // In Review mode, show the "Already granted" pill above the title for granted
                // perms so the user immediately sees they have nothing to do.
                if (mode is PermissionsEntryMode.Review && currentGranted) {
                    GrantedPill()
                    Spacer(Modifier.height(14.dp))
                }

                Text(
                    titleFor(current),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 36.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    whyFor(current),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                ReassurancePill()
                Spacer(Modifier.weight(0.25f))

                // Primary action varies by mode + grant state.
                when {
                    mode is PermissionsEntryMode.Review && currentGranted -> {
                        // Already granted in Review — primary action is "Next" / "Done" navigation.
                        PrimaryActionPill(label = if (isLastStep) "Done" else "Next") {
                            if (isLastStep) {
                                mode.onBack()
                            } else {
                                currentIdx = currentIdx + 1
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GhostActionPill(label = "Revoke in system settings") {
                            Permissions.launchGrant(context, current)
                        }
                    }

                    else -> {
                        // Ungranted (either onboarding or review) — primary is "Grant access".
                        PrimaryActionPill(label = "Grant access") {
                            val runtime = Permissions.runtimePermission(current)
                            if (runtime != null) {
                                runtimeLauncher.launch(runtime)
                            } else {
                                Permissions.launchGrant(context, current)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        GhostActionPill(
                            label =
                                when (mode) {
                                    is PermissionsEntryMode.Onboarding -> "Not now"
                                    is PermissionsEntryMode.Review -> if (isLastStep) "Done" else "Skip"
                                },
                        ) {
                            when (mode) {
                                is PermissionsEntryMode.Onboarding -> {
                                    val next =
                                        statuses
                                            .drop(currentIdx + 1)
                                            .indexOfFirst { !it.granted }
                                            .let { if (it >= 0) currentIdx + 1 + it else -1 }
                                    if (next < 0) mode.onContinue() else currentIdx = next
                                }
                                is PermissionsEntryMode.Review -> {
                                    if (isLastStep) mode.onBack() else currentIdx = currentIdx + 1
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantedPill() {
    Surface(
        color = Color(0xFF4F7A3F).copy(alpha = 0.18f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF4F7A3F),
                modifier = Modifier.size(14.dp),
            )
            Text(
                "Already granted",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF4F7A3F),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StepDots(total: Int, current: Int, statuses: List<PermissionStatus>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..total) {
            val status = statuses.getOrNull(i - 1)
            val isCurrent = i == current
            val isGranted = status?.granted == true

            // Dot palette:
            // - Current step: primary (clay), wider bar
            // - Granted step (review mode): success green
            // - Pending step: outline
            val color =
                when {
                    isCurrent -> MaterialTheme.colorScheme.primary
                    isGranted -> Color(0xFF8FB97D)
                    else -> MaterialTheme.colorScheme.outline
                }
            Surface(
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(width = if (isCurrent) 28.dp else 8.dp, height = 8.dp),
            ) {}
        }
    }
}

@Composable
private fun ReassurancePill() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Nothing leaves your phone. You can revoke any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrimaryActionPill(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun GhostActionPill(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun iconFor(p: Permission): ImageVector =
    when (p) {
        Permission.WRITE_SETTINGS -> Icons.Filled.BrightnessMedium
        Permission.NOTIFICATION_POLICY -> Icons.Filled.NotificationsOff
        Permission.BLUETOOTH_CONNECT -> Icons.Filled.Bluetooth
        Permission.READ_CONTACTS -> Icons.Filled.Contacts
        Permission.CALL_PHONE -> Icons.Filled.Phone
        Permission.SEND_SMS -> Icons.Filled.Sms
    }

private fun titleFor(p: Permission): String =
    when (p) {
        Permission.WRITE_SETTINGS -> "Change system settings"
        Permission.NOTIFICATION_POLICY -> "Control Do Not Disturb"
        Permission.BLUETOOTH_CONNECT -> "Switch Bluetooth on and off"
        Permission.READ_CONTACTS -> "Find contacts"
        Permission.CALL_PHONE -> "Place calls"
        Permission.SEND_SMS -> "Send text messages"
    }

private fun whyFor(p: Permission): String =
    when (p) {
        Permission.WRITE_SETTINGS ->
            "Mitra adjusts brightness, auto-rotate, and screen timeout when you ask."
        Permission.NOTIFICATION_POLICY ->
            "Mitra turns Do Not Disturb on or off, and switches the ringer to silent."
        Permission.BLUETOOTH_CONNECT ->
            "Mitra switches Bluetooth on and off directly. Without this, Mitra opens the Bluetooth page instead."
        Permission.READ_CONTACTS ->
            "Mitra looks up phone numbers when you ask. Without this, name lookups won't work."
        Permission.CALL_PHONE ->
            "Mitra places calls from inside the app when you say 'call X'. Every call is shown for confirm before it dials."
        Permission.SEND_SMS ->
            "Mitra sends texts directly when you say 'text X <message>'. Every message is shown for confirm before it sends, and SMS counts toward your carrier plan."
    }

@Composable
private fun PermissionPreview(perm: Permission) {
    val resId =
        when (perm) {
            Permission.WRITE_SETTINGS -> com.mitra.R.raw.perm_settings
            Permission.NOTIFICATION_POLICY -> com.mitra.R.raw.perm_dnd
            Permission.BLUETOOTH_CONNECT -> com.mitra.R.raw.perm_bluetooth
            // No preview video for the reactive-grant perms; render a calm icon placeholder.
            Permission.READ_CONTACTS -> {
                IconPreviewPlaceholder(icon = Icons.Filled.Contacts)
                return
            }
            Permission.CALL_PHONE -> {
                IconPreviewPlaceholder(icon = Icons.Filled.Phone)
                return
            }
            Permission.SEND_SMS -> {
                IconPreviewPlaceholder(icon = Icons.Filled.Sms)
                return
            }
        }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            LoopingVideoView(resId = resId, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun IconPreviewPlaceholder(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun LoopingVideoView(resId: Int, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.widget.VideoView(ctx).apply {
                val uri = android.net.Uri.parse("android.resource://${ctx.packageName}/$resId")
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    start()
                }
                setOnErrorListener { _, _, _ -> true }
            }
        },
    )
}
