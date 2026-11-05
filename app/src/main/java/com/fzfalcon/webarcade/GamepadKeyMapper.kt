package com.fzfalcon.webarcade

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Optional "gamepad as keyboard" layer for games that only understand keys.
 *   A = Space, B = Esc, X = Enter, Y = Tab, left stick = arrow keys (or WASD for mouse+key games).
 * Games that use the browser Gamepad API should keep this OFF so they receive the real pad.
 */
class GamepadKeyMapper(private val sendKey: (keyCode: Int, down: Boolean) -> Unit) {

    var enabled = false
        set(value) {
            if (!value) releaseAll()
            field = value
        }

    /** true = left stick sends WASD, false = arrow keys */
    var wasd = false

    private val held = HashSet<Int>()

    /** @return true if the event was consumed */
    fun onKey(event: KeyEvent): Boolean {
        if (!enabled) return false
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_SPACE
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_ESCAPE
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_ENTER
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_TAB
            else -> return false
        }
        sendKey(mapped, event.action == KeyEvent.ACTION_DOWN)
        return true
    }

    /** @return true if the event was consumed */
    fun onMotion(ev: MotionEvent): Boolean {
        if (!enabled) return false
        if ((ev.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false
        if (ev.action != MotionEvent.ACTION_MOVE) return false
        val x = ev.getAxisValue(MotionEvent.AXIS_X)
        val y = ev.getAxisValue(MotionEvent.AXIS_Y)
        setHeld(if (wasd) KeyEvent.KEYCODE_A else KeyEvent.KEYCODE_DPAD_LEFT, x < -THRESHOLD)
        setHeld(if (wasd) KeyEvent.KEYCODE_D else KeyEvent.KEYCODE_DPAD_RIGHT, x > THRESHOLD)
        setHeld(if (wasd) KeyEvent.KEYCODE_W else KeyEvent.KEYCODE_DPAD_UP, y < -THRESHOLD)
        setHeld(if (wasd) KeyEvent.KEYCODE_S else KeyEvent.KEYCODE_DPAD_DOWN, y > THRESHOLD)
        return true
    }

    fun releaseAll() {
        for (code in held.toList()) sendKey(code, false)
        held.clear()
    }

    private fun setHeld(code: Int, on: Boolean) {
        if (on) {
            if (held.add(code)) sendKey(code, true)
        } else if (held.remove(code)) {
            sendKey(code, false)
        }
    }

    private companion object {
        const val THRESHOLD = 0.5f
    }
}
