package com.mitra.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
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
import com.mitra.permissions.PermissionSnapshot
import com.mitra.permissions.Permissions

@Composable
fun PermissionsScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var snapshot by remember { mutableStateOf(Permissions.snapshot(context)) }
    var currentIdx by remember { mutableIntStateOf(snapshot.firstUngrantedIndex()) }

    LaunchedEffect(Unit) {
        if (snapshot.allGranted) onContinue()
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = Permissions.snapshot(context)
                if (snapshot.allGranted) {
                    onContinue()
                } else if (currentIdx in snapshot.statuses.indices && snapshot.statuses[currentIdx].granted) {
                    currentIdx = snapshot.firstUngrantedIndex()
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        snapshot = Permissions.snapshot(context)
        if (snapshot.allGranted) onContinue() else currentIdx = snapshot.firstUngrantedIndex()
    }

    if (currentIdx !in snapshot.statuses.indices) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }

    val current = snapshot.statuses[currentIdx].permission
    val totalUngranted = snapshot.statuses.count { !it.granted }
    val stepInUngranted = snapshot.statuses.take(currentIdx + 1).count { !it.granted }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            StepDots(total = totalUngranted, current = stepInUngranted)
            Spacer(Modifier.height(10.dp))
            Text(
                "Step $stepInUngranted of $totalUngranted",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PermissionPreview(current)
            Spacer(Modifier.height(24.dp))
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
            PrimaryActionPill(label = "Grant access") {
                val runtime = Permissions.runtimePermission(current)
                if (runtime != null) runtimeLauncher.launch(runtime)
                else Permissions.launchGrant(context, current)
            }
            Spacer(Modifier.height(8.dp))
            GhostActionPill(label = "Not now") {
                val next = snapshot.statuses.drop(currentIdx + 1)
                    .indexOfFirst { !it.granted }
                    .let { if (it >= 0) currentIdx + 1 + it else -1 }
                if (next < 0) onContinue() else currentIdx = next
            }
        }
    }
}

@Composable
private fun StepDots(total: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 1..total) {
            val active = i <= current
            Surface(
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
                modifier = Modifier.size(width = if (active) 28.dp else 8.dp, height = 8.dp),
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
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GhostActionPill(label: String, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private fun PermissionSnapshot.firstUngrantedIndex(): Int =
    statuses.indexOfFirst { !it.granted }

private fun iconFor(p: Permission): ImageVector = when (p) {
    Permission.WRITE_SETTINGS -> Icons.Filled.BrightnessMedium
    Permission.NOTIFICATION_POLICY -> Icons.Filled.NotificationsOff
    Permission.BLUETOOTH_CONNECT -> Icons.Filled.Bluetooth
}

private fun titleFor(p: Permission): String = when (p) {
    Permission.WRITE_SETTINGS -> "Change system settings"
    Permission.NOTIFICATION_POLICY -> "Control Do Not Disturb"
    Permission.BLUETOOTH_CONNECT -> "Switch Bluetooth on and off"
}

private fun whyFor(p: Permission): String = when (p) {
    Permission.WRITE_SETTINGS ->
        "Mitra adjusts brightness, auto-rotate, and screen timeout when you ask."
    Permission.NOTIFICATION_POLICY ->
        "Mitra turns Do Not Disturb on or off, and switches the ringer to silent."
    Permission.BLUETOOTH_CONNECT ->
        "Mitra switches Bluetooth on and off directly. Without this, Mitra opens the Bluetooth page instead."
}

@Composable
private fun PermissionPreview(perm: Permission) {
    val resId = when (perm) {
        Permission.WRITE_SETTINGS -> com.mitra.R.raw.perm_settings
        Permission.NOTIFICATION_POLICY -> com.mitra.R.raw.perm_dnd
        Permission.BLUETOOTH_CONNECT -> com.mitra.R.raw.perm_bluetooth
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


