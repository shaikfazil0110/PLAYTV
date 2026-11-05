#!/usr/bin/env python3
import json, sys
path = sys.argv[1] if len(sys.argv) > 1 else "shared/assets/games.json"
games = json.load(open(path, encoding="utf-8"))
assert games, "catalog is empty"
ids, urls = set(), set()
for g in games:
    for k in ("id", "name", "genre", "url", "icon", "note", "platform", "kind", "requires", "input"):
        assert k in g, f"{g.get('name')}: missing {k}"
    assert g["url"].startswith("https://"), f"{g['name']}: url must be https"
    assert g["kind"] in ("game", "portal"), g["name"]
    assert g["requires"] in ("none", "webgl", "webgpu"), g["name"]
    assert g["input"] in ("cursor", "keys", "both"), g["name"]
    assert g["id"] not in ids, f"duplicate id {g['id']}"
    assert g["url"] not in urls, f"duplicate url {g['url']}"
    ids.add(g["id"]); urls.add(g["url"])
print(f"{path}: {len(games)} entries OK")
