"""Null Lance sounds: one void family for the whole rifle.
Run: python3 scripts/sounds/lance.py   (needs numpy and ffmpeg with libvorbis)

Writes assets/robotica/sounds/gear/lance_*.ogg, mono (positional), made from seeded noise and sines, so every run gives
the same files:
  charge  1.1 s  rising void spool with 4 coil ticks at 0.20/0.45/0.70/0.95 s (25/50/75/100 % of the 1 s charge)
  ready   0.9 s  lock-in: mechanical clunk and a bright crystal ping
  hum     2.0 s  quiet low loop while the charged rifle is held; every part repeats exactly in 2 s, so it loops seamlessly
  fire    1.8 s  the beam: sub thump, void crack and tear, falling zap, reverb tail
  bolt    0.55 s null bolt: a short punchy void shot
  impact  1.0 s  void collapse where the beam ends
  fizzle  0.6 s  early release: the charge winds down and sputters out
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/robotica/sounds/gear'
RATE = 44100
# Coil ticks in the charge sound. The server starts it on the first charge tick and the charge is full on tick 20.
COIL_TICKS = (0.20, 0.45, 0.70, 0.95)


def samples(seconds):
    return int(round(seconds * RATE))


def times(n):
    return np.arange(n) / RATE


def filt(x, low=None, high=None, order=2, periodic=False):
    """Zero-phase Butterworth-shaped band filter in the frequency domain. Periodic keeps a loop seamless."""
    n = len(x)
    m = n if periodic else n + min(n, RATE // 2)
    spec = np.fft.rfft(x, m)
    f = np.fft.rfftfreq(m, 1.0 / RATE)
    g = np.ones_like(f)
    if high is not None:
        g /= np.sqrt(1.0 + (f / high) ** (2 * order))
    if low is not None:
        with np.errstate(divide='ignore'):
            g /= np.sqrt(1.0 + (low / np.maximum(f, 1e-6)) ** (2 * order))
    return np.fft.irfft(spec * g, m)[:n]


def decay(n, attack, tau, start=0.0):
    """Linear attack then exponential decay, starting {@code start} seconds in (0 before)."""
    t = times(n) - start
    e = np.where(t < 0, 0.0, np.minimum(1.0, t / max(attack, 1e-4)) * np.exp(-np.maximum(t, 0.0) / tau))
    return e


def chirp(n, f0, f1, sweep, start=0.0):
    """Phase of an exponential glide from f0 to f1 over {@code sweep} seconds, holding f1 after."""
    t = np.maximum(times(n) - start, 0.0)
    f = np.where(t < sweep, f0 * (f1 / f0) ** (t / sweep), f1)
    return 2 * np.pi * np.cumsum(f) / RATE


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def soft(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)


def place(x, part, start):
    s = samples(start)
    end = min(len(x), s + len(part))
    x[s:end] += part[:end - s]


def reverb(x, rng, seconds, tau, mix, low=150.0, high=3800.0):
    """A short synthetic room: decaying filtered noise with a few early echoes, convolved in and mixed under the dry."""
    n = samples(seconds)
    ir = filt(rng.standard_normal(n), low, high) * decay(n, 0.004, tau, 0.010)
    for delay, gain in ((0.013, 0.6), (0.029, 0.45), (0.047, 0.3)):
        ir[samples(delay)] += gain * 8
    m = len(x) + n
    wet = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[:len(x)]
    wet *= np.sqrt(np.mean(x ** 2)) / max(1e-9, np.sqrt(np.mean(wet ** 2)))
    return x + mix * wet


def fade_out(x, seconds):
    k = samples(seconds)
    x[-k:] *= np.linspace(1.0, 0.0, k) ** 2
    return x


def normalize(x, peak):
    return x * (peak / max(1e-9, np.max(np.abs(x))))


def click(rng, seconds, low, tau):
    n = samples(seconds)
    return filt(rng.standard_normal(n), low) * decay(n, 0.0003, tau)


def charge(rng):
    """Rising void whine that steps up at each coil, a deep sub under it, 4 coil ticks with rising blips."""
    n = samples(1.10)
    t = times(n)
    prog = np.clip(t / COIL_TICKS[-1], 0.0, 1.0)
    steps = sum(smoothstep((t - c) / 0.035) for c in COIL_TICKS) / len(COIL_TICKS)
    p = 0.55 * prog ** 1.3 + 0.45 * steps
    f = 130.0 * 2 ** (p * 2.6)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    whine = np.sin(phase) + 0.4 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    whine += 0.6 * np.sin(phase * 1.007 + 0.5)
    sub = 0.55 * np.sin(phase * 0.5)
    texture = filt(rng.standard_normal(n), None, 380.0) * np.sin(phase * 1.5) * 1.6
    rate = 7.0 + 26.0 * prog
    spool = 1.0 - 0.28 * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(rate) / RATE))
    amp = (0.22 + 0.78 * prog ** 1.2) * np.minimum(1.0, t / 0.04)
    amp *= np.clip((1.10 - t) / 0.12, 0.0, 1.0)
    x = (0.42 * whine + sub + texture) * spool * amp
    for i, c in enumerate(COIL_TICKS):
        k = samples(0.16)
        tt = times(k)
        level = 0.75 + 0.12 * i
        tick = click(rng, 0.16, 2500.0, 0.0016) * 0.9
        tick += np.sin(2 * np.pi * (880.0 * 1.26 ** i) * tt) * decay(k, 0.001, 0.035) * 0.5
        tick += np.sin(2 * np.pi * 170.0 * tt) * decay(k, 0.001, 0.022) * 0.45
        place(x, tick * level, c)
    return normalize(x, 0.85)


def ready(rng):
    """Lock-in: a heavy clunk and latch, then a bright crystal ping with a little shimmer, over a short sub swell."""
    n = samples(0.9)
    t = times(n)
    x = soft(np.sin(chirp(n, 115.0, 44.0, 0.09)) * decay(n, 0.001, 0.075), 1.8) * 1.0
    x += filt(rng.standard_normal(n), None, 900.0) * decay(n, 0.0005, 0.022) * 1.4
    for at in (0.0, 0.019):
        place(x, click(rng, 0.03, 3000.0, 0.0018) * 0.9, at)
    ping = np.zeros(n)
    tp = np.maximum(t - 0.014, 0.0)
    for ratio, amp, tau in ((1.0, 1.0, 0.42), (1.0021, 0.6, 0.38), (2.32, 0.45, 0.24), (4.25, 0.28, 0.13), (6.63, 0.16, 0.08)):
        ping += amp * np.sin(2 * np.pi * 1568.0 * ratio * tp) * decay(n, 0.002, tau, 0.014)
    x += ping * 0.42
    x += np.sin(2 * np.pi * 55.0 * t) * decay(n, 0.02, 0.25) * 0.35
    x = reverb(x, rng, 0.6, 0.14, 0.2)
    return normalize(fade_out(x, 0.08), 0.85)


def hum(rng):
    """Low steady void hum with a slow beat and faint shimmer. Every part repeats in exactly 2 s."""
    seconds = 2.0
    n = samples(seconds)
    t = times(n)
    tone = np.sin(2 * np.pi * 55.0 * t) + 0.5 * np.sin(2 * np.pi * 110.0 * t + 0.4) + 0.15 * np.sin(2 * np.pi * 165.0 * t)
    tone += 0.4 * np.sin(2 * np.pi * 55.5 * t + 1.1)
    swell = 1.0 + 0.12 * np.sin(2 * np.pi * t / seconds) + 0.05 * np.sin(2 * np.pi * 3.0 * t)
    air = filt(rng.standard_normal(n), 300.0, 700.0, periodic=True)
    air *= 0.12 / max(1e-9, np.sqrt(np.mean(air ** 2))) * (1.0 + 0.5 * np.sin(2 * np.pi * 1.0 * t))
    shimmer = 0.03 * np.sin(2 * np.pi * 880.0 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t))
    return normalize(tone * swell + air + shimmer, 0.6)


def fire(rng):
    """The beam: sub thump, void crack, a tearing ring-mod rip, a falling zap, crackle and rumble, then a room tail."""
    n = samples(1.8)
    t = times(n)
    x = soft(np.sin(chirp(n, 150.0, 32.0, 0.35)) * decay(n, 0.002, 0.22), 2.0) * 1.0
    x += filt(rng.standard_normal(n), 1500.0, 9000.0) * decay(n, 0.001, 0.006, 0.004) * 1.0
    x += filt(rng.standard_normal(n), 300.0, 3000.0) * decay(n, 0.001, 0.04) * 0.7
    gate = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * np.cumsum(55.0 + 30.0 * rng.random(n)) / RATE))
    gate = filt(gate, None, 400.0)
    tear = filt(rng.standard_normal(n), None, 600.0) * np.sin(chirp(n, 3200.0, 250.0, 0.4)) * gate
    x += tear * decay(n, 0.003, 0.12) * 1.6
    zap = chirp(n, 2000.0, 180.0, 0.3)
    x += (np.sin(zap) + 0.5 * np.sin(2 * zap) + 0.25 * np.sin(3 * zap) + 0.6 * np.sin(zap * 0.99)) * decay(n, 0.002, 0.15) * 0.3
    x += filt(rng.standard_normal(n), None, 200.0) * decay(n, 0.005, 0.35) * 2.2
    crackle = np.zeros(n)
    for _ in range(40):
        at = 0.04 + rng.exponential(0.15)
        if at < 0.9:
            place(crackle, click(rng, 0.01, 2500.0, 0.0008) * np.exp(-at / 0.3) * (0.3 + 0.7 * rng.random()), at)
    x += crackle * 0.4
    x = soft(x / np.max(np.abs(x)), 1.5)
    x = reverb(x, rng, 1.4, 0.35, 0.35, 120.0, 3500.0)
    # squeeze the crack so the body and tail carry: the loudest sound of the gear set
    x = soft(x / np.max(np.abs(x)), 1.7)
    return normalize(fade_out(x, 0.2), 0.78)


def bolt(rng):
    """Null bolt: a short punchy thump and crack with a quick falling zap and a void whoosh, tight room."""
    n = samples(0.55)
    x = soft(np.sin(chirp(n, 180.0, 60.0, 0.08)) * decay(n, 0.001, 0.05), 1.8) * 0.9
    x += click(rng, 0.55, 2000.0, 0.003) * 1.0
    x += filt(rng.standard_normal(n), 500.0, 4000.0) * decay(n, 0.0008, 0.015) * 0.5
    zap = chirp(n, 1400.0, 350.0, 0.12)
    x += (np.sin(zap) + 0.4 * np.sin(2 * zap)) * decay(n, 0.001, 0.06) * 0.5
    x += np.sin(2 * np.pi * 2400.0 * times(n)) * decay(n, 0.001, 0.08) * 0.1
    x += filt(rng.standard_normal(n), None, 500.0) * np.sin(chirp(n, 900.0, 300.0, 0.1)) * decay(n, 0.002, 0.07) * 1.2
    x = soft(x / np.max(np.abs(x)), 1.4)
    x = reverb(x, rng, 0.5, 0.12, 0.22)
    return normalize(fade_out(x, 0.1), 0.85)


def impact(rng):
    """Void collapse: a quick inward suck into a deep thud, glassy shards and a dark rumble."""
    n = samples(1.0)
    x = filt(rng.standard_normal(n), None, 500.0) * np.sin(chirp(n, 200.0, 1200.0, 0.05)) * decay(n, 0.04, 0.012) * 1.2
    x += soft(np.sin(chirp(n, 90.0, 30.0, 0.25, 0.04)) * decay(n, 0.002, 0.18, 0.04), 2.0) * 1.0
    x += filt(rng.standard_normal(n), None, 150.0) * decay(n, 0.01, 0.4, 0.04) * 2.0
    for _ in range(9):
        at = 0.04 + rng.random() * 0.16
        k = samples(0.3)
        f = 2000.0 + rng.random() * 4000.0
        part = np.sin(2 * np.pi * f * times(k)) * decay(k, 0.0005, 0.03 + rng.random() * 0.09)
        place(x, part * (0.08 + rng.random() * 0.1), at)
    x = reverb(x, rng, 1.0, 0.25, 0.3)
    return normalize(fade_out(x, 0.15), 0.7)


def fizzle(rng):
    """The charge winds down and sputters out."""
    n = samples(0.6)
    whine = chirp(n, 600.0, 120.0, 0.5)
    x = (np.sin(whine) + 0.3 * np.sin(2 * whine)) * decay(n, 0.005, 0.18) * 0.5
    gate = (rng.random(n // 1764 + 1) > 0.45).astype(float).repeat(1764)[:n]
    x += filt(rng.standard_normal(n), 800.0, 4000.0) * filt(gate, None, 120.0) * decay(n, 0.005, 0.25) * 0.5
    return normalize(fade_out(x, 0.1), 0.7)


def write_ogg(name, x):
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(x, -1.0, 1.0) * 32767.0).astype('<i2')
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


SOUNDS = (('lance_charge', charge), ('lance_ready', ready), ('lance_hum', hum), ('lance_fire', fire),
          ('lance_bolt', bolt), ('lance_impact', impact), ('lance_fizzle', fizzle))


def main():
    for seed, (name, make) in enumerate(SOUNDS, start=41):
        write_ogg(name, make(np.random.default_rng(seed)))
    print('lance sounds written to', OUT)


if __name__ == '__main__':
    main()
