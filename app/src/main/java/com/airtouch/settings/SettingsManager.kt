package com.airtouch.settings

import android.content.Context

/** Configurações simples do usuário, guardadas localmente no aparelho. */
class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("airtouch_settings", Context.MODE_PRIVATE)

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 0.5f)
        set(value) {
            prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(0f, 1f)).apply()
        }

    var invertScroll: Boolean
        get() = prefs.getBoolean(KEY_INVERT_SCROLL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INVERT_SCROLL, value).apply()
        }

    private companion object {
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_INVERT_SCROLL = "invert_scroll"
    }
}
