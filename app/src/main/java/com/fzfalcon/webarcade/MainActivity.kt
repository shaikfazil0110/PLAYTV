package com.fzfalcon.webarcade

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class MainActivity : Activity() {
    private lateinit var rootContainer: FrameLayout
    private var browserEngine: PlayBrowserEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableImmersiveMode()

        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#090B0E"))
        }
        setContentView(rootContainer)

        setupLauncher()
    }

    private fun setupLauncher() {
        val engine = BrowserEngineFactory.create(this) { event ->
            runOnUiThread {
                handleEngineEvent(event)
            }
        }
        browserEngine = engine
        rootContainer.addView(engine.view, FrameLayout.LayoutParams(-1, -1))
        engine.loadUrl("file:///android_asset/steam_launcher/index.html")
    }

    private fun handleEngineEvent(event: LegacyWebViewEngine.EngineEvent) {
        when (event) {
            is LegacyWebViewEngine.EngineEvent.WebGpuCompatibilityWarning -> {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("WebGPU Hardware Boundary")
                    .setMessage(
                        "This game requires WebGPU / Vulkan 1.1 compute pipelines.\n\n" +
                        "Reason: ${event.message}\n\n" +
                        "Hardware note: MediaTek / PowerVR GPUs on Fire OS 7 (Android 9) do not expose Vulkan 1.1 in Chromium. Returning to the Steam Launcher."
                    )
                    .setPositiveButton("Return to Launcher") { d, _ ->
                        d.dismiss()
                        browserEngine?.loadUrl("file:///android_asset/steam_launcher/index.html")
                    }
                    .setCancelable(false)
                    .show()
            }
            is LegacyWebViewEngine.EngineEvent.Error -> {
                browserEngine?.loadUrl("file:///android_asset/steam_launcher/index.html")
            }
            else -> {}
        }
    }

    private fun enableImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (browserEngine?.canGoBack() == true) {
                browserEngine?.goBack()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        browserEngine?.onResume()
    }

    override fun onPause() {
        super.onPause()
        browserEngine?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        browserEngine?.close()
    }
}
