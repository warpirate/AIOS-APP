package com.mitra.prefs

import android.content.Context

/**
 * How aggressively the ConfirmationGate fires. The mapping from mode to which side-effect
 * classes get gated lives in [MainActivity] (`buildRuntime`'s `requiresGate` lambda) so the
 * agent layer stays unaware of the enum — it sees only a `(SideEffect) -> Boolean`.
 *
 * - **STRICT** — gate every state-changing tool. Even a brightness nudge confirms first. The
 *   safer-than-default mode for users who want to verify each action before it fires.
 * - **BALANCED** (default) — gate only `SideEffect.Irreversible` tools (SMS / calls). Reversible
 *   actions run silently with an audit-log entry. Matches the R-005 mitigation target.
 *
 * A LOOSE mode is intentionally NOT exposed in V1 — it would require the per-action "don't ask
 * again 5 min" suppression from `docs/design/action-cards.md §5` to be meaningful, and that
 * lands separately.
 */
enum class ConfirmationMode { STRICT, BALANCED }

object UserPrefs {
    private const val PREFS = "mitra_user"
    private const val KEY_NAME = "user_name"
    private const val KEY_LAST_UTTERANCE = "last_utterance"
    private const val KEY_TTS_ENABLED = "tts_enabled"
    private const val KEY_CONFIRM_MODE = "confirmation_mode"
    private const val MAX_UTTERANCE_LEN = 240

    fun name(context: Context): String =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "")
            ?.trim()
            .orEmpty()

    fun setName(context: Context, name: String) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name.trim())
            .apply()
    }

    fun lastUtterance(context: Context): String =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_UTTERANCE, "")
            ?.trim()
            .orEmpty()

    fun setLastUtterance(context: Context, utterance: String) {
        val trimmed = utterance.trim().take(MAX_UTTERANCE_LEN)
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_UTTERANCE, trimmed)
            .apply()
    }

    fun clearLastUtterance(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_UTTERANCE)
            .apply()
    }

    fun ttsEnabled(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_TTS_ENABLED, false)

    fun setTtsEnabled(context: Context, enabled: Boolean) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TTS_ENABLED, enabled)
            .apply()
    }

    /** Returns the user's chosen [ConfirmationMode]. Defaults to [ConfirmationMode.BALANCED]
     *  on a fresh install — matches the R-005 mitigation target. */
    fun confirmationMode(context: Context): ConfirmationMode {
        val raw =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CONFIRM_MODE, null)
                ?: return ConfirmationMode.BALANCED
        return runCatching { ConfirmationMode.valueOf(raw) }.getOrDefault(ConfirmationMode.BALANCED)
    }

    fun setConfirmationMode(context: Context, mode: ConfirmationMode) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIRM_MODE, mode.name)
            .apply()
    }
}
