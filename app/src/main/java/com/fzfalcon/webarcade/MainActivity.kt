package com.fzfalcon.webarcade

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Hosts the browser engine and the native overlays.
 *
 * Remote / gamepad behaviour
 *  - Launcher: D-pad is handled by the launcher page itself. BACK first goes to the launcher
 *    (close dialog / Home tab); a second BACK within 2s exits.
 *  - In a game: BACK = Home, hold BACK or MENU / START = pause menu.
 *  - Cursor mode: D-pad or left stick moves a mouse pointer, OK / A clicks, rewind / fast-forward scroll.
 *  - Gamepad-as-keyboard mode (optional): A/B/X/Y and the left stick become keys.
 */
class MainActivity : Activity(), EngineListener {

    private lateinit var prefs: Prefs
    private lateinit var root: FrameLayout
    private lateinit var engine: PlayBrowserEngine
    private lateinit var status: StatusOverlay
    private lateinit var cursorView: CursorView
    private lateinit var cursor: CursorController
    private lateinit var mapper: GamepadKeyMapper
    private lateinit var menu: GameMenuView

    private val handler = Handler(Looper.getMainLooper())
    private var session: GameSession? = null
    private var backPending = false
    private var lastExitBack = 0L

    // left stick state for cursor mode (a held stick does not generate repeated events)
    private var stickX = 0f
    private var stickY = 0f
    private var stickRunning = false

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        prefs = Prefs(this)
        root = FrameLayout(this).apply { setBackgroundColor(0xFF0A0C11.toInt()) }
        setContentView(root)

        engine = WebViewEngine(this, prefs, this)
        status = StatusOverlay(this)
        cursorView = CursorView(this)
        menu = GameMenuView(this, menuActions)

        root.addView(engine.view, FrameLayout.LayoutParams(-1, -1))
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
        root.addView(cursorView, FrameLayout.LayoutParams(-1, -1))
        root.addView(menu, FrameLayout.LayoutParams(-1, -1))

        cursor = CursorController(engine.view, cursorView)
        mapper = GamepadKeyMapper { code, down -> engine.sendKey(code, down) }

        engine.showLauncher()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        engine.onResume()
    }

    override fun onPause() {
        cursor.release()
        mapper.releaseAll()
        engine.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        engine.close()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    @Suppress("DEPRECATION")
    private fun enableImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ engine callbacks

    override fun onLaunchRequested(session: GameSession) {
        this.session = session
        val cursorOn = prefs.getBool("cursor.${session.id}", session.input == "cursor")
        val padOn = prefs.getBool("pad.${session.id}", false)
        mapper.wasd = session.input == "both"
        applyInput(cursorOn, padOn)
        status.showLoading(session.name)
        engine.openGame(session.url)
    }

    override fun onEvent(event: EngineEvent) {
        when (event) {
            is EngineEvent.PageStarted -> if (!event.isGame) leaveGameUi()
            is EngineEvent.Progress -> status.setProgress(event.percent)
            is EngineEvent.PageFinished -> if (event.isGame) status.hideSoon() else status.hideNow()
            is EngineEvent.LoadError -> if (!isLauncherUrl(event.url)) {
                toast("Could not load the game: ${event.description}")
                goHome()
            }
            is EngineEvent.GpuHint ->
                toast("This game reported a WebGPU problem. If it does not start, hold BACK for the menu.")
            EngineEvent.RendererRecovered -> {
                leaveGameUi()
                toast("The game crashed, probably out of memory. Returning Home.")
            }
        }
    }

    // ------------------------------------------------------------------ navigation helpers

    private fun goHome() {
        leaveGameUi()
        engine.showLauncher()
    }

    private fun leaveGameUi() {
        menu.hide()
        status.hideNow()
        engine.setInputLocked(false)
        applyInput(cursorOn = false, padOn = false)
        stickX = 0f; stickY = 0f
        session = null
    }

    private fun applyInput(cursorOn: Boolean, padOn: Boolean) {
        cursor.enable(cursorOn)
        mapper.enabled = padOn
    }

    private fun openMenu() {
        val s = session ?: return
        cursor.release()
        mapper.releaseAll()
        stickX = 0f; stickY = 0f
        engine.setInputLocked(true)
        menu.show(s.name, cursor.enabled, mapper.enabled)
    }

    private fun closeMenu() {
        menu.hide()
        engine.setInputLocked(false)
    }

    private val menuActions = object : GameMenuView.Actions {
        override fun onResume() = closeMenu()

        override fun onHome() = goHome()

        override fun onReload() {
            closeMenu()
            session?.let { status.showLoading(it.name) }
            engine.reload()
        }

        override fun onToggleCursor(): Boolean {
            val on = !cursor.enabled
            cursor.enable(on)
            session?.let { prefs.setBool("cursor.${it.id}", on) }
            return on
        }

        override fun onToggleGamepadKeys(): Boolean {
            val on = !mapper.enabled
            mapper.enabled = on
            session?.let { prefs.setBool("pad.${it.id}", on) }
            return on
        }
    }

    // ------------------------------------------------------------------ input

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN

        if (code == KeyEvent.KEYCODE_BACK) return handleBack(event)

        if (engine.isInGame) {
            if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_BUTTON_START) {
                if (down && event.repeatCount == 0) {
                    if (menu.isShowing) closeMenu() else openMenu()
                }
                return true
            }
            // While the menu is open, normal focus navigation moves between its items.
            if (menu.isShowing) return super.dispatchKeyEvent(event)
            if (cursor.enabled && handleCursorKey(event)) return true
            if (mapper.onKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleBack(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN

        if (menu.isShowing) {
            if (down && event.repeatCount == 0) closeMenu()
            return true
        }
        if (!engine.isInGame) {
            // Let the launcher close a dialog / return to the Home tab first.
            if (!down) engine.askLauncherBack { handled -> if (!handled) exitOnDoubleBack() }
            return true
        }
        // In a game: short press = Home, long press = pause menu.
        if (down) {
            if (event.repeatCount == 0) backPending = true
            else if (event.repeatCount == 1 && backPending) {
                backPending = false
                openMenu()
            }
        } else if (backPending) {
            backPending = false
            goHome()
        }
        return true
    }

    private fun exitOnDoubleBack() {
        val now = SystemClock.uptimeMillis()
        if (now - lastExitBack < 2000) {
            finish()
        } else {
            lastExitBack = now
            Toast.makeText(this, "Press Back again to exit PLAYTV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCursorKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val scale = max(0.6f, root.width / 1920f)
        // Accelerate while the key is held.
        val step = (14f + min(event.repeatCount, 25) * 2.4f) * scale
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (down) cursor.move(0f, -step)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) cursor.move(0f, step)
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) cursor.move(-step, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) cursor.move(step, 0f)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (down) {
                    if (event.repeatCount == 0) cursor.press()
                } else {
                    cursor.release()
                }
            }
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP ->
                if (down) cursor.scroll(1f)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN ->
                if (down) cursor.scroll(-1f)
            else -> return false
        }
        return true
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val joystick = (ev.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (joystick && ev.action == MotionEvent.ACTION_MOVE && engine.isInGame && !menu.isShowing) {
            if (cursor.enabled) {
                stickX = deadzone(ev.getAxisValue(MotionEvent.AXIS_X))
                stickY = deadzone(ev.getAxisValue(MotionEvent.AXIS_Y))
                val rz = deadzone(ev.getAxisValue(MotionEvent.AXIS_RZ))
                val ry = deadzone(ev.getAxisValue(MotionEvent.AXIS_RY))
                val scrollAxis = if (abs(rz) > abs(ry)) rz else ry
                if (scrollAxis != 0f) cursor.scroll(-scrollAxis * 0.5f)
                startStickLoop()
                return true
            }
            if (mapper.onMotion(ev)) return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun deadzone(v: Float): Float = if (abs(v) < 0.2f) 0f else v

    private val stickTick = object : Runnable {
        override fun run() {
            if (!cursor.enabled || menu.isShowing || (stickX == 0f && stickY == 0f)) {
                stickRunning = false
                return
            }
            val scale = max(0.6f, root.width / 1920f)
            cursor.move(stickX * 16f * scale, stickY * 16f * scale)
            handler.postDelayed(this, 16)
        }
    }

    private fun startStickLoop() {
        if (stickRunning) return
        stickRunning = true
        handler.post(stickTick)
    }
}
