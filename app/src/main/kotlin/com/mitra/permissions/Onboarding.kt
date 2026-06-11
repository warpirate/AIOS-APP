package com.mitra.permissions

import android.content.Context

/**
 * One-shot flag: did the user finish (or skip) the first-run permission wizard?
 * Once true, the app never shows the wizard again — even if perms get revoked later, the user
 * manages them via the Settings screen.
 *
 * Stored in SharedPreferences (cheap, no DataStore dep) under a single boolean key.
 */
object Onboarding {
    private const val PREFS = "mitra_onboarding"
    private const val KEY_COMPLETE = "onboarding_complete"

    fun isComplete(context: Context): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETE, false)

    fun markComplete(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }
}
