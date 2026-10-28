package com.fzfalcon.webarcade

import android.app.Activity
import android.view.View

interface PlayBrowserEngine {
    val view: View
    fun loadUrl(url: String)
    fun setDesktopMode(enabled: Boolean)
    fun canGoBack(): Boolean
    fun goBack()
    fun reload()
    fun onPause()
    fun onResume()
    fun exitFullscreen()
    fun setFullscreenListener(listener: (Boolean) -> Unit)
    fun close()
}

object BrowserEngineFactory {
    const val engineLabel: String = "Android System WebView (legacy compatibility)"
    fun create(activity: Activity): PlayBrowserEngine = LegacyWebViewEngine(activity)
}
