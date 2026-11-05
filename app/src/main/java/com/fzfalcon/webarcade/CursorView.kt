package com.fzfalcon.webarcade

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Mouse pointer drawn above the browser. Purely visual: it never takes touch or focus. */
class CursorView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val pressedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFB020"); style = Paint.Style.FILL }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
    }
    private val arrow = Path()

    private var px = 0f
    private var py = 0f

    var buttonDown: Boolean = false
        set(value) { field = value; invalidate() }

    init {
        isFocusable = false
        isClickable = false
        visibility = GONE
        setWillNotDraw(false)
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val u = maxOf(1f, width / 1920f) * 1.1f
        arrow.reset()
        arrow.moveTo(0f, 0f)
        arrow.lineTo(0f, 30f * u)
        arrow.lineTo(8f * u, 23f * u)
        arrow.lineTo(14f * u, 36f * u)
        arrow.lineTo(19f * u, 34f * u)
        arrow.lineTo(13f * u, 21f * u)
        arrow.lineTo(23f * u, 21f * u)
        arrow.close()
        canvas.save()
        canvas.translate(px, py)
        canvas.drawPath(arrow, if (buttonDown) pressedFill else fill)
        canvas.drawPath(arrow, outline)
        canvas.restore()
    }
}
