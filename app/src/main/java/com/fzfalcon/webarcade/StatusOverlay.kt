package com.fzfalcon.webarcade

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Full-screen "loading <game>" cover so the user never stares at a black WebView. */
class StatusOverlay(context: Context) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val title = TextView(context)
    private val message = TextView(context)
    private val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)

    private val slowHint = Runnable {
        message.text = "Still loading. Big games can take a minute on this device.\nHold BACK for the menu, or press BACK to return Home."
    }

    private val hideRunnable = Runnable { visibility = GONE }

    init {
        visibility = GONE
        isClickable = true
        setBackgroundColor(Color.parseColor("#0A0C11"))

        title.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        message.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#8D97AA"))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        bar.apply {
            max = 100
            isIndeterminate = false
        }

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(title)
            addView(bar, LinearLayout.LayoutParams(dp(420), dp(10)).apply { topMargin = dp(24) })
            addView(message)
        }
        addView(column, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun showLoading(gameName: String) {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(slowHint)
        title.text = gameName
        message.text = "Starting…"
        bar.progress = 0
        visibility = VISIBLE
        handler.postDelayed(slowHint, 15_000)
    }

    fun setProgress(percent: Int) {
        if (visibility == VISIBLE) bar.progress = percent
    }

    /** Hide after a short delay so the game's first frame can appear underneath. */
    fun hideSoon() {
        handler.removeCallbacks(slowHint)
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 500)
    }

    fun hideNow() {
        handler.removeCallbacks(slowHint)
        handler.removeCallbacks(hideRunnable)
        visibility = GONE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
