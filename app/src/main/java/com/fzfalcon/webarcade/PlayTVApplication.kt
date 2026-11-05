package com.fzfalcon.webarcade

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.webkit.WebView

class PlayTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Debug builds: inspect the real console / network errors of a game from a PC with
        //   chrome://inspect  (adb connect <fire-tv-ip>)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = getProcessName()
                if (packageName != processName) WebView.setDataDirectorySuffix(processName)
            }
        } catch (e: Exception) {
            Log.w("PlayTVApp", "WebView data directory suffix: ${e.message}")
        }
    }
}
