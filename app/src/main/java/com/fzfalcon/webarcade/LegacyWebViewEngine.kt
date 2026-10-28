package com.fzfalcon.webarcade

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

class LegacyWebViewEngine(private val activity: Activity) : PlayBrowserEngine {
    private val container = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK); isFocusable = true; isFocusableInTouchMode = true }
    private val webView = WebView(activity).apply {
        setBackgroundColor(Color.BLACK)
        isFocusable = true; isFocusableInTouchMode = true
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
        }
        webChromeClient = object : WebChromeClient() {
            private var custom: View? = null
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                custom = view
                this@LegacyWebViewEngine.container.removeView(this@LegacyWebViewEngine.webView)
                this@LegacyWebViewEngine.container.addView(view, FrameLayout.LayoutParams(-1,-1))
                fullScreen = true; fullScreenListener?.invoke(true)
            }
            override fun onHideCustomView() {
                custom?.let { this@LegacyWebViewEngine.container.removeView(it) }
                custom = null
                if (this@LegacyWebViewEngine.webView.parent == null) this@LegacyWebViewEngine.container.addView(this@LegacyWebViewEngine.webView, FrameLayout.LayoutParams(-1,-1))
                fullScreen = false; fullScreenListener?.invoke(false)
            }
        }
    }
    private var fullScreen = false
    private var fullScreenListener: ((Boolean) -> Unit)? = null
    private val defaultUserAgent: String = webView.settings.userAgentString
    override val view: View get() = container

    init { container.addView(webView, FrameLayout.LayoutParams(-1,-1)) }
    override fun loadUrl(url: String) { webView.loadUrl(url) }
    override fun setDesktopMode(enabled: Boolean) {
        webView.settings.userAgentString = if (enabled) DESKTOP_UA else defaultUserAgent
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
    }
    override fun canGoBack() = webView.canGoBack()
    override fun goBack() { if (webView.canGoBack()) webView.goBack() }
    override fun reload() = webView.reload()
    override fun onPause() { webView.onPause() }
    override fun onResume() { webView.onResume() }
    override fun exitFullscreen() { if (fullScreen) { fullScreen = false; container.removeAllViews(); container.addView(webView, FrameLayout.LayoutParams(-1,-1)); fullScreenListener?.invoke(false) } }
    override fun setFullscreenListener(listener: (Boolean) -> Unit) { fullScreenListener = listener }
    override fun close() { try { webView.stopLoading(); container.removeAllViews(); webView.destroy() } catch (_: Exception) { } }
}
