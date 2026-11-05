package com.fzfalcon.webarcade

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView

class PlayTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            // chrome://inspect over adb shows the real console / network errors of a game.
            WebView.setWebContentsDebuggingEnabled(true)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = getProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
        } catch (e: Exception) {
            Log.w("PlayTVApp", "Data directory suffix setup: ${e.message}")
        }
    }
}
