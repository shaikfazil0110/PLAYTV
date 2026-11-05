package com.fzfalcon.webarcade

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Pause menu opened by long-pressing BACK or pressing MENU / START while a game is running. */
class GameMenuView(context: Context, private val actions: Actions) : FrameLayout(context) {

    interface Actions {
        fun onResume()
        fun onHome()
        fun onReload()
        /** @return the new state */
        fun onToggleCursor(): Boolean
        /** @return the new state */
        fun onToggleGamepadKeys(): Boolean
    }

    private val title = TextView(context)
    private val cursorItem: TextView
    private val padItem: TextView
    private val first: TextView

    val isShowing: Boolean get() = visibility == VISIBLE

    init {
        visibility = GONE
        isClickable = true // swallow touches while open
        setBackgroundColor(Color.argb(190, 4, 6, 10))

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(28)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161B25"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(2), Color.parseColor("#262D3B"))
            }
        }
        title.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#FFB020"))
            letterSpacing = 0.12f
            setPadding(dp(8), 0, dp(8), dp(14))
        }
        panel.addView(title)

        first = item("Resume") { actions.onResume() }
        panel.addView(first, rowParams())
        panel.addView(item("Back to Home") { actions.onHome() }, rowParams())
        panel.addView(item("Reload game") { actions.onReload() }, rowParams())
        cursorItem = item("") { }
        cursorItem.setOnClickListener { setLabel(cursorItem, "Cursor mode", actions.onToggleCursor()) }
        panel.addView(cursorItem, rowParams())
        padItem = item("") { }
        padItem.setOnClickListener { setLabel(padItem, "Gamepad as keyboard", actions.onToggleGamepadKeys()) }
        panel.addView(padItem, rowParams())

        addView(panel, LayoutParams(dp(520), LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun show(gameName: String, cursorOn: Boolean, padOn: Boolean) {
        title.text = "PAUSED  -  ${gameName.uppercase()}"
        setLabel(cursorItem, "Cursor mode", cursorOn)
        setLabel(padItem, "Gamepad as keyboard", padOn)
        visibility = VISIBLE
        first.requestFocus()
    }

    fun hide() {
        visibility = GONE
    }

    private fun setLabel(view: TextView, label: String, on: Boolean) {
        view.text = "$label:  ${if (on) "ON" else "OFF"}"
    }

    private fun item(text: String, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        setTextColor(Color.WHITE)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setOnClickListener { onClick() }
        applyFocusStyle(this, false)
        onFocusChangeListener = View.OnFocusChangeListener { v, focused -> applyFocusStyle(v as TextView, focused) }
    }

    private fun applyFocusStyle(v: TextView, focused: Boolean) {
        v.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (focused) Color.WHITE else Color.TRANSPARENT)
        }
        v.setTextColor(if (focused) Color.BLACK else Color.WHITE)
    }

    private fun rowParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(6) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
