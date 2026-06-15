package com.mitra.tools

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import com.mitra.contacts.ContactResolver
import com.mitra.permissions.Permissions

/**
 * Send a text message to a contact by name or directly to a phone number.
 *
 * **SideEffect.Irreversible.** Once sent, the message is out and the carrier bills for it. The
 * action card in chat MUST gate via [com.mitra.safety.ConfirmationGate] and surface the resolved
 * name + number AND the body text before the SMS fires. See [previewFor] for the UI helper that
 * does the resolution at card creation time so the modal reads
 * "Text Blanta — +91 76718 90230 — 'on my way'?" rather than just "Send SMS".
 *
 * Uses [SmsManager.sendTextMessage] for a direct, UI-less send via the system telephony stack
 * (mirrors ARCHITECTURE.md §SMS guidance). Requires the dangerous `SEND_SMS` runtime permission.
 * Missing grant bounces to the app-permissions page and returns a grant-then-retry failure —
 * same pattern as [MakeCall] / [QueryContacts]. We deliberately do NOT fall back to
 * `ACTION_SENDTO` (opens the default SMS app pre-filled); the user picked Mitra-sends-the-SMS
 * over Mitra-opens-the-messages-app.
 *
 * Long messages (> 160 GSM-7 chars) are split via [SmsManager.divideMessage] and sent as a
 * multipart message — the carrier reassembles them on the recipient end.
 *
 * Arguments:
 *   - `name`: contact name, resolved via [ContactResolver] (exact + starts-with).
 *   - `number`: raw digits. Wins over `name` when both are present.
 *   - `body`: the message text. Required — empty → Failure.
 *
 * Privacy: [com.mitra.safety.AuditLog] records `send_sms` + outcome only. **Never the recipient,
 * never the body text.** The whitelist test in [com.mitra.safety.AuditLogTest] enforces this.
 */
class SendSms(
    private val context: Context,
) : Tool {
    override val name = "send_sms"
    override val sideEffect = SideEffect.Irreversible

    private val resolver = ContactResolver(context)

    override fun execute(args: Map<String, Any?>): ToolResult {
        val body = argString(args["body"]) ?: return ToolResult.Failure("What should I say?")
        val resolved = resolveTarget(args) ?: return ToolResult.Failure("Who should I text?")

        if (!Permissions.hasSendSms(context)) {
            // ChatScreen catches this sentinel and launches the in-app RequestPermission dialog.
            return ToolResult.Failure("__NEED_PERM__:${Manifest.permission.SEND_SMS}")
        }

        return try {
            val sms = smsManager()
            val parts = sms.divideMessage(body)
            if (parts.size > 1) {
                sms.sendMultipartTextMessage(resolved.number, null, parts, null, null)
            } else {
                sms.sendTextMessage(resolved.number, null, body, null, null)
            }
            ToolResult.Success("Sent to ${resolved.displayLabel}")
        } catch (_: SecurityException) {
            ToolResult.Failure("SMS permission missing — grant it and ask again")
        } catch (_: Exception) {
            ToolResult.Failure("Couldn't send the message")
        }
    }

    /** Public preview helper for the chat action card. Safe to call from UI thread. */
    fun previewFor(args: Map<String, Any?>): String? {
        val body = argString(args["body"]) ?: return null
        val r = resolveTarget(args) ?: return null
        val who = if (r.displayLabel == r.number) r.number else "${r.displayLabel} — ${r.number}"
        return "$who · $body"
    }

    private fun resolveTarget(args: Map<String, Any?>): ResolvedTarget? {
        val numberArg = argString(args["number"])
        val nameArg = argString(args["name"])
        return when {
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

    private data class ResolvedTarget(val displayLabel: String, val number: String)

    /**
     * Get the default SmsManager. Pre-31 used the static [SmsManager.getDefault]; that method was
     * deprecated in API 31 in favour of the system-service lookup which respects the user's chosen
     * default-SMS subscription on multi-SIM devices.
     */
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

}
