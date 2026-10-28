package com.fzfalcon.webarcade

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import org.json.JSONObject

class LegacyWebViewEngine(
    private val activity: Activity,
    private val onEngineEvent: (EngineEvent) -> Unit
) : PlayBrowserEngine {

    sealed class EngineEvent {
        data class PageStarted(val url: String) : EngineEvent()
        data class PageFinished(val url: String) : EngineEvent()
        data class Error(val code: Int, val description: String, val failingUrl: String) : EngineEvent()
        data class WebGpuCompatibilityWarning(val message: String) : EngineEvent()
        object RequestLauncherHome : EngineEvent()
    }

    private val container: FrameLayout = FrameLayout(activity).apply {
        setBackgroundColor(Color.parseColor("#090B0E"))
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private var fullScreen: Boolean = false
    private var fullScreenListener: ((Boolean) -> Unit)? = null
    private var isPlayingGame: Boolean = false
    private var currentGameTitle: String = ""

    val webView: WebView = createWebView()
    private val defaultUserAgent: String = webView.settings.userAgentString

    override val view: View
        get() = container

    init {
        container.addView(webView, matchParentLayoutParams())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val browser = WebView(activity)
        browser.setBackgroundColor(Color.parseColor("#090B0E"))
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        browser.isFocusable = true
        browser.isFocusableInTouchMode = true

        val s = browser.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(false)
        s.allowFileAccess = true
        s.allowContentAccess = true
        s.cacheMode = WebSettings.LOAD_DEFAULT

        browser.addJavascriptInterface(PlayTVJsBridge(), "PlayTVNative")

        browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { onEngineEvent(EngineEvent.PageStarted(it)) }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectCompatibilityPolyfills(browser)
                url?.let { onEngineEvent(EngineEvent.PageFinished(it)) }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString() ?: "Resource Error"
                } else "Resource Error"
                val url = request?.url?.toString() ?: ""
                Log.w(TAG, "WebView error: $desc at $url")
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val didCrash = detail?.didCrash() ?: true
                Log.e(TAG, "Render process gone. Crashed: $didCrash")
                onEngineEvent(EngineEvent.Error(-1, "Renderer memory or driver failure. Recovering...", ""))
                loadLauncher()
                return true
            }
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

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val msg = consoleMessage?.message() ?: ""
                Log.d("WebConsole", msg)
                if (msg.contains("WebGPU", ignoreCase = true) &&
                    (msg.contains("failed", ignoreCase = true) || msg.contains("could not find", ignoreCase = true))) {
                    onEngineEvent(EngineEvent.WebGpuCompatibilityWarning(msg))
                }
                return true
            }
        }

        return browser
    }

    private fun injectCompatibilityPolyfills(target: WebView) {
        val shim = """
            (function() {
                if (!navigator.gpu) {
                    console.warn('[PLAYTV] navigator.gpu not present in system webview.');
                    return;
                }
                const orig = navigator.gpu.requestAdapter.bind(navigator.gpu);
                navigator.gpu.requestAdapter = async function(opts) {
                    try {
                        const a = await orig(opts);
                        if (!a && window.PlayTVNative && window.PlayTVNative.onWebGpuFallback) {
                            window.PlayTVNative.onWebGpuFallback('Adapter unavailable from GPU driver');
                        }
                        return a;
                    } catch(err) {
                        if (window.PlayTVNative && window.PlayTVNative.onWebGpuFallback) {
                            window.PlayTVNative.onWebGpuFallback(err.message || 'GPU Adapter failure');
                        }
                        throw err;
                    }
                };
            })();
        """.trimIndent()
        target.evaluateJavascript(shim, null)
    }

    private fun matchParentLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    fun loadLauncher() {
        isPlayingGame = false
        currentGameTitle = ""
        webView.loadUrl("file:///android_asset/steam_launcher/index.html")
    }

    override fun loadUrl(url: String) {
        isPlayingGame = !url.contains("steam_launcher/index.html")
        webView.loadUrl(url)
    }

    override fun setDesktopMode(enabled: Boolean) {
        val desktopUa = "Mozilla/5.0 (Linux; Android 9; AFTMM) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
        webView.settings.userAgentString = if (enabled) desktopUa else defaultUserAgent
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
    }

    override fun canGoBack(): Boolean = webView.canGoBack()

    override fun goBack() {
        if (isPlayingGame) {
            loadLauncher()
        } else if (webView.canGoBack()) {
            webView.goBack()
        }
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
        } catch (_: Exception) {}
    }

    inner class PlayTVJsBridge {
        @JavascriptInterface
        fun launchGame(gameJson: String) {
            try {
                val json = JSONObject(gameJson)
                val url = json.getString("url")
                val title = json.getString("name")
                activity.runOnUiThread {
                    isPlayingGame = true
                    currentGameTitle = title
                    webView.loadUrl(url)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bridge launchGame error: ${e.message}")
            }
        }

        @JavascriptInterface
        fun getDeviceInfo(): String {
            val actManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)

            val root = JSONObject()
            root.put("deviceModel", Build.MODEL)
            root.put("androidVersion", Build.VERSION.RELEASE)
            root.put("apiLevel", Build.VERSION.SDK_INT)
            root.put("totalMemMb", memInfo.totalMem / (1024 * 1024))
            root.put("availMemMb", memInfo.availMem / (1024 * 1024))
            root.put("cpuAbi", Build.SUPPORTED_ABIS.joinToString(", "))
            return root.toString()
        }

        @JavascriptInterface
        fun onWebGpuFallback(reason: String) {
            onEngineEvent(EngineEvent.WebGpuCompatibilityWarning(reason))
        }
    }

    companion object {
        private const val TAG = "LegacyWebViewEngine"
    }
}
