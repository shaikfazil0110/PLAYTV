#!/usr/bin/env python3
"""Regenerates shared/assets/games.json from the legacy catalog plus classification.
Kept as documentation of how each entry was classified. Edit games.json directly afterwards if you prefer."""
import json, re, sys
src, dst = sys.argv[1], sys.argv[2]
games = json.load(open(src, encoding="utf-8"))

# requires: none | webgl | webgpu   (minimum browser graphics feature)
# input:    cursor (pointer driven) | keys (keyboard/gamepad) | both
META = {
 "Need for Speed: Underground 2": ("webgpu", "keys"),
 "NFS Most Wanted (2005) Web Port": ("webgl", "keys"),
 "Quake III Arena — Q3JS": ("webgl", "both"),
 "Krunker.io": ("webgl", "both"),
 "Shell Shockers": ("webgl", "both"),
 "Minecraft Classic": ("webgl", "both"),
 "Chrono Divide: Red Alert 2": ("webgl", "cursor"),
 "Agar.io": ("none", "cursor"),
 "Slither.io": ("none", "cursor"),
 "TETR.IO": ("webgl", "keys"),
 "2048": ("none", "keys"),
 "Little Alchemy 2": ("none", "cursor"),
 "Lichess": ("none", "cursor"),
 "Chess.com Play": ("none", "cursor"),
 "Gartic Phone": ("none", "cursor"),
 "skribbl.io": ("none", "cursor"),
 "GeoGuessr": ("webgl", "cursor"),
 "Wordle": ("none", "both"),
 "Pokémon Showdown": ("none", "cursor"),
 "OpenLara — Tomb Raider Engine": ("webgl", "keys"),
 "QuakeJS": ("webgl", "both"),
 "Board Game Arena": ("none", "cursor"),
 "Chess.org — Browser Chess": ("none", "cursor"),
 "Word Games — Sporcle": ("none", "cursor"),
}
PORTAL_PLATFORMS = {"HTML5 GAME HUBS", "RETRO / DOS HUBS", "RETRO / DOS"}
PORTAL_NAMES = {"Neal.fun — Interactive Games"}

def slug(s):
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")

out, seen = [], set()
for g in games:
    n = g["name"]
    portal = g["platform"] in PORTAL_PLATFORMS or n in PORTAL_NAMES
    req, inp = META.get(n, ("none", "cursor"))
    if portal and "3D" in n:
        req = "webgl"
    e = dict(g)
    e["id"] = slug(n)
    assert e["id"] not in seen, e["id"]; seen.add(e["id"])
    e["kind"] = "portal" if portal else "game"
    e["requires"] = req
    e["input"] = inp
    out.append(e)
json.dump(out, open(dst, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(len(out), "entries;", sum(e["kind"] == "game" for e in out), "games,", sum(e["kind"] == "portal" for e in out), "portals")
