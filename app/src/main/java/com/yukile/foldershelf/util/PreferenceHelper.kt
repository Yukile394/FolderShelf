package com.yukile.foldershelf.util

import android.content.Context
import android.content.SharedPreferences

/**
 * PreferenceHelper
 *
 * Floating balonun ekrandaki konumunu, boyutunu, gizli/gorunur durumunu
 * ve tema tercihini kalici olarak saklar. Uygulama tamamen kapatilsa
 * veya telefon yeniden baslatilsa bile bu degerler korunur; servis
 * yeniden baslatildiginda balon kaldigi yerden (ayni konum/boyutla)
 * devam eder.
 */
class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var bubbleX: Int
        get() = prefs.getInt(KEY_BUBBLE_X, 0)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = prefs.getInt(KEY_BUBBLE_Y, 650)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_Y, value).apply()

    var bubbleSizeDp: Int
        get() = prefs.getInt(KEY_BUBBLE_SIZE, Constants.DEFAULT_BUBBLE_SIZE_DP)
        set(value) = prefs.edit()
            .putInt(KEY_BUBBLE_SIZE, value.coerceIn(Constants.MIN_BUBBLE_SIZE_DP, Constants.MAX_BUBBLE_SIZE_DP))
            .apply()

    var isBubbleHidden: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE_HIDDEN, value).apply()

    var isBubbleOnLeftEdge: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE_LEFT_EDGE, true)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE_LEFT_EDGE, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    companion object {
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_BUBBLE_SIZE = "bubble_size"
        private const val KEY_BUBBLE_HIDDEN = "bubble_hidden"
        private const val KEY_BUBBLE_LEFT_EDGE = "bubble_left_edge"
        private const val KEY_THEME_MODE = "theme_mode"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
