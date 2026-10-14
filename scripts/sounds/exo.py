"""Exo-Frame sounds: the Jet Assist thruster loop, its overheat cut-out and the cooled-down tick.
Run: python3 scripts/sounds/exo.py   (needs numpy and ffmpeg with libvorbis)

Writes assets/robotica/sounds/exo/jet_{loop,overheat,ready}.ogg, mono (positional), made from seeded noise and sines,
so every run gives the same files. The loop is 3 s and crossfaded into itself so it repeats without a click.
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/robotica/sounds/exo'
RATE = 44100


def lowpass(x, cutoff):
    """One-pole low-pass at about {@code cutoff} Hz."""
    a = np.exp(-2.0 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1.0 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, low, high):
    return lowpass(highpass(x, low), high)


def env(n, attack, release):
    """Linear attack and exponential release envelope over n samples (times in seconds)."""
    t = np.arange(n) / RATE
    e = np.minimum(1.0, t / max(attack, 1e-4))
    return e * np.exp(-t / max(release, 1e-4))


def normalize(x, peak=0.89):
    return x * (peak / max(1e-9, np.max(np.abs(x))))


def loop_crossfade(x, fade):
    """Folds the last {@code fade} samples over the first ones (equal power) so the result loops seamlessly."""
    body, tail = x[:-fade].copy(), x[-fade:]
    w = np.linspace(0.0, 1.0, fade)
    body[:fade] = body[:fade] * np.sqrt(w) + tail * np.sqrt(1.0 - w)
    return body


def jet_loop(rng):
    """A soft, steady air rush: muffled low whoosh with a little breath on top, no crackle or flutter."""
    seconds, fade = 3.0, int(0.5 * RATE)
    n = int(seconds * RATE) + fade
    white = rng.standard_normal(n)
    body = lowpass(lowpass(highpass(white, 90.0), 520.0), 700.0) * 2.4
    breath = lowpass(bandpass(rng.standard_normal(n), 700.0, 1800.0), 2200.0) * 0.35
    t = np.arange(n) / RATE
    # 1 cycle in 3 s: a slow, even swell that lines up with the loop.
    swell = 1.0 + 0.05 * np.sin(2 * np.pi * t / seconds)
    x = (body + breath) * swell
    return normalize(loop_crossfade(x, fade), 0.6)


def jet_overheat(rng):
    """Cut-out: a heavy clunk, three sputters that die off, then a long venting hiss."""
    n = int(1.6 * RATE)
    x = np.zeros(n)
    t = np.arange(n) / RATE
    k = int(0.18 * RATE)
    sweep = np.sin(2 * np.pi * np.cumsum(np.linspace(130.0, 45.0, k)) / RATE)
    x[:k] += sweep * env(k, 0.002, 0.06) * 1.2
    x[:k] += lowpass(rng.standard_normal(k), 900.0) * env(k, 0.001, 0.03) * 2.0
    for start, amp in ((0.06, 1.0), (0.17, 0.75), (0.25, 0.55), (0.38, 0.35)):
        s = int(start * RATE)
        m = int(0.07 * RATE)
        burst = bandpass(rng.standard_normal(m), 200.0, 3000.0) * env(m, 0.003, 0.018) * amp * 2.2
        x[s:s + m] += burst
    s = int(0.3 * RATE)
    m = n - s
    vent = highpass(rng.standard_normal(m), 2500.0) * 0.9
    vent *= np.minimum(1.0, np.arange(m) / (0.08 * RATE)) * np.exp(-np.arange(m) / (0.45 * RATE))
    vent *= 1.0 + 0.25 * np.sin(2 * np.pi * 7.0 * t[s:])
    x[s:] += vent
    return normalize(x, 0.85)


def jet_ready(rng):
    """Cooled down: two quick metal ticks and a short bright ping."""
    n = int(0.45 * RATE)
    x = np.zeros(n)
    for start in (0.0, 0.055):
        s = int(start * RATE)
        m = int(0.006 * RATE)
        x[s:s + m] += highpass(rng.standard_normal(m), 2000.0) * np.linspace(1.0, 0.0, m) * 1.4
    s = int(0.1 * RATE)
    m = n - s
    tt = np.arange(m) / RATE
    ping = np.sin(2 * np.pi * 1760.0 * tt) + 0.45 * np.sin(2 * np.pi * 2637.0 * tt) + 0.2 * np.sin(2 * np.pi * 4400.0 * tt)
    x[s:] += ping * env(m, 0.002, 0.09) * 0.55
    return normalize(x, 0.7)


def write_ogg(name, samples):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(samples, -1.0, 1.0) * 32767.0).astype('<i2')
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / f'{name}.wav'
        with wave.open(str(wav), 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '4',
                        '-fflags', '+bitexact', '-flags:a', '+bitexact', '-map_metadata', '-1', str(OUT / f'{name}.ogg')],
                       check=True)


def main():
    write_ogg('jet_loop', jet_loop(np.random.default_rng(31)))
    write_ogg('jet_overheat', jet_overheat(np.random.default_rng(32)))
    write_ogg('jet_ready', jet_ready(np.random.default_rng(33)))
    print('exo sounds written to', OUT)


if __name__ == '__main__':
    main()
