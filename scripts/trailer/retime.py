#!/usr/bin/env python3
"""Turns the recorder's footage into real-speed 60 fps clips (Python 3 stdlib + ffmpeg/ffprobe).

For every <scene>_raw.mp4 + <scene>_times.txt in the footage folder (the recorder captured every rendered frame while
the game ran at tick rate 5, tagged with its scene time in ticks) it picks, for each output frame at 60 fps
(t_out = i/60 s, scene ticks = t_out * 20 * speed), the captured frame with the nearest scene time and encodes
<scene>.mp4 (1920x1080, 60 fps, crf 16).

Usage: retime.py [--dir run-trailer/footage] [--speed 1.0] [scene ...]
  --speed 0.5 gives half speed (slow motion), 2 double speed.
"""
import argparse
import bisect
import os
import shutil
import subprocess
import sys

FFMPEG = "/opt/homebrew/bin/ffmpeg" if os.path.exists("/opt/homebrew/bin/ffmpeg") else shutil.which("ffmpeg") or "ffmpeg"
FFPROBE = "/opt/homebrew/bin/ffprobe" if os.path.exists("/opt/homebrew/bin/ffprobe") else shutil.which("ffprobe") or "ffprobe"
W, H = 1920, 1080


def read_times(path):
    with open(path) as f:
        return [float(line) for line in f if line.strip()]


def nearest(times, t):
    i = bisect.bisect_left(times, t)
    if i == 0:
        return 0
    if i >= len(times):
        return len(times) - 1
    return i if times[i] - t < t - times[i - 1] else i - 1


def retime(folder, scene, speed):
    raw = os.path.join(folder, scene + "_raw.mp4")
    times = read_times(os.path.join(folder, scene + "_times.txt"))
    out = os.path.join(folder, scene + ".mp4")
    if len(times) < 2:
        print(f"{scene}: not enough frames ({len(times)})", file=sys.stderr)
        return False
    t0, t1 = times[0], times[-1]
    step = 20.0 * speed / 60.0  # scene ticks per output frame
    count = int((t1 - t0) / step) + 1
    wanted = [nearest(times, t0 + i * step) for i in range(count)]
    frame_size = W * H * 3 // 2
    dec = subprocess.Popen([FFMPEG, "-v", "error", "-i", raw, "-f", "rawvideo", "-pix_fmt", "yuv420p", "-"],
                           stdout=subprocess.PIPE)
    enc = subprocess.Popen([FFMPEG, "-y", "-v", "error", "-f", "rawvideo", "-pix_fmt", "yuv420p", "-s", f"{W}x{H}",
                            "-r", "60", "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "16",
                            "-pix_fmt", "yuv420p", "-movflags", "+faststart", out], stdin=subprocess.PIPE)
    current, index = None, -1
    for target in wanted:
        while index < target:
            buf = dec.stdout.read(frame_size)
            if len(buf) < frame_size:
                break
            current, index = buf, index + 1
        if current is None:
            break
        enc.stdin.write(current)
    enc.stdin.close()
    dec.stdout.close()
    dec.terminate()
    enc.wait()
    dec.wait()
    ticks = t1 - t0
    deltas = [b - a for a, b in zip(times, times[1:])]
    print(f"{scene}: {len(times)} captured frames over {ticks:.1f} ticks ({len(times) / ticks:.1f} per tick, "
          f"max gap {max(deltas):.3f} ticks) -> {count} frames, {count / 60:.2f} s: {out}")
    return enc.returncode == 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=os.path.join(os.path.dirname(__file__), "..", "..", "run-trailer", "footage"))
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("scenes", nargs="*")
    args = ap.parse_args()
    folder = os.path.abspath(args.dir)
    scenes = args.scenes or sorted(f[:-8] for f in os.listdir(folder) if f.endswith("_raw.mp4"))
    ok = True
    for scene in scenes:
        ok = retime(folder, scene, args.speed) and ok
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
