"""Definitively settle the y direction using 上 and 下.

上 (shang): a short vertical mark sits ABOVE a horizontal base, plus a bar
            crossing the vertical partway down.
  - if y is DOWN, the short top mark has the SMALLEST y.
  - if y is UP,   the short top mark has the LARGEST y.

下 (xia):   a long horizontal bar sits at the TOP with a vertical stem and a
            dot to the side.
  - if y is DOWN, the top bar has the SMALLEST y.
  - if y is UP,   the top bar has the LARGEST y.
"""
import json
import os
import re

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "draw_all",
)

shards = {}
for name in os.listdir(ASSETS):
    with open(os.path.join(ASSETS, name), encoding="utf-8") as f:
        shards.update(json.load(f))


def coords(s):
    n = [float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", s)]
    return list(zip(n[0::2], n[1::2]))


def report(ch):
    if ch not in shards:
        print(f"  {ch}: not found")
        return
    print(f"\n  {ch}:")
    for i, s in enumerate(shards[ch]["strokes"]):
        pts = coords(s)
        ys = [p[1] for p in pts]
        xs = [p[0] for p in pts]
        w = max(xs) - min(xs)
        h = max(ys) - min(ys)
        shape = "horizontal" if w > h * 2 else ("vertical" if h > w * 2 else "diagonal/other")
        print(f"    stroke {i}: y {min(ys):6.1f}..{max(ys):6.1f} mean {sum(ys)/len(ys):6.1f}  "
              f"x {min(xs):6.1f}..{max(xs):6.1f}  [{shape}]")


print("=== 上 shang ===")
report("上")
print("\n=== 下 xia ===")
report("下")
print("\n=== 二 er (two horizontal bars, the second is longer) ===")
report("二")
print("\n=== 三 san (three bars, the last is longest) ===")
report("三")

print("\n\n=== VERDICT INPUTS ===")
print("For 上, the SHORT stroke is the top mark; the long strokes are below it.")
print("For 三, bar 0 is shortest and bar 2 is longest -- and they are stacked.")
print("Compare which end of the y range the short/top element occupies.")
