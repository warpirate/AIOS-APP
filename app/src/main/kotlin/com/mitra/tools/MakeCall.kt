package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.mitra.permissions.Permissions

/**
 * Dial a contact by name or directly by phone number.
 *
 * SideEffect.None — we use [Intent.ACTION_DIAL] (NOT `ACTION_CALL`). That opens the system dialer
 * pre-filled with the resolved number; the user taps the green button to actually place the call.
 * Mitra never auto-dials. This makes the tool safe enough to bypass [com.mitra.safety.ConfirmationGate]
 * (the dialer itself IS the confirmation), and means we do NOT need the dangerous `CALL_PHONE`
 * runtime permission. Trade-off: one extra tap; in return, zero risk of a surprise outgoing call
 * if the model misreads intent or the contact name resolves wrong.
 *
 * Arguments:
 *   - `name`: contact name as the user said it. Three-tier fuzzy match against
 *     [ContactsContract.Contacts.DISPLAY_NAME] (exact → starts-with → contains). First match wins.
 *     If a contact has multiple phones, prefer TYPE_MOBILE; else first phone.
 *   - `number`: phone number, raw as the user spoke / typed it. Used when the user gave digits
 *     directly. Goes straight into `tel:` URI; the dialer is permissive about formatting.
 *
 * If both name and number are provided, `number` wins (the user named a specific number).
 * If neither is provided, returns Failure asking who to call.
 *
 * Privacy: name search uses an explicit ContactsContract projection (no null projection).
 * [com.mitra.safety.AuditLog] records `make_call` + outcome only — never the name, never the
 * number, never the matched contact display name. The existing whitelist test enforces.
 */
class MakeCall(
    private val context: Context,
) : Tool {
    override val name = "make_call"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val numberArg = argString(args["number"])
        val nameArg = argString(args["name"])
        // Number wins if both present — explicit beats ambiguous.
        val (target, resolvedNumber) =
            when {
                numberArg != null -> nameArg.orEmpty().ifBlank { numberArg } to numberArg
                nameArg != null -> {
                    if (!Permissions.hasReadContacts(context)) {
                        bounceToAppPermissions()
                        return ToolResult.Failure(
                            "Grant Mitra Contacts permission on the page I just opened, then ask again",
                        )
                    }
                    val number = resolveContactNumber(nameArg)
                        ?: return ToolResult.Failure("No contact named '$nameArg'")
                    nameArg to number
                }
                else -> return ToolResult.Failure("Who should I call?")
            }

        return try {
            val intent =
                Intent(Intent.ACTION_DIAL)
                    .setData(Uri.parse("tel:${Uri.encode(resolvedNumber)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult.Success("Calling $target — confirm in the dialer")
        } catch (_: SecurityException) {
            ToolResult.Failure("Couldn't open the dialer — system refused")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't open the dialer")
        }
    }

    /**
     * Three-tier name match. Returns the preferred phone number for the first matching contact,
     * or null if nothing matched / contact had no phones.
     */
    private fun resolveContactNumber(query: String): String? {
        val contactId = findFirstContactId(query) ?: return null
        return preferredPhoneFor(contactId)
    }

    private fun findFirstContactId(query: String): Long? {
        val q = query.trim()
        if (q.isEmpty()) return null

        // Tier 1: exact
        firstId("LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) = LOWER(?)", arrayOf(q))?.let { return it }
        // Tier 2: starts-with
        firstId(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE LOWER(?) || '%'",
            arrayOf(q),
        )?.let { return it }
        // Tier 3: contains
        return firstId(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE '%' || LOWER(?) || '%'",
            arrayOf(q),
        )
    }

    private fun firstId(selection: String, selectionArgs: Array<String>): Long? {
        val projection = arrayOf(ContactsContract.Contacts._ID)
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "$selection AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            selectionArgs,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    /**
     * Returns the preferred phone for the contact: TYPE_MOBILE first, then the first phone of any
     * type. Returns null if the contact has no phones.
     */
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
