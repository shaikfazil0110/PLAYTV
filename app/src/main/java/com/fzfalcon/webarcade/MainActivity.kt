package com.fzfalcon.webarcade

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var outer: FrameLayout
    private var browserEngine: PlayBrowserEngine? = null
    private var browserToolbar: LinearLayout? = null
    private var browserHost: FrameLayout? = null
    private var pageFullscreen = false
    private var desktopMode = true
    private var currentGame: Game? = null
    private val imageCache = ConcurrentHashMap<String, android.graphics.Bitmap>()
    private val prefs by lazy { getSharedPreferences("playtv", MODE_PRIVATE) }

    data class Game(val name: String, val genre: String, val url: String, val note: String, val platform: String = "BROWSER", val art: String = "") {
        val host: String get() = Uri.parse(url).host?.removePrefix("www.") ?: "web game"
        val badge: String get() = name.split(Regex("[^A-Za-z0-9]+" )).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "TV" }
    }

    private val bg = Color.rgb(6, 8, 18)
    private val panel = Color.rgb(18, 23, 41)
    private val cyan = Color.rgb(79, 224, 255)
    private val purple = Color.rgb(126, 90, 255)
    private val white = Color.rgb(246, 248, 255)
    private val muted = Color.rgb(158, 170, 198)
    private val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()
        showLibrary()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun enableImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun readGames(): List<Game> = try {
        val raw = assets.open("games.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Game(o.getString("name"), o.getString("genre"), o.getString("url"), o.optString("note", "Browser game"), o.optString("platform", "BROWSER"), o.optString("art", ""))
        }
    } catch (_: Exception) { emptyList() }

    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 22f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, mix(color, Color.rgb(43, 20, 78)))).apply {
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(2), stroke)
        }

    private fun mix(a: Int, b: Int): Int = Color.rgb(
        (Color.red(a) + Color.red(b)) / 2,
        (Color.green(a) + Color.green(b)) / 2,
        (Color.blue(a) + Color.blue(b)) / 2)

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(null, Typeface.BOLD)
        isFocusable = false
    }

    private fun focusable(view: View, onFocus: (Boolean) -> Unit = {}): View = view.apply {
        isFocusable = true; isFocusableInTouchMode = true
        setOnFocusChangeListener { v, focused ->
            v.scaleX = if (focused) 1.035f else 1f
            v.scaleY = if (focused) 1.035f else 1f
            onFocus(focused)
        }
    }

    private fun tvButton(caption: String, prominent: Boolean = false, action: () -> Unit): TextView =
        text(caption, if (prominent) 16f else 14f, if (prominent) Color.BLACK else white, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(22), dp(12), dp(22), dp(12))
            background = rounded(if (prominent) cyan else panel, if (prominent) white else Color.rgb(48, 62, 91), 14f)
            focusable(this) { f -> background = rounded(if (prominent || f) cyan else panel, if (f) white else Color.rgb(48,62,91), 14f); setTextColor(if (prominent || f) Color.BLACK else white) }
            setOnClickListener { action() }
        }

    private fun showLibrary(query: String = "", section: String = "HOME") {
        closeBrowser()
        enableImmersiveMode()
        pageFullscreen = false
        outer = FrameLayout(this).apply { setBackgroundColor(bg); isFocusableInTouchMode = true }
        val split = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bg) }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(24), dp(14), dp(18)); setBackgroundColor(Color.rgb(10, 13, 27))
        }
        nav.addView(text("PLAY\nTV", 25f, cyan, true).apply { gravity = Gravity.CENTER; setLineSpacing(0f, .9f); setPadding(0,0,0,dp(28)) }, LinearLayout.LayoutParams(dp(88), dp(68)))
        listOf("⌂\nHOME", "⌕\nSEARCH", "★\nSAVED", "◷\nRECENT", "⚙\nTEST").forEach { option ->
            val key = option.substringAfterLast('\n')
            val selected = when (section) { "FAVORITES" -> key == "SAVED"; "RECENT" -> key == "RECENT"; "TEST" -> key == "TEST"; "SEARCH" -> key == "SEARCH"; else -> key == "HOME" }
            val item = text(option, 12f, if (selected) cyan else muted, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(3), dp(12), dp(3), dp(12)); background = rounded(if (selected) Color.rgb(25,36,63) else Color.TRANSPARENT, if (selected) cyan else Color.TRANSPARENT, 14f)
                focusable(this) { f -> background = rounded(if (f || selected) Color.rgb(25,36,63) else Color.TRANSPARENT, if (f || selected) cyan else Color.TRANSPARENT, 14f) }
                setOnClickListener { when (key) {
                    "SAVED" -> showLibrary("", "FAVORITES")
                    "RECENT" -> showLibrary("", "RECENT")
                    "SEARCH" -> showLibrary("", "SEARCH")
                    "TEST" -> showDiagnostics()
                    else -> showLibrary()
                } }
            }
            val lp = LinearLayout.LayoutParams(dp(88), dp(68)); lp.bottomMargin = dp(7); nav.addView(item, lp)
        }
        split.addView(nav, LinearLayout.LayoutParams(dp(116), -1))

        val scroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; clipToPadding = false }
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(34), dp(25), dp(36), dp(30)) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text(when (section) { "FAVORITES" -> "Your saved games"; "RECENT" -> "Continue playing"; "SEARCH" -> "Find your next game"; else -> "Your gaming universe" }, 27f, white, true))
        titleBlock.addView(text("A gaming platform built for the big screen", 14f, muted).apply { setPadding(0, dp(4), 0, 0) })
        head.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        var searchResultsContainer: LinearLayout? = null
        val search = EditText(this).apply {
            hint = "Search games or websites"; setHintTextColor(muted); setTextColor(white); textSize = 14f
            setSingleLine(true); setPadding(dp(14), dp(9), dp(14), dp(9)); background = rounded(panel, Color.rgb(51,64,95), 14f)
            isFocusable = true; isFocusableInTouchMode = true
            if (query.isNotBlank()) setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (section == "SEARCH") searchResultsContainer?.let { refreshSearchResults(it, s?.toString().orEmpty()) } }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        if (section == "SEARCH" || query.isNotBlank()) head.addView(search, LinearLayout.LayoutParams(dp(270), dp(48)).apply { leftMargin = dp(16) })
        main.addView(head)

        val all = readGames()
        var games = when (section) {
            "FAVORITES" -> all.filter { isFavorite(it) }
            "RECENT" -> all.filter { it.url == prefs.getString("recent_url", "") }
            else -> all
        }
        if (query.isNotBlank()) games = games.filter { it.name.contains(query, true) || it.genre.contains(query, true) || it.platform.contains(query, true) || it.host.contains(query, true) }

        if (section == "TEST") return showDiagnostics()
        if (section == "SEARCH") {
            val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(18),0,0) }
            searchResultsContainer = results
            main.addView(results)
            refreshSearchResults(results, search.text.toString())
        } else if (section == "FAVORITES" || section == "RECENT" || query.isNotBlank()) {
            main.addView(text("${games.size} GAMES", 13f, cyan, true).apply { setPadding(0, dp(24), 0, dp(10)) })
            addCardRow(main, games, large = true)
            if (games.isEmpty()) main.addView(text("Nothing here yet. Long-press a game card to save it to Favorites.", 16f, muted).apply { setPadding(0,dp(14),0,0) })
        } else {
            val heroGame = all.firstOrNull()
            main.addView(buildHero(heroGame), LinearLayout.LayoutParams(-1, dp(240)).apply { topMargin = dp(22); bottomMargin = dp(28) })
            val recent = all.filter { it.url == prefs.getString("recent_url", "") }
            if (recent.isNotEmpty()) addShelf(main, "CONTINUE PLAYING", recent)
            addShelf(main, "FEATURED", all.take(6))
            all.map { it.platform }.distinct().forEach { platform ->
                val items = all.filter { it.platform.equals(platform, true) }
                if (items.isNotEmpty()) addShelf(main, platform, items)
            }
            main.addView(text("PLAYTV BROWSER ENGINE", 13f, Color.rgb(255, 122, 217), true).apply { setPadding(0, dp(26), 0, dp(6)) })
            main.addView(text("The Chromium build bundles its own engine. Game compatibility still depends on each game's APIs, graphics requirements and website policies. Press TEST in the side menu to view the active build and input hints.", 13f, muted))
        }
        scroll.addView(main)
        split.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        outer.addView(split)
        setContentView(outer)
        nav.getChildAt(if (section == "HOME") 1 else if (section == "SEARCH") 2 else if (section == "FAVORITES") 3 else 4)?.requestFocus()
    }

    private fun buildHero(game: Game?): View {
        val hero = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(18, 25, 60), Color.rgb(68, 25, 89), Color.rgb(9, 24, 40))).apply { cornerRadius = dp(26).toFloat() }
            clipToOutline = true
            setPadding(dp(26), dp(20), dp(26), dp(20))
            isFocusable = true
        }
        val visual = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .48f }
        if (game != null) loadSiteIcon(game, visual)
        hero.addView(visual, FrameLayout.LayoutParams(-1, -1))
        val shade = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(8, 10, 24), Color.argb(225, 8, 10, 24), Color.argb(35, 8, 10, 24)))
        }
        hero.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val overlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4),0,dp(4),0) }
        overlay.addView(text("PLAYTV  •  FEATURED GAME", 12f, cyan, true))
        overlay.addView(text(game?.name ?: "YOUR GAMING UNIVERSE", 29f, white, true).apply { setPadding(0, dp(9), 0, dp(5)); maxLines = 2 })
        overlay.addView(text(game?.note ?: "Explore browser games and community ports.", 14f, Color.rgb(204,214,236)).apply { maxLines = 2 })
        overlay.addView(tvButton("▶   PLAY NOW", true) { if (game != null) launchGame(game) }.apply { setPadding(dp(24),dp(12),dp(24),dp(12)) }, LinearLayout.LayoutParams(dp(172), dp(50)).apply { topMargin = dp(18) })
        hero.addView(overlay, FrameLayout.LayoutParams(dp(560), -1, Gravity.LEFT))
        return hero
    }

    private fun addShelf(parent: LinearLayout, heading: String, games: List<Game>) {
        parent.addView(text(heading, 16f, white, true).apply { setPadding(dp(2), dp(5), 0, dp(12)) })
        addCardRow(parent, games, large = false)
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(19)))
    }

    private fun addCardRow(parent: LinearLayout, games: List<Game>, large: Boolean) {
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false; isFillViewport = false }
        val rail = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        games.forEach { game ->
            val card = FrameLayout(this).apply {
                background = rounded(panel, Color.rgb(38,50,79), 20f)
                clipToOutline = true
                isFocusable = true
                isFocusableInTouchMode = true
                transitionName = "game-${game.badge}"
            }
            val fallback = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(19,38,67), Color.rgb(46,26,68)))
            val art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = fallback; contentDescription = "${game.name} cover artwork" }
            loadSiteIcon(game, art)
            card.addView(art, FrameLayout.LayoutParams(-1, -1))
            val shade = View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, Color.argb(95, 5, 8, 18), Color.rgb(5, 8, 18)))
            }
            card.addView(shade, FrameLayout.LayoutParams(-1, -1))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM
                setPadding(dp(14), dp(10), dp(14), dp(13))
            }
            info.addView(text(game.name, 15f, white, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            info.addView(text("${game.genre.uppercase()}  •  ${game.platform}", 10f, cyan, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0,dp(5),0,0)
            })
            card.addView(info, FrameLayout.LayoutParams(-1, if (large) dp(104) else dp(96), Gravity.BOTTOM))
            focusable(card) { focused ->
                card.animate().scaleX(if (focused) 1.045f else 1f).scaleY(if (focused) 1.045f else 1f).setDuration(130).start()
                card.background = rounded(if (focused) Color.rgb(30,47,76) else panel, if (focused) cyan else Color.rgb(38,50,79), 20f)
                card.elevation = if (focused) dp(12).toFloat() else 0f
            }
            card.contentDescription = "${game.name}, ${game.genre}, ${game.platform}. Press select to play. Long press to save."
            card.setOnClickListener { launchGame(game) }
            card.setOnLongClickListener { toggleFavorite(game); Toast.makeText(this, if (isFavorite(game)) "Saved to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show(); true }
            val lp = LinearLayout.LayoutParams(if (large) dp(270) else dp(226), if (large) dp(170) else dp(156)); lp.rightMargin = dp(16); rail.addView(card, lp)
        }
        horizontal.addView(rail)
        parent.addView(horizontal, LinearLayout.LayoutParams(-1, if (large) dp(188) else dp(174)))
    }

    private fun refreshSearchResults(container: LinearLayout, q: String) {
        container.removeAllViews()
        val results = readGames().filter { it.name.contains(q, true) || it.genre.contains(q, true) || it.platform.contains(q, true) || it.host.contains(q, true) }
        container.addView(text("${results.size} RESULTS", 13f, cyan, true).apply { setPadding(0,0,0,dp(12)) })
        addCardRow(container, results, large = true)
        if (results.isEmpty()) container.addView(text("No games found. Try a genre or website name.", 15f, muted))
    }

    private fun loadSiteIcon(game: Game, target: ImageView) {
        // Prefer explicit art, then Open Graph/Twitter share-card artwork.
        // Some sites publish no public artwork, so a favicon is the final fallback.
        val cacheKey = game.art.ifBlank { game.url }
        val cached = imageCache[cacheKey]
        if (cached != null) { target.setImageBitmap(cached); return }

        thread(name = "playtv-artwork") {
            var bitmap: android.graphics.Bitmap? = null
            try {
                var imageUrl = game.art.trim()
                if (imageUrl.isBlank()) {
                    val pageConnection = URL(game.url).openConnection() as HttpURLConnection
                    pageConnection.connectTimeout = 7000
                    pageConnection.readTimeout = 7000
                    pageConnection.instanceFollowRedirects = true
                    pageConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; PLAYTV) AppleWebKit/537.36 Chrome/156.0.0.0 Safari/537.36")
                    pageConnection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                    val html = try {
                        pageConnection.inputStream.bufferedReader().use { it.readText().take(1500000) }
                    } finally {
                        pageConnection.disconnect()
                    }
                    fun metaAttr(tag: String, key: String): String? =
                        Regex("(?is)\\b" + key + "\\s*=\\s*[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
                    val tags = Regex("(?is)<meta\\b[^>]*>").findAll(html).map { it.value }
                    for (tag in tags) {
                        val field = (metaAttr(tag, "property") ?: metaAttr(tag, "name") ?: "").lowercase()
                        if (field == "og:image" || field == "twitter:image" || field == "twitter:image:src") {
                            val candidate = metaAttr(tag, "content")?.replace("&amp;", "&")?.replace("&quot;", "\"")?.trim().orEmpty()
                            if (candidate.isNotBlank()) {
                                imageUrl = try { URL(URL(game.url), candidate).toString() } catch (_: Exception) { candidate }
                                break
                            }
                        }
                    }
                }
                if (imageUrl.isBlank()) {
                    imageUrl = "https://www.google.com/s2/favicons?domain_url=${Uri.encode("https://${game.host}")}&sz=256"
                }
                val imageConnection = URL(imageUrl).openConnection() as HttpURLConnection
                imageConnection.connectTimeout = 7000
                imageConnection.readTimeout = 9000
                imageConnection.instanceFollowRedirects = true
                imageConnection.setRequestProperty("User-Agent", "PLAYTV/1.2")
                try {
                    imageConnection.inputStream.use { bitmap = BitmapFactory.decodeStream(it) }
                } finally {
                    imageConnection.disconnect()
                }
            } catch (_: Exception) { }
            if (bitmap != null) {
                imageCache[cacheKey] = bitmap!!
                runOnUiThread { if (!isFinishing && target.isAttachedToWindow) target.setImageBitmap(bitmap) }
            } else {
                runOnUiThread {
                    if (!isFinishing && target.isAttachedToWindow && target.drawable == null) {
                        target.setImageDrawable(null)
                        target.setBackground(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(25,41,68), Color.rgb(70,27,81))))
                    }
                }
            }
        }
    }

    private fun isFavorite(game: Game): Boolean = prefs.getStringSet("favorites", emptySet())?.contains(game.url) == true
    private fun toggleFavorite(game: Game) {
        val copy = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (copy.contains(game.url)) copy.remove(game.url) else copy.add(game.url)
        prefs.edit().putStringSet("favorites", copy).apply()
    }

    private fun launchGame(game: Game) {
        closeBrowser()
        currentGame = game; pageFullscreen = false; desktopMode = true
        prefs.edit().putString("recent_url", game.url).putString("recent_name", game.name).apply()
        enableImmersiveMode()
        outer = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(15),dp(7),dp(15),dp(7)); setBackgroundColor(Color.rgb(10,13,24)) }
        browserToolbar = toolbar
        toolbar.addView(tvButton("‹  LIBRARY") { showLibrary() }, LinearLayout.LayoutParams(-2, dp(42)))
        toolbar.addView(text(game.name, 15f, white, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(15),0,dp(10),0) }, LinearLayout.LayoutParams(0, -2, 1f))
        val uaBtn = tvButton("DESKTOP: ON") { desktopMode = !desktopMode; browserEngine?.setDesktopMode(desktopMode); uaBtnTextRefresh(desktopMode); browserEngine?.reload() }
        uaBtn.tag = "desktop-toggle"
        toolbar.addView(uaBtn, LinearLayout.LayoutParams(-2, dp(42)))
        toolbar.addView(tvButton("⟳ RELOAD") { browserEngine?.reload() }.apply { setPadding(dp(14),dp(10),dp(14),dp(10)) }, LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(8) })
        toolbar.addView(tvButton("FULLSCREEN") { pageFullscreen = !pageFullscreen; toolbar.visibility = if (pageFullscreen) View.GONE else View.VISIBLE; if (pageFullscreen) enableImmersiveMode() }, LinearLayout.LayoutParams(-2, dp(42)).apply { leftMargin = dp(8) })
        page.addView(toolbar, LinearLayout.LayoutParams(-1, dp(58)))
        val host = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); isFocusableInTouchMode = true }
        browserHost = host
        page.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        outer.addView(page, FrameLayout.LayoutParams(-1, -1))
        setContentView(outer)
        try {
            val engine = BrowserEngineFactory.create(this)
            browserEngine = engine
            engine.setFullscreenListener { full -> runOnUiThread {
                pageFullscreen = full
                toolbar.visibility = if (full) View.GONE else View.VISIBLE
                enableImmersiveMode()
            } }
            host.addView(engine.view, FrameLayout.LayoutParams(-1, -1))
            engine.setDesktopMode(desktopMode)
            engine.loadUrl(game.url)
            engine.view.isFocusable = true; engine.view.isFocusableInTouchMode = true; engine.view.requestFocus()
        } catch (e: Exception) {
            val message = text("The built-in browser failed to start.\n${e.message ?: "Unknown startup error"}", 18f, white).apply { setPadding(dp(28),dp(28),dp(28),dp(28)) }
            host.addView(message)
        }
        host.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_MENU) { toolbar.visibility = if (toolbar.visibility == View.VISIBLE) View.GONE else View.VISIBLE; true } else false
        }
    }

    private fun uaBtnTextRefresh(enabled: Boolean) {
        val view = outer.findViewWithTag<TextView>("desktop-toggle")
        view?.text = if (enabled) "DESKTOP: ON" else "DESKTOP: OFF"
    }

    private fun showDiagnostics() {
        closeBrowser(); enableImmersiveMode()
        outer = FrameLayout(this).apply { setBackgroundColor(bg) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(38),dp(28),dp(38),dp(28)) }
        column.addView(text("PLAYTV · Browser & Input Test", 28f, white, true))
        column.addView(text("Browser engine: ${BrowserEngineFactory.engineLabel}", 16f, cyan, true).apply { setPadding(0,dp(16),0,dp(10)) })
        column.addView(text("• Use D-pad arrows to move between game cards.\n• OK / Enter launches the focused game.\n• Press Back to return to the library.\n• Xbox / DualSense / generic gamepad inputs are passed to the browser; individual games must support the Gamepad API or keyboard mapping.\n• Desktop mode sends a desktop Chrome-style user agent; it cannot add missing graphics APIs or unsupported device capabilities.\n• On this app variant, all games remain inside PLAYTV. No external browser is launched.", 17f, muted).apply { setPadding(0,dp(14),0,dp(26)); setLineSpacing(dp(5).toFloat(),1f) })
        column.addView(tvButton("RUN BROWSER FEATURE TEST", true) { launchDiagnosticPage() }, LinearLayout.LayoutParams(-2, dp(54)))
        column.addView(tvButton("‹ BACK TO PLAYTV") { showLibrary() }.apply { setPadding(dp(20),dp(11),dp(20),dp(11)) }, LinearLayout.LayoutParams(-2,dp(48)).apply { topMargin = dp(14) })
        outer.addView(column); setContentView(outer)
        column.getChildAt(2).requestFocus()
    }

    private fun launchDiagnosticPage() {
        val test = Game("Browser Feature Test", "Diagnostics", "https://cefrium.com/modern-web/", "Test page for modern browser APIs")
        launchGame(test)
    }

    private fun closeBrowser() {
        try { browserEngine?.close() } catch (_: Exception) { }
        browserEngine = null; browserToolbar = null; browserHost = null; currentGame = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK && browserEngine != null) {
            if (pageFullscreen) { browserEngine?.exitFullscreen(); pageFullscreen = false; browserToolbar?.visibility = View.VISIBLE; enableImmersiveMode(); return true }
            if (browserEngine?.canGoBack() == true) { browserEngine?.goBack(); return true }
            showLibrary(); return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() { try { browserEngine?.onPause() } catch (_: Exception) { }; super.onPause() }
    override fun onResume() { super.onResume(); try { browserEngine?.onResume() } catch (_: Exception) { } }
    override fun onDestroy() { closeBrowser(); super.onDestroy() }
}
