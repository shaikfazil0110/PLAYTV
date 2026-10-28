#!/usr/bin/env python3
"""PLAYTV source patch helper. Run from repository root: python apply_playtv_patch.py"""
from pathlib import Path
import json, sys

ROOT = Path.cwd()
PATCH_DIR = Path(__file__).resolve().parent
candidates = [
    PATCH_DIR / "games.json",
    PATCH_DIR / "app-chromium" / "src" / "main" / "assets" / "games.json",
    PATCH_DIR / "app" / "src" / "main" / "assets" / "games.json",
]
catalog_path = next((p for p in candidates if p.exists()), None)
if catalog_path is None:
    sys.exit("ERROR: games.json not found beside this script or in the bundle paths.")
games = json.loads(catalog_path.read_text(encoding="utf-8"))
if not isinstance(games, list) or not games or not all("name" in x and "url" in x and "platform" in x for x in games):
    sys.exit("ERROR: Catalog validation failed; no source files changed.")

def replace_once(s, old, new, label):
    if new in s:
        return s
    count = s.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one source anchor, found {count}; file may have changed.")
    return s.replace(old, new, 1)

def patch_kotlin(s):
    s = replace_once(s,
        'data class Game(val name: String, val genre: String, val url: String, val note: String) {',
        'data class Game(val name: String, val genre: String, val url: String, val note: String, val platform: String = "BROWSER", val art: String = "") {',
        "Game model")
    s = replace_once(s,
        'Game(o.getString("name"), o.getString("genre"), o.getString("url"), o.optString("note", "Browser game"))',
        'Game(o.getString("name"), o.getString("genre"), o.getString("url"), o.optString("note", "Browser game"), o.optString("platform", "BROWSER"), o.optString("art", ""))',
        "Catalog parser")
    s = replace_once(s,
        'if (query.isNotBlank()) games = games.filter { it.name.contains(query, true) || it.genre.contains(query, true) || it.host.contains(query, true) }',
        'if (query.isNotBlank()) games = games.filter { it.name.contains(query, true) || it.genre.contains(query, true) || it.platform.contains(query, true) || it.host.contains(query, true) }',
        "Library search")
    s = replace_once(s,
        'listOf("Racing", "Shooter", "Arcade", "Puzzle", "Strategy", "Sandbox", "Adventure", "Chess & Board", "Party & Multiplayer", "Game Hubs").forEach { genre ->\n                val items = all.filter { it.genre.equals(genre, true) }\n                if (items.isNotEmpty()) addShelf(main, genre.uppercase(), items)\n            }',
        'all.map { it.platform }.distinct().forEach { platform ->\n                val items = all.filter { it.platform.equals(platform, true) }\n                if (items.isNotEmpty()) addShelf(main, platform, items)\n            }',
        "Platform shelves")
    s = replace_once(s,
        'val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(dp(24),dp(12),dp(24),dp(12)) }',
        'val logo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setPadding(0,0,0,0) }',
        "Artwork scaling")
    s = replace_once(s,
        'nameOverlay.addView(text(game.genre.uppercase(), 10f, cyan, true).apply { setPadding(0,dp(4),0,0) })',
        'nameOverlay.addView(text("${game.genre.uppercase()}  •  ${game.platform}", 10f, cyan, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0,dp(4),0,0) })',
        "Card platform label")
    s = replace_once(s,
        'val results = readGames().filter { it.name.contains(q, true) || it.genre.contains(q, true) || it.host.contains(q, true) }',
        'val results = readGames().filter { it.name.contains(q, true) || it.genre.contains(q, true) || it.platform.contains(q, true) || it.host.contains(q, true) }',
        "Search results")

    start = s.find("    private fun loadSiteIcon(game: Game, target: ImageView) {")
    end = s.find("\n    private fun isFavorite(game: Game)", start)
    if start < 0 or end < 0:
        raise RuntimeError("Artwork loader anchor not found; Kotlin file differs from expected source.")
    if "og:image" not in s[start:end]:
        new_loader = r"""    private fun loadSiteIcon(game: Game, target: ImageView) {
        // Prefer explicit art, then Open Graph/Twitter share-card artwork.
        // Some sites publish no public artwork, so a favicon is the final fallback.
        val cacheKey = game.art.ifBlank { game.url }
        val cached = imageCache[cacheKey]
        if (cached != null) { target.setImageBitmap(cached); return }

        thread(name = "playtv-artwork") {
            var bitmap: android.graphics.Bitmap? = null
            try {
                var imageUrl = game.art.trim()
                if (imageUrl.isBlank()) {
                    val pageConnection = URL(game.url).openConnection() as HttpURLConnection
                    pageConnection.connectTimeout = 7000
                    pageConnection.readTimeout = 7000
                    pageConnection.instanceFollowRedirects = true
                    pageConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; PLAYTV) AppleWebKit/537.36 Chrome/156.0.0.0 Safari/537.36")
                    pageConnection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                    val html = try {
                        pageConnection.inputStream.bufferedReader().use { it.readText().take(1500000) }
                    } finally {
                        pageConnection.disconnect()
                    }
                    fun metaAttr(tag: String, key: String): String? =
                        Regex("(?is)\\b" + key + "\\s*=\\s*[\"']([^\"']+)[\"']").find(tag)?.groupValues?.get(1)
                    val tags = Regex("(?is)<meta\\b[^>]*>").findAll(html).map { it.value }
                    for (tag in tags) {
                        val field = (metaAttr(tag, "property") ?: metaAttr(tag, "name") ?: "").lowercase()
                        if (field == "og:image" || field == "twitter:image" || field == "twitter:image:src") {
                            val candidate = metaAttr(tag, "content")?.replace("&amp;", "&")?.replace("&quot;", "\"")?.trim().orEmpty()
                            if (candidate.isNotBlank()) {
                                imageUrl = try { URL(URL(game.url), candidate).toString() } catch (_: Exception) { candidate }
                                break
                            }
                        }
                    }
                }
                if (imageUrl.isBlank()) {
                    imageUrl = "https://www.google.com/s2/favicons?domain_url=${Uri.encode("https://${game.host}")}&sz=256"
                }
                val imageConnection = URL(imageUrl).openConnection() as HttpURLConnection
                imageConnection.connectTimeout = 7000
                imageConnection.readTimeout = 9000
                imageConnection.instanceFollowRedirects = true
                imageConnection.setRequestProperty("User-Agent", "PLAYTV/1.2")
                try {
                    imageConnection.inputStream.use { bitmap = BitmapFactory.decodeStream(it) }
                } finally {
                    imageConnection.disconnect()
                }
            } catch (_: Exception) { }
            if (bitmap != null) {
                imageCache[cacheKey] = bitmap!!
                runOnUiThread { if (!isFinishing && target.isAttachedToWindow) target.setImageBitmap(bitmap) }
            } else {
                runOnUiThread {
                    if (!isFinishing && target.isAttachedToWindow && target.drawable == null) {
                        target.setImageDrawable(null)
                        target.setBackground(GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(25,41,68), Color.rgb(70,27,81))))
                    }
                }
            }
        }
    }
"""
        s = s[:start] + new_loader + s[end:]
    return s

# Patch all existing Kotlin sources in memory first so partial failure does not half-update them.
targets = [
    ROOT / "app/src/main/java/com/fzfalcon/webarcade/MainActivity.kt",
    ROOT / "app-chromium/src/main/java/com/fzfalcon/webarcade/MainActivity.kt",
]
present = [p for p in targets if p.exists()]
if not present:
    sys.exit("ERROR: Run from PLAYTV repository root, where app/ and app-chromium/ exist.")
patched = {}
try:
    for p in present:
        patched[p] = patch_kotlin(p.read_text(encoding="utf-8"))
except Exception as exc:
    sys.exit(f"ERROR: {exc}\nNo Kotlin files or catalogs were changed.")
for p, content in patched.items():
    p.write_text(content, encoding="utf-8")
    print("UPDATED", p.relative_to(ROOT))
for rel in ["app/src/main/assets/games.json", "app-chromium/src/main/assets/games.json"]:
    p = ROOT / rel
    if p.parent.exists():
        p.write_text(json.dumps(games, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("UPDATED", p.relative_to(ROOT))
print(f"Catalog entries: {len(games)}")
print("Review `git diff`, build/test both APK variants, and commit.")
