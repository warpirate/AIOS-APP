package com.mitra.prefs

import android.content.Context

object UserPrefs {
    private const val PREFS = "mitra_user"
    private const val KEY_NAME = "user_name"

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "")
            ?.trim()
            .orEmpty()

    fun setName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name.trim())
            .apply()
    }
}
