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
    private val container: FrameLayout = FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private var fullScreen: Boolean = false
    private var fullScreenListener: ((Boolean) -> Unit)? = null

    // Build the browser in a separate function so callbacks don't reference a
    // property while Kotlin is still inferring that property's initializer.
    private val webView: WebView = createWebView()
    private val defaultUserAgent: String = webView.settings.userAgentString

    override val view: View
        get() = container

    init {
        container.addView(webView, matchParentLayoutParams())
    }

    private fun createWebView(): WebView {
        val browser: WebView = WebView(activity)
        browser.setBackgroundColor(Color.BLACK)
        browser.isFocusable = true
        browser.isFocusableInTouchMode = true

        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
        }

        browser.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                customView?.let { container.removeView(it) }
                customView = view
                container.removeView(browser)
                container.addView(view, matchParentLayoutParams())
                fullScreen = true
                fullScreenListener?.invoke(true)
            }

            override fun onHideCustomView() {
                customView?.let { container.removeView(it) }
                customView = null
                if (container.indexOfChild(browser) < 0) {
                    container.addView(browser, matchParentLayoutParams())
                }
                if (fullScreen) {
                    fullScreen = false
                    fullScreenListener?.invoke(false)
                }
            }
        }

        return browser
    }

    private fun matchParentLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    override fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun setDesktopMode(enabled: Boolean) {
        webView.settings.userAgentString = if (enabled) DESKTOP_UA else defaultUserAgent
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
    }

    override fun canGoBack(): Boolean = webView.canGoBack()

    override fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    override fun reload() {
        webView.reload()
    }

    override fun onPause() {
        webView.onPause()
    }

    override fun onResume() {
        webView.onResume()
    }

    override fun exitFullscreen() {
        if (!fullScreen) return
        container.removeAllViews()
        container.addView(webView, matchParentLayoutParams())
        fullScreen = false
        fullScreenListener?.invoke(false)
    }

    override fun setFullscreenListener(listener: (Boolean) -> Unit) {
        fullScreenListener = listener
    }

    override fun close() {
        try {
            webView.stopLoading()
            container.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {
            // Best-effort cleanup during activity shutdown.
        }
    }
}
