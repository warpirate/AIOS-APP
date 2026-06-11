package com.mitra.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.mitra.permissions.Permissions

/**
 * Looks up phone numbers by contact display-name.
 *
 * SideEffect.None — pure read. Three-tier fuzzy match against
 * [ContactsContract.Contacts.DISPLAY_NAME] (exact → starts-with → contains), capped at 5 results
 * total. For each matched contact, all phone numbers are read from
 * [ContactsContract.CommonDataKinds.Phone] with type labels.
 *
 * Privacy: the result string contains contact names + numbers, returned only to the chat UI
 * (volatile session memory). [com.mitra.safety.AuditLog] receives tool-name + outcome only —
 * never the search term, never any matched name or number. ContentResolver projections are
 * explicit, never null.
 */
class QueryContacts(
    private val context: Context,
) : Tool {
    override val name = "query_contacts"
    override val sideEffect = SideEffect.None

    override fun execute(args: Map<String, Any?>): ToolResult {
        val name = argString(args["name"]) ?: return ToolResult.Failure("I need a name to search for")

        if (!Permissions.hasReadContacts(context)) {
            bounceToAppPermissions()
            return ToolResult.Failure(
                "Grant Mitra Contacts permission on the page I just opened, then ask again",
            )
        }

        return try {
            val matches = findContacts(name)
            if (matches.isEmpty()) {
                ToolResult.Failure("No contact named '$name'")
            } else {
                val withPhones = matches.map { it to phonesFor(it.id) }.filter { it.second.isNotEmpty() }
                if (withPhones.isEmpty()) {
                    ToolResult.Failure("Found '$name' but no phone numbers stored")
                } else {
                    ToolResult.Success(formatResult(name, matches.size, withPhones))
                }
            }
        } catch (_: SecurityException) {
            ToolResult.Failure("Contacts permission missing — grant it and ask again")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't read contacts")
        }
    }

    private data class ContactRow(val id: Long, val displayName: String)

    private data class PhoneRow(val number: String, val typeLabel: String)

    private fun findContacts(query: String): List<ContactRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        // Tier 1: exact (case-insensitive).
        var rows = queryContacts(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) = LOWER(?)",
            arrayOf(q),
        )
        if (rows.isNotEmpty()) return rows.take(MAX_RESULTS)

        // Tier 2: starts-with.
        rows = queryContacts(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE LOWER(?) || '%'",
            arrayOf(q),
        )
        if (rows.isNotEmpty()) return rows.take(MAX_RESULTS)

        // Tier 3: contains.
        rows = queryContacts(
            "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE '%' || LOWER(?) || '%'",
            arrayOf(q),
        )
        return rows.take(MAX_RESULTS)
    }

    private fun queryContacts(selection: String, selectionArgs: Array<String>): List<ContactRow> {
        val projection =
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
            )
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        val rows = mutableListOf<ContactRow>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "$selection AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1",
            selectionArgs,
            sortOrder,
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
            while (c.moveToNext() && rows.size < MAX_RESULTS) {
                val displayName = c.getString(nameIdx) ?: continue
                rows += ContactRow(c.getLong(idIdx), displayName)
            }
        }
        return rows
    }

    private fun phonesFor(contactId: Long): List<PhoneRow> {
        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
            )
        val phones = mutableListOf<PhoneRow>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LABEL)
            while (c.moveToNext()) {
                val number = c.getString(numIdx)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val type = c.getInt(typeIdx)
                val customLabel = c.getString(labelIdx)?.trim().orEmpty()
                phones += PhoneRow(number, labelFor(type, customLabel))
            }
        }
        return phones
    }

    private fun labelFor(type: Int, customLabel: String): String =
        when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel.lowercase().ifBlank { "other" }
            else -> "other"
        }

    private fun formatResult(
        query: String,
        totalMatched: Int,
        contacts: List<Pair<ContactRow, List<PhoneRow>>>,
    ): String {
        if (contacts.size == 1) {
            val (contact, phones) = contacts[0]
            return "Found ${contact.displayName} — " + phones.joinToString(", ") { "${it.typeLabel} ${it.number}" }
        }
        val header =
            if (totalMatched > MAX_RESULTS) {
                "Found $MAX_RESULTS of $totalMatched contacts for '$query':"
            } else {
                "Found ${contacts.size} contacts for '$query':"
            }
        val body =
            contacts.joinToString("\n") { (c, phones) ->
                "• ${c.displayName} — " + phones.joinToString(", ") { "${it.typeLabel} ${it.number}" }
            }
        return "$header\n$body"
    }

    private fun bounceToAppPermissions() {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        private const val MAX_RESULTS = 5
    }
}
