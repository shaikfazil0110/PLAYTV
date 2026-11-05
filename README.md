# PLAYTV v3

A TV launcher for browser games, built for Fire TV / Android TV with a remote or gamepad.

```
PLAYTV-v3/
├── shared/assets/              single source of truth for BOTH apps
│   ├── games.json              catalogue (id, kind, requires, input ...)
│   └── launcher/               TV launcher UI (index.html, style.css, app.js)
├── app/                        "Legacy" build: system WebView (Fire OS 7 / API 28+)  <- test this on the Fire TV Stick
│   └── src/main/java/...       Kotlin, ~900 lines, no third-party deps except androidx.webkit
├── app-chromium/               experimental build with bundled Chromium (Cefrium), native launcher
├── tools/                      catalogue validator / generator
└── .github/workflows/          CI: validate, build Legacy APK, build Chromium APK (non-blocking)
```

## Build
Push to GitHub and run the **Build PLAYTV** workflow, or locally (JDK 17, Android SDK 34, Gradle 8.8):

    gradle :app:assembleDebug          ->  app/build/outputs/apk/debug/app-debug.apk
    adb install -r app/build/outputs/apk/debug/app-debug.apk

Debug builds enable WebView remote debugging: open `chrome://inspect` on a PC to see a game's console/network errors.

## Controls
| Where | Input | Action |
|---|---|---|
| Launcher | D-pad / OK | move / open |
| Launcher | BACK | close dialog -> Home tab -> (press again within 2 s) exit |
| In game | BACK | return Home |
| In game | hold BACK, or MENU / START | pause menu (resume, home, reload, cursor mode, gamepad-as-keys) |
| Cursor mode | D-pad / left stick | move mouse pointer (accelerates when held; pushing at the top/bottom edge scrolls) |
| Cursor mode | OK / A | left click (hold = drag) |
| Cursor mode | rewind / fast-forward, right stick | scroll |
| Gamepad-as-keys | A / B / X / Y | Space / Esc / Enter / Tab |
| Gamepad-as-keys | left stick | arrows (or WASD for mouse+keyboard games) |

Cursor mode starts ON for pointer-driven games and every site in "Browse"; the choice is remembered per game.

## Catalogue (`shared/assets/games.json`)
Each entry: `id, name, genre, url (https), icon, note, platform, art (optional image URL), kind, requires, input`
- `kind`: `game` or `portal` (a site with many games; shown under Browse)
- `requires`: `none | webgl | webgpu` - the launcher compares this with a live capability probe and shows **Ready / Needs WebGL / Needs WebGPU**. Games that cannot run here are dimmed and ask for confirmation before launching.
- `input`: `cursor | keys | both` - decides the default control mode.

Validate after editing: `python3 tools/validate_catalog.py shared/assets/games.json`

## What changed versus v2 (root causes fixed)
1. Games were kicked back to the launcher whenever they logged a WebGPU error. Now it is a toast only.
2. `fetch('../games.json')` from `file://` is blocked by Chromium, so the old launcher fell back to 3 hard-coded games. The launcher is now served from `https://appassets.androidplatform.net` (WebViewAssetLoader) and the native bridge also offers the catalogue.
3. Games got the stock WebView user agent (`; wv`, `Mobile`) and were refused. They now get a normal Chrome UA.
4. Third-party cookies and mixed content were blocked (broke iframe portals).
5. Favorites / Library never worked. Rebuilt with persistent favorites and "continue playing".
6. Back stack bug (Back on Home re-opened the last game), blank page on load errors, dead renderer reuse, always-exposed JS bridge: all fixed.

## Honest limits
- True WebGPU games cannot run on the Fire OS 7 system WebView. They are labelled instead of failing silently.
- This source was compiled here against Android API 28 stubs (no errors) and the launcher was run in headless Chromium, but it has **not** been run on a real Fire TV. Cursor-mode mouse injection and gamepad mapping in particular need on-device testing.
- `app-chromium` keeps its own native launcher and only shares the catalogue; its WebGPU behaviour depends on the Cefrium SDK and is untested.
