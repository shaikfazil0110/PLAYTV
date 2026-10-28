# PLAYTV — Android TV game launcher

PLAYTV is a neon-styled Android TV / Fire TV browser-game catalogue. It groups a curated collection of browser-playable games, web ports, multiplayer titles and game portals by category. It is not a claim to contain every game on the internet.

## Build the APK with GitHub Actions
1. Create a GitHub repository named `PLAYTV`.
2. Extract this ZIP and upload the **contents** of the project folder to the repository root, including `.github/workflows/build-apk.yml`.
3. Open **Actions** and run **Build PLAYTV Android TV APK**.
4. Download the `PLAYTV-AndroidTV-debug-APK` artifact and extract the APK from its ZIP.
5. Sideload the debug APK to your Fire TV Stick.

The launcher icon is the supplied PLAYTV neon artwork in Android mipmap densities. Catalogue cards use emoji glyphs for fast, offline-loading category/game artwork; websites may load their own logos after opening. The app is a launcher and WebView, not an emulator. Some websites may block embedded playback, require sign-in, or not support a TV remote.
