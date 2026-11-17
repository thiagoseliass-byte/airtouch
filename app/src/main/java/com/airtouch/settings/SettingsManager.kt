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

    /** Troca de perfil automática (por aba no app; pelo app em primeiro plano no modo global). */
    var autoProfile: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PROFILE, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_PROFILE, value).apply() }

    /** Economia de bateria: limita a análise a ~15 quadros/s. */
    var powerSaver: Boolean
        get() = prefs.getBoolean(KEY_POWER_SAVER, false)
        set(value) { prefs.edit().putBoolean(KEY_POWER_SAVER, value).apply() }

    /** Filtro de qualidade: ignora mãos mal detectadas ou cortadas pela borda da câmera. */
    var qualityFilter: Boolean
        get() = prefs.getBoolean(KEY_QUALITY, true)
        set(value) { prefs.edit().putBoolean(KEY_QUALITY, value).apply() }

    /** Fator de calibração da pinça (1 = padrão). */
    var pinchScale: Float
        get() = prefs.getFloat(KEY_PINCH_SCALE, 1f)
        set(value) { prefs.edit().putFloat(KEY_PINCH_SCALE, value.coerceIn(0.7f, 1.6f)).apply() }

    private companion object {
        const val KEY_AUTO_PROFILE = "auto_profile"
        const val KEY_POWER_SAVER = "power_saver"
        const val KEY_QUALITY = "quality_filter"
        const val KEY_PINCH_SCALE = "pinch_scale
        const val KEY_SENSITIVITY = "sensitivity"
        const val KEY_INVERT_SCROLL = "invert_scroll"
    }
}
