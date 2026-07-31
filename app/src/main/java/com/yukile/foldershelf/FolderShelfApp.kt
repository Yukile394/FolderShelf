package com.yukile.foldershelf

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.yukile.foldershelf.util.PreferenceHelper

/**
 * FolderShelfApp
 *
 * Uygulama genelinde bir kez calismasi gereken kurulum islemlerini
 * (Material You dinamik renk destegi, kaydedilmis tema tercihinin
 * uygulanmasi) burada yapariz.
 */
class FolderShelfApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Android 12+ (API 31+) cihazlarda duvar kagidina gore dinamik
        // renklendirme uygular; daha eski surumlerde guvenli sekilde
        // hicbir sey yapmaz.
        DynamicColors.applyToActivitiesIfAvailable(this)

        applySavedThemeMode()
    }

    private fun applySavedThemeMode() {
        val prefs = PreferenceHelper(this)
        val mode = when (prefs.themeMode) {
            PreferenceHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            PreferenceHelper.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
