package com.fzfalcon.webarcade

import android.view.View

/**
 * The launcher is served from a virtual https origin by WebViewAssetLoader (see WebViewEngine),
 * which makes fetch(), localStorage and relative URLs behave like a normal website.
 */
const val ASSET_HOST = "appassets.androidplatform.net"
const val LAUNCHER_URL = "https://$ASSET_HOST/assets/launcher/index.html"

fun isLauncherUrl(url: String): Boolean = url.startsWith("https://$ASSET_HOST/")

/** One game the user chose in the launcher. */
data class GameSession(
    val id: String,
    val name: String,
    val url: String,
    /** "cursor" = pointer driven, "keys" = keyboard/gamepad, "both" = mouse + keys (FPS style) */
    val input: String,
    /** "none" | "webgl" | "webgpu" */
    val requires: String
)

sealed class EngineEvent {
    data class PageStarted(val url: String, val isGame: Boolean) : EngineEvent()
    data class PageFinished(val url: String, val isGame: Boolean) : EngineEvent()
    data class Progress(val percent: Int) : EngineEvent()
    data class LoadError(val description: String, val url: String) : EngineEvent()
    /** A game logged a WebGPU problem. Informational only: the game keeps running. */
    data class GpuHint(val message: String) : EngineEvent()
    /** The renderer crashed or was killed (usually out of memory). The engine already rebuilt itself. */
    object RendererRecovered : EngineEvent()
}

interface EngineListener {
    /** Always called on the UI thread. */
    fun onEvent(event: EngineEvent)

    /** The launcher asked to start a game. Always called on the UI thread. */
    fun onLaunchRequested(session: GameSession)
}

/** Abstraction over the browser implementation so the rest of the app does not care which one runs. */
interface PlayBrowserEngine {
    val view: View
    val isInGame: Boolean

    fun showLauncher()
    fun openGame(url: String)
    fun reload()

    /** Lets the launcher page handle BACK first (close a dialog, return to the Home tab). */
    fun askLauncherBack(callback: (handled: Boolean) -> Unit)

    /** Injects a key event into the page. */
    fun sendKey(keyCode: Int, down: Boolean)

    /** While locked the page cannot take focus (used while the native menu is open). */
    fun setInputLocked(locked: Boolean)

    fun clearWebData()
    fun exitFullscreen()
    fun onPause()
    fun onResume()
    fun close()
}
