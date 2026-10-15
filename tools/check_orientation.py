"""Are the stroke paths in screen coordinates (y down) or math (y up)?

Sample characters whose first and last strokes are on known sides:
  一  both strokes near the vertical middle
  上  first stroke is a short mark near the TOP, last is the long base
  下  first stroke is the long top bar, last descends
  人  two strokes meeting at the top
  好  女 first (upper-left), 子 last (lower)
Print the y-range of the first and last stroke so the orientation is clear.
"""
import json
import os
import re

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "draw_all",
)

shard = None
for name in os.listdir(ASSETS):
    with open(os.path.join(ASSETS, name), encoding="utf-8") as f:
        d = json.load(f)
    if "好" in d:
        shard = d
        break


def numbers(path):
    return [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", path)]


def yrange(p):
    nums = numbers(p)
    ys = nums[1::2]          # x y x y ...
    return (min(ys), max(ys), sum(ys) / len(ys))


def xrange(p):
    nums = numbers(p)
    xs = nums[0::2]
    return (min(xs), max(xs), sum(xs) / len(xs))


for ch in ["一", "上", "下", "人", "好", "妈", "字"]:
    if ch not in shard:
        continue
    d = shard[ch]
    print(f"\n=== {ch}  ({len(d['strokes'])} strokes) ===")
    for i, s in enumerate(d["strokes"]):
        y0, y1, ym = yrange(s)
        x0, x1, xm = xrange(s)
        tag = " <-- RAD" if i in (d.get("radStrokes") or []) else ""
        print(f"  stroke {i}: y {y0:7.1f}..{y1:7.1f} (mean {ym:7.1f})  "
              f"x {x0:7.1f}..{x1:7.1f}{tag}")
