"""How many medians contain fractional coordinates?

The app's StrokeData declared medians as Int, which crashes the decoder on
the first fractional value. Find every affected character, and whether
rounding to int is faithful enough.
"""
import json
import os
from collections import defaultdict

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "draw_all",
)

frac_chars = {}          # char -> list of (stroke, point, [x, y])
rounding_deltas = []     # (char, frac, rounded) for the distance analysis
non_int_xy = set()       # points whose length != 2
max_frac = 0.0

for name in sorted(os.listdir(ASSETS)):
    path = os.path.join(ASSETS, name)
    with open(path, encoding="utf-8") as f:
        shard = json.load(f)

    for ch, data in shard.items():
        bad = []
        for si, stroke in enumerate(data.get("medians", [])):
            for pi, pt in enumerate(stroke):
                if len(pt) != 2:
                    non_int_xy.add((ch, si, pi, len(pt)))
                    continue
                for v in pt:
                    if isinstance(v, float) and v != int(v):
                        bad.append((si, pi, pt))
                        rounding_deltas.append(abs(v - round(v)))
                        max_frac = max(max_frac, abs(v - int(v)))
        if bad:
            frac_chars[ch] = bad

print(f"characters with fractional median coords: {len(frac_chars)} / 9574")
print(f"max fractional part seen: {max_frac}")
print(f"points whose coordinate list is not length 2: {len(non_int_xy)}")

by_shard = defaultdict(int)
for ch in frac_chars:
    by_shard[ch.encode("utf-8").hex()[-2:]] += 1
print(f"affected spread across shards: {len(by_shard)}")

print("\nsample affected characters:")
for ch in list(frac_chars)[:12]:
    ex = frac_chars[ch][0]
    print(f"  {ch}: {len(frac_chars[ch])} fractional points, e.g. stroke {ex[0]} point {ex[1]} = {ex[2]}")

# Rounding error analysis: is dropping the fraction visually harmless?
if rounding_deltas:
    worst = max(rounding_deltas)
    mean = sum(rounding_deltas) / len(rounding_deltas)
    print(f"\nrounding a fractional coordinate shifts it by at most {worst:.4f} px")
    print(f"  (mean {mean:.4f}, {len(rounding_deltas)} fractional values total)")
    print(f"  source grid is 1024 units, so the worst shift is {worst / 1024 * 100:.4f}% of the box")

# Also confirm strokes[] themselves are always strings and radStrokes always ints.
with open(os.path.join(ASSETS, "shard-s5.json"), encoding="utf-8") as f:
    one = json.load(f)
ch = next(iter(one))
d = one[ch]
print(f"\nsample '{ch}': strokes are {type(d['strokes'][0]).__name__}, "
      f"radStrokes are {type(d.get('radStrokes', [None])[0]).__name__ if d.get('radStrokes') else 'absent'}")