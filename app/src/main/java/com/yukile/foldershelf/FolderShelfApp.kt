package com.yukile.foldershelf

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.yukile.foldershelf.util.CrashHandler
import com.yukile.foldershelf.util.PreferenceHelper

class FolderShelfApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // EN ONCE kurulmali
        CrashHandler.install(this)

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
