package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Launches an installed app by friendly name or package id. SideEffect.None — opening an app is
 * instant and trivially reversible (the user just goes back).
 *
 * Matching strategy, in order of precedence:
 * 1. Exact package id (e.g. "com.spotify.music")
 * 2. Case-insensitive exact label match (e.g. "Spotify")
 * 3. Case-insensitive prefix match on label
 * 4. Substring match on label
 *
 * Tie-break: whichever resolved app has a launcher intent first wins. We never silently launch a
 * partial match if there's an exact one elsewhere — exact > prefix > substring.
 */
class OpenApp(
    private val context: Context,
) : Tool {
    override val name = "open_app"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val query =
            argString(args["name"]) ?: argString(args["package_name"])
                ?: return ToolResult.Failure("I need an app name or package")
        val pm = context.packageManager
        val pkg =
            resolvePackage(pm, query)
                ?: return ToolResult.Failure("I can't find an app called \"$query\"")
        val launch =
            pm.getLaunchIntentForPackage(pkg)
                ?: return ToolResult.Failure("That app has no launcher entry")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch) }
            .fold(
                onSuccess = { ToolResult.Success("Opened $query") },
                onFailure = { ToolResult.Failure("Couldn't open $query") },
            )
    }

    private data class Entry(
        val pkg: String,
        val label: String,
    )

    private fun resolvePackage(pm: PackageManager, query: String): String? {
        // 1. Treat as a package id first — saves the launcher enumeration on the common case.
        if (query.contains('.') && pm.getLaunchIntentForPackage(query) != null) return query

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries =
            pm
                .queryIntentActivities(launcherIntent, 0)
                .map { Entry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
        if (entries.isEmpty()) return null

        val q = query.trim().lowercase()
        return entries.firstOrNull { it.label.equals(query, ignoreCase = true) }?.pkg
            ?: entries.firstOrNull { it.label.lowercase().startsWith(q) }?.pkg
            ?: entries.firstOrNull { it.label.lowercase().contains(q) }?.pkg
    }
}
