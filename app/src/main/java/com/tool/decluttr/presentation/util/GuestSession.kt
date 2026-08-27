package com.tool.decluttr.presentation.util

import android.content.Context

object GuestSession {
    private const val PREFS_NAME = "decluttr_prefs"
    private const val KEY_GUEST_MODE = "guest_mode"

    fun isGuest(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUEST_MODE, false)

    fun enterAsGuest(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUEST_MODE, true)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUEST_MODE, false)
            .apply()
    }
}
