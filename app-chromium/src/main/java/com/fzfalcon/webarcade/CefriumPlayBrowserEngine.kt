package com.fzfalcon.webarcade

import android.app.Activity
import android.view.View
import com.cefrium.CefriumBrowser

private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

class CefriumPlayBrowserEngine(activity: Activity) : PlayBrowserEngine {
    private val browser: CefriumBrowser = CefriumBrowser.createWithSurface(activity)
    override val view: View = browser.surfaceContainer.apply {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(android.graphics.Color.BLACK)
    }
    private var fullscreenListener: ((Boolean) -> Unit)? = null

    init {
        browser.setJavaScriptEnabled(true)
        browser.setMediaPlaybackRequiresUserGesture(false)
        browser.setPinchToZoomEnabled(true)
        browser.setOnFullscreenChangedListener { fullscreen -> fullscreenListener?.invoke(fullscreen) }
        browser.setOnRenderProcessTerminatedListener { _, status ->
            android.util.Log.e("PLAYTV", "Chromium renderer terminated: $status")
        }
    }

    override fun loadUrl(url: String) { browser.setFocus(true); browser.loadUrl(url) }
    override fun setDesktopMode(enabled: Boolean) {
        browser.setUserAgentOverride(if (enabled) DESKTOP_UA else "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36")
    }
    override fun canGoBack(): Boolean = browser.canGoBack()
    override fun goBack() { browser.goBack() }
    override fun reload() { browser.reload() }
    override fun onPause() { browser.onPause() }
    override fun onResume() { browser.onResume() }
    override fun exitFullscreen() { browser.exitFullscreen() }
    override fun setFullscreenListener(listener: (Boolean) -> Unit) { fullscreenListener = listener }
    override fun close() { browser.close() }
}
