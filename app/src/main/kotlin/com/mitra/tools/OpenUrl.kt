package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens a web page in the user's browser via an ACTION_VIEW intent (no permission needed). */
class OpenUrl(private val context: Context) : Tool {
    override val name = "open_url"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        var url = argString(args["url"]) ?: return ToolResult.Failure("I need a link to open")
        if (!url.contains("://")) url = "https://$url"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult.Success("Opening $url")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't open that link")
        }
    }
}
