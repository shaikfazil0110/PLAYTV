package com.fzfalcon.webarcade

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Turns the D-pad / stick into a real mouse for pointer-driven web games.
 * Events are injected with TOOL_TYPE_MOUSE so the page sees mousemove / mousedown / mouseup / wheel,
 * not touch gestures.
 */
class CursorController(private val target: View, private val cursor: CursorView) {

    var enabled = false
        private set

    private var px = 0f
    private var py = 0f
    private var pressed = false
    private var downTime = 0L

    fun enable(on: Boolean) {
        if (on == enabled) return
        enabled = on
        if (on) {
            if (px == 0f && py == 0f) {
                px = target.width / 2f
                py = target.height / 2f
            }
            cursor.moveTo(px, py)
            cursor.visibility = View.VISIBLE
        } else {
            release()
            cursor.visibility = View.GONE
        }
    }

    /** Moves the pointer. Pushing against the top/bottom edge scrolls the page. */
    fun move(dx: Float, dy: Float) {
        if (!enabled || target.width == 0) return
        val maxX = target.width - 1f
        val maxY = target.height - 1f
        val nx = px + dx
        val ny = py + dy
        if (ny > maxY && dy > 0f) scroll(-0.4f)
        if (ny < 0f && dy < 0f) scroll(0.4f)
        px = nx.coerceIn(0f, maxX)
        py = ny.coerceIn(0f, maxY)
        cursor.moveTo(px, py)
        if (pressed) dispatch(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_PRIMARY, touch = true)
        else dispatch(MotionEvent.ACTION_HOVER_MOVE, 0, touch = false)
    }

    fun press() {
        if (!enabled || pressed) return
        pressed = true
        downTime = SystemClock.uptimeMillis()
        cursor.buttonDown = true
        dispatch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY, touch = true)
    }

    fun release() {
        if (!pressed) return
        pressed = false
        cursor.buttonDown = false
        dispatch(MotionEvent.ACTION_UP, 0, touch = true)
    }

    /** Positive = wheel up (content moves down), negative = wheel down. */
    fun scroll(amount: Float) {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = px; y = py
            setAxisValue(MotionEvent.AXIS_VSCROLL, amount)
        })
        val ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        target.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private fun dispatch(action: Int, buttons: Int, touch: Boolean) {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = px; y = py
            pressure = if (buttons != 0) 1f else 0f
            size = 1f
        })
        val start = if (touch) downTime else now
        val ev = MotionEvent.obtain(start, now, action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        if (touch) target.dispatchTouchEvent(ev) else target.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }
}
