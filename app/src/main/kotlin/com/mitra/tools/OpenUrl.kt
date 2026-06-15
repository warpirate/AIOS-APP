package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a web page in the user's browser via an ACTION_VIEW intent (no permission needed).
 *
 * Defensive: the model occasionally calls open_url with arbitrary chat text (e.g. "lumos maximus",
 * "that's a spell"). This tool refuses anything that doesn't look like a URL — better to fail
 * fast than to open `https://lumos maximus` and surprise the user with a broken browser tab.
 *
 * A URL-shaped argument MUST satisfy ALL of:
 *   - no whitespace anywhere (URLs don't contain spaces)
 *   - contains a `.` separating a host from a TLD, OR has an explicit scheme like `http://`,
 *     `https://`, `mailto:`, `tel:`
 *   - the host part contains at least one letter (rules out pure-numeric "1.2.3" except via
 *     scheme-prefixed IPs)
 *
 * Anything else returns Failure with a clarifying message so the model can re-route.
 */
class OpenUrl(
    private val context: Context,
) : Tool {
    override val name = "open_url"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val raw = argString(args["url"]) ?: return ToolResult.Failure("I need a link to open")
        if (!looksLikeUrl(raw)) {
            return ToolResult.Failure("That doesn't look like a web address — answer in chat instead")
        }
        val url = if (raw.contains("://") || raw.startsWith("mailto:") || raw.startsWith("tel:")) raw else "https://$raw"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolResult.Success("Opening $url")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't open that link")
        }
    }

    private fun looksLikeUrl(s: String): Boolean {
        if (s.any { it.isWhitespace() }) return false
        val hasScheme = listOf("http://", "https://", "mailto:", "tel:").any { s.startsWith(it, ignoreCase = true) }
        if (hasScheme) return true
        val dot = s.indexOf('.')
        if (dot <= 0 || dot >= s.length - 1) return false
        // Host before TLD must contain at least one letter, TLD must be 2+ alpha chars.
        val host = s.substringBefore('/').substringBefore('?')
        val parts = host.split('.')
        if (parts.size < 2) return false
        val tld = parts.last()
        if (tld.length < 2 || !tld.all { it.isLetter() }) return false
        return host.any { it.isLetter() }
    }
}
