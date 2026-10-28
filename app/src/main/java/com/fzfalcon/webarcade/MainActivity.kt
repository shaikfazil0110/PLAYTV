package com.fzfalcon.webarcade

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var gameWebView: WebView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    data class Game(val name: String, val genre: String, val url: String, val icon: String, val note: String)
    private val bg = Color.rgb(5, 7, 17)
    private val panel = Color.rgb(18, 23, 42)
    private val cyan = Color.rgb(65, 226, 255)
    private val white = Color.rgb(245, 247, 255)
    private val muted = Color.rgb(160, 174, 202)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()
        showLibrary()
    }

    private fun enableImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun readGames(): List<Game> = try {
        val raw = assets.open("games.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Game(o.getString("name"), o.getString("genre"), o.getString("url"), o.optString("icon", "🎮"), o.optString("note", ""))
        }
    } catch (_: Exception) { emptyList() }

    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 24f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, blend(color, Color.rgb(43, 22, 76)))).apply {
            cornerRadius = radius
            if (stroke != Color.TRANSPARENT) setStroke(2, stroke)
        }

    private fun blend(a: Int, b: Int): Int {
        fun ch(c: Int, shift: Int) = (Color.red(c) * (255 - shift) + Color.red(b) * shift) / 255
        return Color.rgb(ch(a, 90), (Color.green(a) * 165 + Color.green(b) * 90) / 255, (Color.blue(a) * 165 + Color.blue(b) * 90) / 255)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun baseScreen(title: String, subtitle: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 24, 42, 22)
            setBackgroundColor(bg)
            isFocusableInTouchMode = true
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, 14) }
        top.addView(label("▶  PLAYTV", 32f, cyan, true))
        top.addView(label(title, 25f, white, true).apply { setPadding(0, 8, 0, 2) })
        status = label(subtitle, 13f, muted).apply { setPadding(0, 4, 0, 8) }
        top.addView(status)
        root.addView(top)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; clipToPadding = false; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showLibrary() {
        gameWebView?.stopLoading()
        gameWebView?.destroy()
        gameWebView = null
        fullscreenView = null
        enableImmersiveMode()
        baseScreen("Your games. One screen. One click.", "NEON ARCADE  •  D-pad to browse  •  OK/A to play  •  BACK to return")
        val games = readGames()
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(26, 20, 26, 20)
            background = rounded(Color.rgb(18, 22, 48), Color.rgb(119, 76, 255), 28f)
        }
        hero.addView(label("THE PLAYTV ARCADE", 12f, Color.rgb(255, 104, 218), true))
        hero.addView(label("Press play. Enter another world.", 27f, white, true).apply { setPadding(0, 7, 0, 5) })
        hero.addView(label("Browser games and community ports. Compatibility depends on each game's website.", 14f, muted))
        content.addView(hero, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 18 })

        val categories = listOf("Featured", "Racing", "Shooter", "Arcade", "Puzzle", "Chess & Board", "Party & Multiplayer", "Strategy", "Sandbox", "Adventure", "Game Hubs")
        for (category in categories) {
            val list = when (category) { "Featured" -> games.take(6); else -> games.filter { it.genre.equals(category, true) } }
            if (list.isEmpty()) continue
            content.addView(label(category.uppercase(), 17f, white, true).apply { setPadding(2, 12, 0, 10) })
            val row = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFillViewport = false; clipToPadding = false }
            val rail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(3, 4, 8, 10) }
            list.forEach { game ->
                val tile = FrameLayout(this).apply {
                    background = rounded(Color.rgb(22, 28, 51), Color.rgb(46, 61, 95), 22f)
                    isFocusable = true; isFocusableInTouchMode = true
                    contentDescription = "${game.name}, ${game.genre}. Press OK to play"
                    clipToOutline = true
                }
                val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(14, 12, 14, 12) }
                val art = FrameLayout(this).apply { background = rounded(blend(Color.rgb(28, 40, 80), Color.rgb(80, 20, 115)), Color.TRANSPARENT, 18f) }
                val emoji = label(game.icon, 42f, white, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }
                art.addView(emoji, FrameLayout.LayoutParams(-1, -1))
                body.addView(art, LinearLayout.LayoutParams(-1, 88).apply { bottomMargin = 10 })
                body.addView(label(game.name, 14f, white, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; minHeight = 38 })
                body.addView(label(game.genre.uppercase(), 10f, cyan, true).apply { gravity = Gravity.CENTER; setPadding(0, 5, 0, 0) })
                tile.addView(body, FrameLayout.LayoutParams(-1, -1))
                tile.setOnFocusChangeListener { v, focused ->
                    v.background = rounded(if (focused) Color.rgb(34, 44, 76) else Color.rgb(22, 28, 51), if (focused) cyan else Color.rgb(46, 61, 95), 22f)
                    v.scaleX = if (focused) 1.045f else 1f; v.scaleY = if (focused) 1.045f else 1f
                    v.elevation = if (focused) 12f else 2f
                }
                tile.setOnClickListener { launchGame(game) }
                val lp = LinearLayout.LayoutParams(204, 174); lp.rightMargin = 16; rail.addView(tile, lp)
                loadFavicon(game.url, emoji, art)
            }
            row.addView(rail)
            content.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 5 })
        }
        content.addView(label("BROWSER & CONTROLLER COMPATIBILITY", 12f, Color.rgb(255, 104, 218), true).apply { setPadding(0, 20, 0, 5) })
        content.addView(label("PLAYTV enables JavaScript, storage, hardware acceleration and fullscreen video. Some sites still require desktop Chrome/Edge, keyboard/mouse, login, or browser features not available in Android WebView. Use OPEN IN BROWSER when a game cannot run here. Gamepad buttons are forwarded where Android exposes them; individual websites must implement controller support.", 12f, muted))
        content.getChildAt(1)?.requestFocus()
    }

    private fun loadFavicon(pageUrl: String, fallback: TextView, art: FrameLayout) {
        val host = try { Uri.parse(pageUrl).host ?: return } catch (_: Exception) { return }
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://www.google.com/s2/favicons?domain=${Uri.encode(host)}&sz=128").openConnection() as HttpURLConnection
                connection.connectTimeout = 4000; connection.readTimeout = 4000
                connection.instanceFollowRedirects = true
                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) runOnUiThread {
                    val image = ImageView(this).apply { setImageBitmap(bitmap); scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(22, 18, 22, 18) }
                    art.removeAllViews(); art.addView(image, FrameLayout.LayoutParams(-1, -1)); fallback.visibility = View.GONE
                }
            } catch (_: Exception) { /* emoji artwork remains as an offline fallback */ }
            finally { connection?.disconnect() }
        }
    }

    private fun launchGame(game: Game) {
        gameWebView?.destroy(); gameWebView = null
        enableImmersiveMode()
        baseScreen(game.name, "LOADING GAME  •  BACK returns to PLAYTV  •  MENU offers browser fallback")
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, 8) }
        val back = actionLabel("‹  PLAYTV", cyan) { showLibrary() }
        val browser = actionLabel("OPEN IN BROWSER ↗", Color.rgb(255, 174, 82)) { openExternal(game.url) }
        bar.addView(back); bar.addView(browser, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = 12 })
        content.addView(bar)
        val web = WebView(this)
        gameWebView = web
        web.setBackgroundColor(Color.BLACK)
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            allowContentAccess = true
            loadsImagesAutomatically = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                fullscreenView = view; fullscreenCallback = callback
                root.removeAllViews(); (view.parent as? ViewGroup)?.removeView(view)
                root.addView(view, LinearLayout.LayoutParams(-1, -1)); enableImmersiveMode()
            }
            override fun onHideCustomView() { exitWebFullscreen() }
            override fun onProgressChanged(view: WebView?, newProgress: Int) { status.text = "Loading game… $newProgress%  •  BACK to library" }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase()
                if (scheme == "intent" || scheme == "market") { openExternal(uri.toString()); return true }
                return false
            }
            override fun onPageFinished(view: WebView?, url: String?) { status.text = "Game loaded  •  Controller support depends on the game  •  MENU: browser" }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    status.text = "This game may need a full desktop browser. Select OPEN IN BROWSER."
                    Toast.makeText(this@MainActivity, "Website failed in WebView. Try OPEN IN BROWSER.", Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        web.requestFocus(); web.loadUrl(game.url)
    }

    private fun actionLabel(text: String, color: Int, action: () -> Unit) = label(text, 13f, color, true).apply {
        isFocusable = true; isFocusableInTouchMode = true; background = rounded(panel, color, 14f); setPadding(18, 12, 18, 12); setOnClickListener { action() }
    }

    private fun openExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No compatible browser is installed. Install a browser on this device, or try this game on PC Chrome/Edge.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open an external browser on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun exitWebFullscreen() {
        val callback = fullscreenCallback
        fullscreenCallback = null; fullscreenView = null
        callback?.onCustomViewHidden()
        showLibrary()
    }

    private fun isGamepadKey(keyCode: Int): Boolean = keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE ||
            keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_BUTTON_1 || keyCode == KeyEvent.KEYCODE_BUTTON_2

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (fullscreenView != null && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { exitWebFullscreen(); return true }
        val web = gameWebView
        if (web != null && isGamepadKey(event.keyCode)) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) { showLibrary(); return true }
            if (web.dispatchKeyEvent(event)) return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && gameWebView != null) { showLibrary(); return true }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (fullscreenView != null) exitWebFullscreen()
        else if (gameWebView != null) showLibrary()
        else super.onBackPressed()
    }

    override fun onDestroy() { gameWebView?.stopLoading(); gameWebView?.destroy(); super.onDestroy() }
}
