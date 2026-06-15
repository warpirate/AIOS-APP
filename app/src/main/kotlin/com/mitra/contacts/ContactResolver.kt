package com.mitra.contacts

import android.content.Context
import android.provider.ContactsContract
import com.mitra.permissions.Permissions

/**
 * Resolves a contact-name query to a `(displayName, preferredNumber)`. Shared between
 * [com.mitra.tools.MakeCall] and [com.mitra.tools.SendSms].
 *
 * Match strategy is **deliberately stricter than `query_contacts`**: exact + starts-with only.
 * The contains-tier is dropped because autodialling / autosending is irreversible — "call cop"
 * must NOT auto-resolve to "Cooperative Bank". A user who wants the fuzzier behaviour can run
 * `query_contacts` first and then re-issue the call/text with the explicit number.
 *
 * TYPE_MOBILE is preferred when a contact has multiple phones; first non-empty phone of any type
 * is the fallback. Returns null if READ_CONTACTS is not granted, the query is empty, no contact
 * matches, or the matched contact has no usable phone number.
 *
 * Privacy: queries use an explicit projection (no null projection) and do not log resolved
 * names or numbers anywhere. Callers are responsible for AuditLog hygiene.
 */
class ContactResolver(
    private val context: Context,
) {
    data class Resolved(val displayName: String, val number: String)

    fun resolve(query: String): Resolved? {
        if (!Permissions.hasReadContacts(context)) return null
        val q = query.trim()
        if (q.isEmpty()) return null
        // Tier 1: exact (case-insensitive). Tier 2: starts-with (case-insensitive).
        return firstContact("LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) = LOWER(?)", arrayOf(q))
            ?: firstContact(
                "LOWER(${ContactsContract.Contacts.DISPLAY_NAME}) LIKE LOWER(?) || '%'",
                arrayOf(q),
            )
    }

    private fun firstContact(selection: String, selectionArgs: Array<String>): Resolved? {
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
        return Resolved(displayName, phone)
    }

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
}
