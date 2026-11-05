package com.fzfalcon.webarcade

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.http.SslError
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * Browser engine backed by the system WebView (Amazon WebView on Fire TV).
 *
 * Things this class deliberately does:
 *  - serves the launcher from https://appassets.androidplatform.net (fetch() of file:// is blocked by Chromium)
 *  - gives games a normal Chrome user agent instead of the "; wv ... Mobile" WebView one
 *  - enables third-party cookies so games embedded in iframes (portals) can store progress
 *  - exposes the native bridge ONLY while the bundled launcher is shown, never to remote game pages
 *  - NEVER leaves a game because it logged a WebGPU error: many games fall back to WebGL
 *  - rebuilds the WebView if the renderer process dies, instead of using a dead view
 */
class WebViewEngine(
    private val activity: Activity,
    private val prefs: Prefs,
    private val listener: EngineListener
) : PlayBrowserEngine {

    private val bgColor = Color.parseColor("#0A0C11")

    private val container = FrameLayout(activity).apply { setBackgroundColor(bgColor) }

    private val assetLoader = WebViewAssetLoader.Builder()
        .setDomain(ASSET_HOST)
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(activity))
        .build()

    private var webView: WebView = createWebView()
    private val defaultUserAgent: String = webView.settings.userAgentString
    private val bridge = Bridge()

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var gpuHintSent = false

    override var isInGame: Boolean = false
        private set

    override val view: View get() = container

    init {
        container.addView(webView, matchParent())
    }

    // ------------------------------------------------------------------ setup

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val wv = WebView(activity)
        wv.setBackgroundColor(bgColor)
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.overScrollMode = View.OVER_SCROLL_NEVER

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Layout width = real view width, like a desktop browser. With wide-viewport on, pages
            // without a viewport tag are laid out at 980px and scaled up (blurry, tiny UI).
            useWideViewPort = false
            loadWithOverviewMode = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(false)
        }

        // Game portals embed games in cross-site iframes that need third-party cookies/storage.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                request?.let { assetLoader.shouldInterceptRequest(it.url) }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Ignore intent://, market://, mailto: ... which would otherwise show an error page.
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                return scheme !in ALLOWED_SCHEMES
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url == null) return
                gpuHintSent = false
                isInGame = !isLauncherUrl(url)
                listener.onEvent(EngineEvent.PageStarted(url, isInGame))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return
                val game = !isLauncherUrl(url)
                // Keep history empty on Home so BACK on Home leaves the app instead of reopening a game.
                if (!game) view?.clearHistory()
                view?.requestFocus()
                listener.onEvent(EngineEvent.PageFinished(url, game))
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val req = request ?: return
                if (!req.isForMainFrame) return
                val description = error?.description?.toString() ?: "Unknown error"
                Log.w(TAG, "Main frame error: $description at ${req.url}")
                listener.onEvent(EngineEvent.LoadError(description, req.url.toString()))
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                listener.onEvent(
                    EngineEvent.LoadError("Secure connection failed. Check the date and time on this device.", error?.url ?: "")
                )
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.e(TAG, "Renderer gone, crashed=${detail?.didCrash()}")
                // A dead WebView must be destroyed and replaced; post so we are outside its callback.
                container.post {
                    recreateWebView()
                    listener.onEvent(EngineEvent.RendererRecovered)
                }
                return true
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                listener.onEvent(EngineEvent.Progress(newProgress))
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) { callback?.onCustomViewHidden(); return }
                customView?.let { container.removeView(it) }
                customView = view
                customViewCallback = callback
                container.addView(view, matchParent())
            }

            override fun onHideCustomView() {
                customView?.let { container.removeView(it) }
                customView = null
                customViewCallback = null
            }

            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                val msg = message?.message().orEmpty()
                Log.d("WebConsole", msg)
                if (isInGame && !gpuHintSent &&
                    msg.contains("WebGPU", ignoreCase = true) &&
                    (msg.contains("fail", ignoreCase = true) ||
                        msg.contains("could not find", ignoreCase = true) ||
                        msg.contains("not supported", ignoreCase = true))
                ) {
                    // Informational only. Do NOT leave the game: it may fall back to WebGL by itself.
                    gpuHintSent = true
                    listener.onEvent(EngineEvent.GpuHint(msg))
                }
                return true
            }
        }
        return wv
    }

    private fun recreateWebView() {
        customView?.let { container.removeView(it) }
        customView = null
        customViewCallback = null
        val old = webView
        container.removeView(old)
        try { old.destroy() } catch (e: Exception) { Log.w(TAG, "destroy: ${e.message}") }
        webView = createWebView()
        container.addView(webView, matchParent())
        showLauncher()
    }

    private fun matchParent() =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    /**
     * The stock WebView UA contains "; wv", "Version/4.0" and "Mobile". Many game sites treat that as an
     * embedded or phone browser and refuse to start. Present a plain Android Chrome UA to games.
     */
    private fun gameUserAgent(): String = defaultUserAgent
        .replace("; wv", "")
        .replace(Regex("\\sVersion/[0-9.]+"), "")
        .replace(" Mobile Safari", " Safari")

    // ------------------------------------------------------------------ PlayBrowserEngine

    override fun showLauncher() {
        isInGame = false
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.addJavascriptInterface(bridge, BRIDGE_NAME)
        webView.settings.userAgentString = defaultUserAgent
        webView.loadUrl(LAUNCHER_URL)
    }

    override fun openGame(url: String) {
        isInGame = true
        gpuHintSent = false
        // Remote pages must never see the native bridge. Takes effect on the next page load.
        webView.removeJavascriptInterface(BRIDGE_NAME)
        webView.settings.userAgentString = gameUserAgent()
        webView.loadUrl(url)
    }

    override fun reload() = webView.reload()

    override fun askLauncherBack(callback: (Boolean) -> Unit) {
        val js = "(function(){try{return !!(window.PlayTVLauncher&&PlayTVLauncher.handleBack());}catch(e){return false;}})()"
        webView.evaluateJavascript(js) { callback(it == "true") }
    }

    override fun sendKey(keyCode: Int, down: Boolean) {
        val now = SystemClock.uptimeMillis()
        val action = if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        webView.dispatchKeyEvent(KeyEvent(now, now, action, keyCode, 0))
    }

    override fun setInputLocked(locked: Boolean) {
        webView.isFocusable = !locked
        webView.isFocusableInTouchMode = !locked
        if (locked) webView.clearFocus() else webView.requestFocus()
    }

    override fun clearWebData() {
        webView.clearCache(true)
        webView.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    override fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customView?.let { container.removeView(it) }
        customView = null
        customViewCallback = null
    }

    override fun onPause() = webView.onPause()
    override fun onResume() = webView.onResume()

    override fun close() {
        try {
            webView.stopLoading()
            container.removeAllViews()
            webView.destroy()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ JS bridge (launcher only)

    private inner class Bridge {
        @JavascriptInterface
        fun launchGame(json: String) {
            try {
                val o = JSONObject(json)
                val url = o.getString("url")
                if (!url.startsWith("https://")) return
                val session = GameSession(
                    id = o.optString("id", url),
                    name = o.optString("name", "Game"),
                    url = url,
                    input = o.optString("input", "cursor"),
                    requires = o.optString("requires", "none")
                )
                activity.runOnUiThread { listener.onLaunchRequested(session) }
            } catch (e: Exception) {
                Log.e(TAG, "launchGame: ${e.message}")
            }
        }

        @JavascriptInterface
        fun getPref(key: String): String =
            if (key in PREF_KEYS) prefs.getString("ui.$key") else ""

        @JavascriptInterface
        fun setPref(key: String, value: String) {
            if (key in PREF_KEYS && value.length <= 20_000) prefs.setString("ui.$key", value)
        }

        @JavascriptInterface
        fun getCatalog(): String =
            activity.assets.open("games.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

        @JavascriptInterface
        fun clearWebData() {
            activity.runOnUiThread { this@WebViewEngine.clearWebData() }
        }

        @JavascriptInterface
        fun getDeviceInfo(): String {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalMb = mem.totalMem / (1024 * 1024)
            val webViewVersion = try {
                WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "unknown"
            } catch (_: Exception) { "unknown" }
            val appVersion = try {
                activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "?"
            } catch (_: Exception) { "?" }
            return JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("deviceModel", Build.MODEL)
                put("androidVersion", Build.VERSION.RELEASE)
                put("apiLevel", Build.VERSION.SDK_INT)
                put("totalMemMb", totalMb)
                put("availMemMb", mem.availMem / (1024 * 1024))
                put("cpuAbi", Build.SUPPORTED_ABIS.joinToString(", "))
                put("engine", "Android WebView")
                put("webViewVersion", webViewVersion)
                put("appVersion", appVersion)
                put("lowRam", am.isLowRamDevice || totalMb < 1800)
            }.toString()
        }
    }

    companion object {
        private const val TAG = "WebViewEngine"
        private const val BRIDGE_NAME = "PlayTVNative"
        private val PREF_KEYS = setOf("fav", "recent")
        private val ALLOWED_SCHEMES = setOf("http", "https", "about", "blob", "data")
    }
}
