package com.fzfalcon.webarcade

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var gameWebView: WebView? = null
    data class Game(val name: String, val genre: String, val url: String, val icon: String, val note: String)
    private val bg = Color.rgb(7, 10, 20)
    private val panel = Color.rgb(19, 25, 43)
    private val cyan = Color.rgb(63, 229, 255)
    private val white = Color.rgb(244, 247, 255)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showLibrary() }

    private fun readGames(): List<Game> = try {
        val raw = assets.open("games.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Game(o.getString("name"), o.getString("genre"), o.getString("url"), o.optString("icon", "🎮"), o.optString("note", "")) }
    } catch (_: Exception) { emptyList() }

    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT, radius: Float = 22f): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius; if (stroke != Color.TRANSPARENT) setStroke(2, stroke) }
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView = TextView(this).apply { this.text=text; textSize=size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD) }

    private fun baseScreen(title: String, subtitle: String) {
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(34, 22, 34, 22); setBackgroundColor(bg); isFocusableInTouchMode=true }
        val top = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,0,0,14) }
        top.addView(label("▶  PLAYTV", 30f, cyan, true))
        top.addView(label(title, 24f, white, true).apply { setPadding(0,10,0,2) })
        status = label(subtitle, 13f, Color.rgb(160,174,202))
        status.setPadding(0,4,0,8)
        top.addView(status)
        root.addView(top)
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport=true; isVerticalScrollBarEnabled=false; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    private fun showLibrary() {
        gameWebView?.stopLoading(); gameWebView?.destroy(); gameWebView=null
        baseScreen("Your games, all in one place", "NEON ARCADE  •  Use the Fire TV remote to browse and select")
        val games=readGames()
        val featured = games.take(4)
        val hero = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,18,24,18); background=rounded(Color.rgb(20,25,49), Color.rgb(89,79,255), 26f) }
        hero.addView(label("YOUR NEXT GAME IS HERE", 12f, Color.rgb(255,111,218), true))
        hero.addView(label("Play. Explore. Repeat.", 25f, white, true).apply { setPadding(0,7,0,5) })
        hero.addView(label("Browser games and web ports — no console install needed for supported titles.", 14f, Color.rgb(185,197,224)))
        content.addView(hero, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=20 })
        val categories = listOf("Featured", "Racing", "Shooter", "Arcade", "Puzzle", "Chess & Board", "Party & Multiplayer", "Strategy", "Sandbox", "Adventure", "Game Hubs")
        for (category in categories) {
            val list = when(category) { "Featured" -> featured; else -> games.filter { it.genre.equals(category, true) } }
            if (list.isEmpty()) continue
            content.addView(label(category.uppercase(), 17f, white, true).apply { setPadding(2,10,0,10) })
            val row = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; isFillViewport=false; clipToPadding=false }
            val rail = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            list.forEachIndexed { index, game ->
                val card = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(12,12,12,12); background=rounded(panel, Color.rgb(40,57,88), 20f); isFocusable=true; isFocusableInTouchMode=true; contentDescription="${game.name}, ${game.genre}" }
                val emoji = label(game.icon, 36f, white).apply { gravity=Gravity.CENTER; setPadding(0,0,0,8) }
                card.addView(emoji, LinearLayout.LayoutParams(-1, 58))
                card.addView(label(game.name, 13f, white, true).apply { gravity=Gravity.CENTER; maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END; minHeight=38 })
                card.addView(label(game.genre, 10f, cyan).apply { gravity=Gravity.CENTER; setPadding(0,5,0,0) })
                card.setOnFocusChangeListener { v, focused -> v.background=rounded(if(focused) Color.rgb(31,43,72) else panel, if(focused) cyan else Color.rgb(40,57,88), 20f); v.scaleX=if(focused) 1.04f else 1f; v.scaleY=if(focused) 1.04f else 1f }
                card.setOnClickListener { launchGame(game) }
                val lp=LinearLayout.LayoutParams(176,132); lp.rightMargin=12; rail.addView(card,lp)
            }
            row.addView(rail); content.addView(row, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=8 })
        }
        content.addView(label("CATALOGUE NOTE", 12f, Color.rgb(255,111,218), true).apply { setPadding(0,20,0,5) })
        content.addView(label("PLAYTV is a curated launcher, not literally every game on the internet. Website availability, ads, sign-ins, controller support, and Fire TV WebView compatibility vary. Game tiles use built-in icon artwork so the catalogue loads without downloading dozens of separate logos.", 12f, Color.rgb(145,158,185)))
        content.getChildAt(1)?.requestFocus()
    }

    private fun launchGame(game: Game) {
        baseScreen(game.name, "${game.note}  •  Press BACK to return to PLAYTV")
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,0,0,10) }
        val back=label("‹  BACK TO PLAYTV", 14f, cyan, true).apply { isFocusable=true; isFocusableInTouchMode=true; background=rounded(panel, cyan, 14f); setPadding(18,12,18,12); setOnClickListener { showLibrary() } }
        bar.addView(back); content.addView(bar)
        val web=WebView(this); gameWebView=web; web.setBackgroundColor(Color.BLACK)
        web.settings.javaScriptEnabled=true; web.settings.domStorageEnabled=true; web.settings.mediaPlaybackRequiresUserGesture=false; web.settings.loadWithOverviewMode=true; web.settings.useWideViewPort=true
        web.webChromeClient=WebChromeClient()
        web.webViewClient=object: WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) { status.text="Opening website • Press BACK to return to PLAYTV" }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) { if(request?.isForMainFrame==true) Toast.makeText(this@MainActivity,"This website may not support Fire TV WebView. Try opening it in a supported browser.",Toast.LENGTH_LONG).show() }
        }
        content.addView(web, LinearLayout.LayoutParams(-1,0,1f)); web.loadUrl(game.url); back.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean { if(keyCode==KeyEvent.KEYCODE_BACK && gameWebView!=null) { showLibrary(); return true }; return super.onKeyDown(keyCode,event) }
    override fun onDestroy() { gameWebView?.destroy(); super.onDestroy() }
}
