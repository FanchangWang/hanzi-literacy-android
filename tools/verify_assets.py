"""Verify the merged shards are byte-equivalent in content to the sources.

Compares every entry in every shard against its original per-char file.
Also measures parse cost, since that drives the lazy-load decision (§8 risk 1).
"""
import json
import os
import random
import time

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets",
)
SRC = r"C:\Users\guyue\code\pupil-learning-tool\cnchar-data\cnchar-data"
SHARD_BITS = 12


def load_shards(sub):
    out = {}
    d = os.path.join(ASSETS, sub)
    for name in sorted(os.listdir(d)):
        with open(os.path.join(d, name), encoding="utf-8") as f:
            for ch, payload in json.load(f).items():
                out[ch] = payload
    return out


def verify(sub, src_sub):
    print(f"=== {sub} ===")
    merged = load_shards(sub)
    src_dir = os.path.join(SRC, src_sub)
    names = [
        n for n in os.listdir(src_dir)
        if n.endswith(".json") and n != "package.json"
    ]
    print(f"  source files: {len(names)}   merged entries: {len(merged)}")
    assert len(names) == len(merged), "entry count mismatch!"

    mismatch = 0
    missing = []
    for n in names:
        ch = n[:-5]
        with open(os.path.join(src_dir, n), encoding="utf-8") as f:
            original = json.load(f)
        if merged.get(ch) != original:
            mismatch += 1
            if len(missing) < 5:
                missing.append(ch)
    print(f"  content mismatches: {mismatch}")
    if missing:
        print(f"    e.g. {missing}")

    # shard routing check
    bad_route = [ch for ch in merged
                 if f"s{ord(ch) >> 9:x}"
                 not in {n.split("-")[1].split(".")[0]
                         for n in os.listdir(os.path.join(ASSETS, sub))}]
    print(f"  routing errors: {len(bad_route)}")
    return mismatch == 0


def timing():
    print("\n=== parse cost (cold, per shard) ===")
    for sub in ["draw_all", "explanation_all"]:
        d = os.path.join(ASSETS, sub)
        sizes = sorted(os.listdir(d))
        for name in sizes:
            p = os.path.join(d, name)
            mb = os.path.getsize(p) / 1024 / 1024
            t0 = time.perf_counter()
            with open(p, encoding="utf-8") as f:
                json.load(f)
            dt = (time.perf_counter() - t0) * 1000
            print(f"  {sub}/{name}: {mb:5.2f} MB -> {dt:6.1f} ms")

    # whole-draw cold parse, the worst case if we eagerly load everything
    t0 = time.perf_counter()
    total = 0
    for name in sorted(os.listdir(os.path.join(ASSETS, "draw_all"))):
        with open(os.path.join(ASSETS, "draw_all", name), encoding="utf-8") as f:
            total += len(json.load(f))
    print(f"\n  ALL draw shards: {(time.perf_counter() - t0) * 1000:.0f} ms for {total} entries")


def spot_check():
    print("\n=== spot check known chars ===")
    draw = load_shards("draw_all")
    expl = load_shards("explanation_all")
    for ch in ["好", "人", "一", "长", "重", "水", "火", "龘"]:
        d = draw.get(ch)
        e = expl.get(ch)
        ds = f"{len(d['strokes'])} strokes, rad={d.get('radStrokes')}" if d else "MISSING"
        es = f"{len(e)} words" if e else "MISSING"
        print(f"  {ch}: draw={ds:<28} explanation={es}")

    d = draw["好"]
    print(f"\n  好 strokes[0] head: {d['strokes'][0][:50]}")
    print(f"  好 medians[0] head: {d['medians'][0][:2]}")
    print(f"  好解释 sample: {expl['好']['好艾']!r}")


if __name__ == "__main__":
    ok = verify("draw_all", "draw")
    ok &= verify("explanation_all", "explanation")
    print(f"\nALL CONTENT MATCHES: {ok}")
    spot_check()
    timing()