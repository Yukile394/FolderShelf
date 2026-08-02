package com.yukile.foldershelf.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val appContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Yakalanmamis hata: ", throwable)
            saveCrashLog(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Hata gunlugu kaydedilemedi", e)
        }

        try {
            restartApp()
            Process.killProcess(Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }
        }
    }

    private fun saveCrashLog(throwable: Throwable) {
        val dir = File(appContext.filesDir, "crash_logs").apply { mkdirs() }
        val fileName = "crash_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("Zaman: ${Date()}")
            pw.println("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            pw.println("Cihaz: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            pw.println("Thread: ${Thread.currentThread().name}")
            pw.println()
            throwable.printStackTrace(pw)
        }
        File(dir, fileName).writeText(sw.toString())
        pruneOldLogs(dir)
    }

    private fun pruneOldLogs(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_LOGS).forEach { it.delete() }
    }

    private fun restartApp() {
        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
            ?: throw IllegalStateException("Baslatma intent'i bulunamadi")
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
        launchIntent.putExtra(EXTRA_CRASHED, true)
        appContext.startActivity(launchIntent)
    }

    companion object {
        private const val TAG = "FolderShelfCrash"
        private const val MAX_LOGS = 15
        const val EXTRA_CRASHED = "extra_app_crashed"

        fun install(application: Application) {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(application.applicationContext, current)
            )
        }

        fun latestCrashLog(context: Context): String? {
            val dir = File(context.filesDir, "crash_logs")
            val latest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
            return try {
                latest.readText()
            } catch (e: Exception) {
                null
            }
        }
    }
}
