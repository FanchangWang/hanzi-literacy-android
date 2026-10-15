"""Build-time asset merger.

cnchar ships ~18k tiny JSON files (draw/ 9574, explanation/ 8616). Putting
those in assets/ directly bloats the APK and makes first-launch parsing slow.

This merges them into sharded files, keeping voice/ as individual mp3s
(audio is streamed, not concatenated).

draw_all/        shard-<bucket>.json   8 shards, ~6.7MB largest
explanation_all/ shard-<bucket>.json   shard by the definition's first char

Shard key = codepoint >> 9, i.e. 512-codepoint buckets: a shard is a few
hundred KB rather than 6MB, so reading one character does not parse megabytes.
Matches how the
app indexes: look up shard by char codepoint, then look up the entry.

Usage:  uv run tools/merge_assets.py [source_dir] [out_dir]
"""
import json
import os
import shutil
import sys

DEFAULT_SRC = r"C:\Users\guyue\code\pupil-learning-tool\cnchar-data\cnchar-data"
DEFAULT_OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "app", "src", "main", "assets",
)

SKIP = {"package.json"}
SHARD_BITS = 9


def shard_of(ch: str) -> str:
    return f"s{(ord(ch) >> SHARD_BITS):x}"


def merge_dir(src_name: str, out_name: str, transform=None):
    src = os.path.join(SRC, src_name)
    out = os.path.join(OUT, out_name)
    if os.path.isdir(out):
        shutil.rmtree(out)
    os.makedirs(out, exist_ok=True)

    buckets: dict[str, dict] = {}
    skipped = 0
    for fname in sorted(os.listdir(src)):
        if not fname.endswith(".json") or fname in SKIP:
            skipped += 1
            continue
        ch = fname[:-5]
        if len(ch) != 1:
            skipped += 1
            continue
        with open(os.path.join(src, fname), encoding="utf-8") as f:
            payload = json.load(f)
        buckets.setdefault(shard_of(ch), {})[ch] = transform(payload) if transform else payload

    for shard, data in sorted(buckets.items()):
        path = os.path.join(out, f"shard-{shard}.json")
        # ensure_ascii=False keeps hanzi readable in the file; compact separators
        # cut the 30MB down by roughly a third versus the per-char originals.
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))

    total = sum(os.path.getsize(os.path.join(out, n)) for n in os.listdir(out))
    largest = max(
        os.path.getsize(os.path.join(out, n)) for n in os.listdir(out)
    )
    print(
        f"  {out_name}: {len(buckets)} shards, "
        f"{sum(len(v) for v in buckets.values())} entries, "
        f"skipped {skipped}, total {total / 1024 / 1024:.1f} MB, "
        f"largest {largest / 1024 / 1024:.2f} MB"
    )
    return buckets


def copy_voice():
    src = os.path.join(SRC, "voice")
    out = os.path.join(OUT, "voice")
    if os.path.isdir(out):
        shutil.rmtree(out)
    shutil.copytree(src, out)
    n = len(os.listdir(out))
    total = sum(os.path.getsize(os.path.join(out, f)) for f in os.listdir(out))
    print(f"  voice: {n} mp3, {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    SRC = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SRC
    OUT = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUT

    print(f"source: {SRC}")
    print(f"output: {OUT}")
    print("merging...")

    # radStrokes is a list of indices into `strokes` marking the radical.
    # Kept as-is; the app resolves indices to paths at draw time.
    merge_dir("draw", "draw_all")
    merge_dir("explanation", "explanation_all")
    copy_voice()

    print("done")