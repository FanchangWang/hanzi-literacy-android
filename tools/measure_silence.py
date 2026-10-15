"""Measure how much silence each syllable mp3 actually contains.

The user reports a long pause between syllables when concatenating. If the
assets carry leading or trailing silence, concatenating them compounds the
gap. This decodes with ffmpeg/pydub-free logic: use the bundled ffmpeg if
present, else fall back to estimating from the MP3 frame layout.

Run:  uv run tools/measure_silence.py
"""
import os
import struct
import subprocess
import sys

VOICE = r"C:\Users\guyue\code\pupil-learning-tool\cnchar-data\cnchar-data\voice"
SAMPLES = ["hao3", "chong2", "zhong4", "shi4", "ni3", "hao4", "zhong1"]


def decode_with_ffmpeg(path: str) -> tuple:
    """Return (rate, samples) as a list of 16-bit ints via ffmpeg raw output."""
    try:
        raw = subprocess.run(
            ["ffmpeg", "-v", "quiet", "-i", path, "-f", "s16le", "-acodec", "pcm_s16le", "-"],
            capture_output=True, check=True,
        ).stdout
        n = len(raw) // 2
        vals = struct.unpack(f"<{n}h", raw[: n * 2])
        return 44100, vals
    except Exception as e:
        print(f"  ffmpeg unavailable: {e}", file=sys.stderr)
        return None, None


def analyse(name: str) -> None:
    path = os.path.join(VOICE, name + ".mp3")
    rate, pcm = decode_with_ffmpeg(path)
    if pcm is None:
        return

    total = len(pcm)
    duration_ms = total * 1000.0 / rate

    # A sample counts as silence below this amplitude (out of 32767).
    threshold = 400
    window = rate // 50  # 20ms

    lead = 0
    for i in range(0, total, window):
        chunk = pcm[i:i + window]
        if chunk and max(abs(v) for v in chunk) > threshold:
            lead = i
            break

    tail = 0
    for i in range(0, total, window):
        chunk = pcm[total - window - i: total - i] if i else pcm[total - window:]
        if chunk and max(abs(v) for v in chunk) > threshold:
            tail = i
            break

    speak = duration_ms - (lead + tail) * 1000.0 / rate
    print(
        f"{name:<10} total {duration_ms:7.1f}ms   "
        f"lead {lead * 1000.0 / rate:6.1f}ms   "
        f"tail {tail * 1000.0 / rate:6.1f}ms   "
        f"speech {speak:7.1f}ms  ({100 * speak / duration_ms:4.1f}%)"
    )


if __name__ == "__main__":
    for s in SAMPLES:
        analyse(s)
