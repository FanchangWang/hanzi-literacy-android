"""Verify the four rendering/animation questions against the real data.

1. Vertical centring: what is the actual y-extent of a character? If glyphs
   sit low, the data may be biased or my inset is wrong.
2. Left/right order: for 好, the radical strokes must come before the rest.
3. Stroke timing: stroke lengths differ hugely, so a fixed per-stroke time
   makes 一 and 懂 animate at wildly different speeds. Measure the spread.
4. Fill direction: the outline is a closed contour, so a growing segment is
   what makes a stroke look filled in, not a wipe.
"""
import json
import os
import re
import math

ASSETS = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets", "draw_all",
)

shards = {}
for name in os.listdir(ASSETS):
    with open(os.path.join(ASSETS, name), encoding="utf-8") as f:
        shards.update(json.load(f))


def coords(path):
    nums = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", path)]
    return list(zip(nums[0::2], nums[1::2]))


print("=== 1. vertical extent (is the glyph centred in 0..1024?) ===")
ys_all = []
for ch in list(shards)[:400]:
    for s in shards[ch]["strokes"]:
        for x, y in coords(s):
            ys_all.append(y)
print(f"  sampled {len(ys_all)} points")
print(f"  y min {min(ys_all):.1f}  max {max(ys_all):.1f}  "
      f"mean {sum(ys_all) / len(ys_all):.1f}")
print(f"  box centre is 512; data mean {sum(ys_all) / len(ys_all):.1f}")
print("  -> if the mean is well below 512 the glyphs sit high in the box")

print("\n=== per-character centre of mass ===")
for ch in "一好懂字长重下上":
    if ch not in shards:
        continue
    pts = [p for s in shards[ch]["strokes"] for p in coords(s)]
    ys = [p[1] for p in pts]
    xs = [p[0] for p in pts]
    print(f"  {ch}: y {min(ys):6.1f}..{max(ys):6.1f} mean {sum(ys)/len(ys):6.1f}   "
          f"x {min(xs):6.1f}..{max(xs):6.1f} mean {sum(xs)/len(xs):6.1f}")

print("\n=== 2. stroke order vs radical for 好 (left radical must come first) ===")
d = shards["好"]
for i, s in enumerate(d["strokes"]):
    pts = coords(s)
    xm = sum(p[0] for p in pts) / len(pts)
    tag = "RAD" if i in (d.get("radStrokes") or []) else "   "
    print(f"  stroke {i} {tag} mean x {xm:6.1f}  (left = radical)")

print("\n=== 3. stroke length spread (why one fixed time is wrong) ===")
for ch in "一懂好字长重下上水火":
    if ch not in shards:
        continue
    lens = []
    for s in shards[ch]["strokes"]:
        pts = coords(s)
        total = 0.0
        for a, b in zip(pts, pts[1:]):
            total += math.hypot(b[0] - a[0], b[1] - a[1])
        lens.append(total)
    if lens:
        print(f"  {ch}: {len(lens):2d} strokes  min {min(lens):6.1f}  max {max(lens):6.1f}  "
              f"ratio {max(lens) / max(min(lens), 1):5.1f}x")

print("\n=== 4. contour count per stroke (multi-contour -> segment fill breaks) ===")
multi = 0
total = 0
worst = []
for ch in list(shards)[:2000]:
    for i, s in enumerate(shards[ch]["strokes"]):
        n = len(re.findall(r"[Mm]", s))
        total += 1
        if n > 1:
            multi += 1
            worst.append((n, ch, i))
print(f"  strokes with >1 contour: {multi}/{total} ({100 * multi / total:.1f}%)")
worst.sort(reverse=True)
print(f"  most contours: {worst[:6]}")

print("\n=== 5. stroke path direction: does the path start at the pen origin? ===")
for ch in "一竖下上":
    if ch not in shards:
        continue
    d2 = shards[ch]
    for i, s in enumerate(d2["strokes"][:3]):
        pts = coords(s)
        if pts:
            print(f"  {ch} stroke {i}: first {pts[0]}  last {pts[-1]}")
