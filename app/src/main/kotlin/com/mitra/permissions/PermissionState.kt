package com.mitra.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Single source of truth for every system-level permission Mitra asks for.
 *
 * Three kinds of grant Android forces apps to handle differently:
 *   - **Runtime** (BLUETOOTH_CONNECT): standard ActivityResult permission dialog. Easy.
 *   - **Special / appop** (WRITE_SETTINGS, ACCESS_NOTIFICATION_POLICY): no dialog — Mitra must
 *     launch the corresponding system Settings page and let the user flip a toggle.
 *
 * Each [Spec] declares what to read to check current state and how to launch the grant surface.
 * The PermissionsScreen calls [snapshot] on resume so the row badges always reflect reality.
 */
enum class Permission(
    val key: String,
) {
    WRITE_SETTINGS("write_settings"),
    NOTIFICATION_POLICY("notification_policy"),
    BLUETOOTH_CONNECT("bluetooth_connect"),
}

data class PermissionStatus(
    val permission: Permission,
    val granted: Boolean,
)

data class PermissionSnapshot(
    val statuses: List<PermissionStatus>,
) {
    val allGranted: Boolean get() = statuses.all { it.granted }
    val anyMissing: Boolean get() = statuses.any { !it.granted }
}

object Permissions {
    fun snapshot(context: Context): PermissionSnapshot =
        PermissionSnapshot(
            statuses =
                listOf(
                    PermissionStatus(Permission.WRITE_SETTINGS, isWriteSettingsGranted(context)),
                    PermissionStatus(Permission.NOTIFICATION_POLICY, isNotificationPolicyGranted(context)),
                    PermissionStatus(Permission.BLUETOOTH_CONNECT, isBluetoothConnectGranted(context)),
                ),
        )

    fun launchGrant(context: Context, permission: Permission) {
        when (permission) {
            Permission.WRITE_SETTINGS ->
                launchSpecial(
                    context,
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}")),
                )
            Permission.NOTIFICATION_POLICY ->
                launchSpecial(
                    context,
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                )
            Permission.BLUETOOTH_CONNECT -> Unit // requested via ActivityResult in the Composable
        }
    }

    /** Runtime permission strings (used by the ActivityResult launcher in the Composable). */
    fun runtimePermission(permission: Permission): String? =
        when (permission) {
            Permission.BLUETOOTH_CONNECT -> Manifest.permission.BLUETOOTH_CONNECT
            else -> null
        }

    private fun launchSpecial(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun isWriteSettingsGranted(context: Context): Boolean =
        Settings.System.canWrite(context)

    private fun isNotificationPolicyGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun isBluetoothConnectGranted(context: Context): Boolean {
        // Below API 31 the BLUETOOTH_ADMIN install-permission covers it (no runtime check needed).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }
}
