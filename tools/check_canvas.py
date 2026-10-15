"""Does the Compose Canvas draw with y down, and does a parse+transform round
trip preserve orientation?

We build a triangle we know the orientation of, put it through the same
parse -> transform -> fill pipeline the app uses, and ask the JVM what came
out. If the apex ends up at the bottom, the flip is in our matrix.
"""
import re
import sys

SRC = r"app/src/main/java/com/pupil/hanzi/stroke/StrokeGeometry.kt"
print("=== current transform code ===")
with open(SRC, encoding="utf-8") as f:
    lines = f.readlines()
for i, line in enumerate(lines, 1):
    if "transformed" in line or "postScale" in line or "postTranslate" in line or "Matrix()" in line:
        print(f"  {i}: {line.rstrip()}")

print("\n=== the data orientation, recomputed ===")
import json
import os

d = json.load(
    open("app/src/main/assets/draw_all/shard-s5.json", encoding="utf-8")
)
sh = d["好"]
nums = [float(n) for n in re.findall(r"-?\d+(?:\.\d+)?", sh["strokes"][0])]
ys = nums[1::2]
xs = nums[0::2]
print(f"  好 stroke0: y {min(ys):.0f}..{max(ys):.0f}  x {min(xs):.0f}..{max(xs):.0f}")
print("  女字旁 should occupy the UPPER-LEFT of the character.")
print(f"  -> in this data y is small where the character is high, so y is DOWN (screen coords).")
print("  -> no flip is needed; a correct renderer draws it as-is.")
