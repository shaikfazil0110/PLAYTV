# PLAYTV — embedded browser test build

This project now builds **two separate APKs**:

1. **PLAYTV-Bundled-Chromium-Android10Plus-debug.apk** — uses Cefrium 0.16.0, an embedded Chromium engine. Requires Android 10 / API 29+, and a 64-bit ARM or x86-64 device. Designed for the Android TV emulator or newer Android TV devices.
2. **PLAYTV-Legacy-WebView-Android9Plus-debug.apk** — supports older Android releases including Android 9 / API 28. It stays inside PLAYTV but uses the system Android WebView, so it does not gain the bundled engine's compatibility advantages.

## Important limitations

- The bundled engine is large; Android TV/emulator downloads can exceed 90 MB for one ABI. Cefrium is pre-1.0/beta. The build and real-device behavior still need verification.
- Desktop User-Agent mode may bypass sites that only check the browser string. It cannot add missing GPU capabilities, unsupported APIs, desktop DRM/codecs, or make every desktop-only website run on Android.
- Controller events are received by the embedded browser/view, but each website/game must support Gamepad API or keyboard/controller mappings. No universal controller mapping is claimed.
- Game art tiles use each site's favicon as a first pass; the UI no longer relies on emojis. Some favicon requests may be blocked/offline.
- No game binaries are redistributed. PLAYTV links to the existing websites.

## Build on GitHub Actions

1. Extract this ZIP.
2. Upload the **contents** of the `PLAYTV` folder to the root of your GitHub repository, including the hidden `.github` folder.
3. Open **Actions → Build PLAYTV (Chromium + Legacy)** and run the workflow.
4. Download the `PLAYTV-APKs` artifact ZIP and extract it. It contains both APKs.

The workflow uses JDK 25, Gradle 9.7.1, Android Gradle Plugin 9.4.0 and compile SDK 37 because those are required by current Cefrium 0.16.0.

## Recommended test order

1. Install `PLAYTV-Bundled-Chromium-Android10Plus-debug.apk` in an Android TV x86-64 emulator running Android 10+.
2. Test a light game, then NFSU2.xyz. Try the Desktop toggle, fullscreen, Back, audio, and controller.
3. For a Fire TV Stick on Fire OS 7 / Android 9, install the legacy APK. Its engine limitations remain.
