package com.mitra.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mitra.contacts.ContactResolver
import com.mitra.permissions.Permissions

/**
 * Place a phone call to a contact by name or directly to a phone number.
 *
 * **SideEffect.Irreversible.** Once dialled, the call is out. The action card in chat MUST gate
 * via [com.mitra.safety.ConfirmationGate] and surface the resolved name + number to the user
 * before the call fires; see [previewFor] for the UI helper that does the resolution at card
 * creation time so the modal can show "Call Blanta — +91 76718 90230?" rather than just "Call".
 *
 * Uses [Intent.ACTION_CALL] which places the call immediately via the system telephony stack
 * (no dialer UI flash, no extra tap). Requires the dangerous `CALL_PHONE` runtime permission. On
 * a missing grant the tool bounces to the app-permissions page and returns a grant-then-retry
 * failure — same pattern as QueryContacts.
 *
 * Arguments:
 *   - `name`: contact name as the user said it. Resolved via [ContactResolver] (exact +
 *     starts-with, no contains-tier — see ContactResolver for the why).
 *   - `number`: phone number, raw as the user spoke / typed it. Wins over `name` when both are
 *     present (explicit beats ambiguous).
 *
 * Privacy: [com.mitra.safety.AuditLog] records `make_call` + outcome only — never the name,
 * never the number, never the matched contact display name.
 */
class MakeCall(
    private val context: Context,
) : Tool {
    override val name = "make_call"
    override val sideEffect = SideEffect.Irreversible

    private val resolver = ContactResolver(context)

    override fun execute(args: Map<String, Any?>): ToolResult {
        val resolved = resolveTarget(args) ?: return ToolResult.Failure("Who should I call?")

        if (!Permissions.hasCallPhone(context)) {
            // ChatScreen catches this sentinel and launches the in-app RequestPermission dialog
            // instead of bouncing to system Settings. The user grants without leaving the chat.
            return ToolResult.Failure("__NEED_PERM__:${Manifest.permission.CALL_PHONE}")
        }

        return try {
            val intent =
                Intent(Intent.ACTION_CALL)
                    .setData(Uri.parse("tel:${Uri.encode(resolved.number)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("Calling ${resolved.displayLabel}")
        } catch (_: SecurityException) {
            ToolResult.Failure("Phone permission missing — grant it and ask again")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't place the call")
        }
    }

    /**
     * Resolve raw tool args into a target with a definite phone number. Pure — does NOT
     * side-effect (does not bounce, does not start activities). Used both by [execute] and
     * by the UI preview helper [previewFor]. Returns null if neither a number nor a resolvable
     * name was provided.
     */
    private fun resolveTarget(args: Map<String, Any?>): ResolvedTarget? {
        val numberArg = argString(args["number"])
        val nameArg = argString(args["name"])
        return when {
            // Number wins: explicit digits beat name-resolution. Display label prefers the name
            // when both were given so the confirm card reads "Call Mom — +91 98XXX?" not
            // "Call +91 98XXX — +91 98XXX?".
            numberArg != null -> {
                val label = nameArg ?: numberArg
                ResolvedTarget(label, numberArg)
            }
            nameArg != null -> {
                val r = resolver.resolve(nameArg) ?: return null
                ResolvedTarget(r.displayName, r.number)
            }
            else -> null
        }
    }

    /** Public preview helper for the chat action card. Safe to call from UI thread. */
    fun previewFor(args: Map<String, Any?>): String? {
        val r = resolveTarget(args) ?: return null
        return if (r.displayLabel == r.number) r.number else "${r.displayLabel} — ${r.number}"
    }

    private data class ResolvedTarget(val displayLabel: String, val number: String)
}
