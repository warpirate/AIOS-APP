package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
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
 * failure — same pattern as QueryContacts. The fallback to `ACTION_DIAL` (which needs no
 * permission) is intentionally not kept: the user explicitly chose Mitra-places-the-call over
 * Mitra-opens-the-dialer.
 *
 * Arguments:
 *   - `name`: contact name as the user said it. Three-tier fuzzy match against
 *     [ContactsContract.Contacts.DISPLAY_NAME] (exact → starts-with → contains). First match
 *     wins; for multiple phones on a contact, TYPE_MOBILE is preferred, else first phone.
 *   - `number`: phone number, raw as the user spoke / typed it. Wins over `name` when both are
 *     present (explicit beats ambiguous).
 *
 * Privacy: name resolution uses an explicit ContactsContract projection (no null projection).
 * [com.mitra.safety.AuditLog] records `make_call` + outcome only — never the name, never the
 * number, never the matched contact display name.
 */
class MakeCall(
    private val context: Context,
) : Tool {
    override val name = "make_call"
    override val sideEffect = SideEffect.Irreversible

    override fun execute(args: Map<String, Any?>): ToolResult {
        val resolved = resolveTarget(args) ?: return ToolResult.Failure("Who should I call?")

        if (!Permissions.hasCallPhone(context)) {
            bounceToAppPermissions()
            return ToolResult.Failure(
                "Grant Mitra the Phone permission on the page I just opened, then ask again",
            )
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
     * Resolve raw tool args into a target with a definite phone number. Pure function — does
     * NOT side-effect (does not bounce, does not start activities). Used both by [execute] and
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
                if (!Permissions.hasReadContacts(context)) return null
                val (display, number) = resolveContactPhone(nameArg) ?: return null
                ResolvedTarget(display, number)
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

    /**
     * Returns (displayName, preferredNumber) for the first matched contact, or null.
     *
     * Match strategy is **deliberately stricter than QueryContacts**: we do exact + starts-with
     * only. The contains-tier is dropped because auto-dialling is irreversible — "call cops"
     * should NOT auto-resolve to a contact whose name happens to contain "cop" (e.g. "Cooperative
     * Bank"). If the user wants a fuzzier search, they can ask `query_contacts` first.
     */
    private fun resolveContactPhone(query: String): Pair<String, String>? {
        val q = query.trim()
        if (q.isEmpty()) return null

        // Tier 1: exact
        firstContact("LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) = LOWER(?)", arrayOf(q))?.let { return it }
        // Tier 2: starts-with
        return firstContact(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE LOWER(?) || '%'",
            arrayOf(q),
        )
    }

    private fun firstContact(selection: String, selectionArgs: Array<String>): Pair<String, String>? {
        val projection =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            )
        var idAndName: Pair<Long, String>? = null
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "$selection AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                if (name != null) idAndName = id to name
            }
        }
        val (contactId, displayName) = idAndName ?: return null
        val phone = preferredPhoneFor(contactId) ?: return null
        return displayName to phone
    }

    /** TYPE_MOBILE preferred; else the first phone of any type. */
    private fun preferredPhoneFor(contactId: Long): String? {
        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            )
        var fallback: String? = null
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            while (c.moveToNext()) {
                val number = c.getString(numIdx)?.trim().orEmpty()
                if (number.isEmpty()) continue
                if (c.getInt(typeIdx) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                    return number
                }
                if (fallback == null) fallback = number
            }
        }
        return fallback
    }

    private fun bounceToAppPermissions() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
