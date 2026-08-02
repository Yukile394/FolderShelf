package com.yukile.foldershelf.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * PermissionUtils
 *
 * Uygulamanin ihtiyac duydugu iki farkli izni kontrol eder:
 *  1) "Diger uygulamalarin uzerinde goster" (SYSTEM_ALERT_WINDOW) - ozel
 *     bir sistem ekrani uzerinden verilir (Settings.canDrawOverlays).
 *  2) Bildirim gosterme izni (POST_NOTIFICATIONS) - yalnizca Android 13
 *     (API 33) ve sonrasinda calisma zamaninda istenmesi gerekir.
 */
object PermissionUtils {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // API 33 oncesinde bildirim izni derleme zamaninda (manifest ile)
            // otomatik taninir, calisma zamaninda istemeye gerek yoktur.
            true
        }
    }

    fun allRequiredPermissionsGranted(context: Context): Boolean {
        return canDrawOverlays(context) && hasNotificationPermission(context)
    }

    /**
     * Kullanıcı bildirim iznini "Bir daha sorma" ile kalıcı olarak
     * reddettiyse true döner. Bu durumda sistem izin diyaloğu tekrar
     * gösterilmez (sessizce false döner), bu yüzden kullanıcıyı doğrudan
     * uygulama ayarlarına yönlendirmemiz gerekir.
     */
    fun isNotificationPermissionPermanentlyDenied(
        activity: android.app.Activity,
        alreadyRequestedBefore: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (hasNotificationPermission(activity)) return false
        val shouldShowRationale = androidx.core.app.ActivityCompat
            .shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        return alreadyRequestedBefore && !shouldShowRationale
    }
}
