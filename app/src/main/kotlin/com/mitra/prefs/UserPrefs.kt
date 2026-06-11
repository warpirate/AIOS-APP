package com.mitra.prefs

import android.content.Context

object UserPrefs {
    private const val PREFS = "mitra_user"
    private const val KEY_NAME = "user_name"
    private const val KEY_LAST_UTTERANCE = "last_utterance"
    private const val MAX_UTTERANCE_LEN = 240

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

    fun lastUtterance(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_UTTERANCE, "")
            ?.trim()
            .orEmpty()

    fun setLastUtterance(context: Context, utterance: String) {
        val trimmed = utterance.trim().take(MAX_UTTERANCE_LEN)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_UTTERANCE, trimmed)
            .apply()
    }

    fun clearLastUtterance(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_UTTERANCE)
            .apply()
    }
}
