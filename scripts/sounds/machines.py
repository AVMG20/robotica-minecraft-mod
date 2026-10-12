"""Machine sounds: soft synthesized clips for the processing machines, generators and chargers.
Run: python3 scripts/sounds/machines.py   (writes assets/robotica/sounds/machine/*.ogg; needs numpy and ffmpeg)

Every clip is mono 44.1 kHz, built from sines and filtered noise with a seeded generator, faded in and out and
normalized to the same peak, so machines sound alike in loudness. Low and muffled on purpose: they repeat a lot.
Volumes and pitches per event live in sounds.json.
"""
import pathlib
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = pathlib.Path(__file__).resolve().parents[2] / 'src/main/resources/assets/robotica/sounds/machine'
PEAK = 0.5


def time(seconds):
    return np.arange(int(SR * seconds)) / SR


def noise(seconds, seed):
    return np.random.default_rng(seed).standard_normal(int(SR * seconds))


def band(signal, low, high):
    """Keeps low..high Hz with soft 1-octave shoulders (FFT filter)."""
    spectrum = np.fft.rfft(signal)
    f = np.fft.rfftfreq(len(signal), 1 / SR)
    gain = np.ones_like(f)
    if low > 0:
        gain *= np.clip(np.log2(np.maximum(f, 1) / low) + 1, 0, 1)
    if high:
        gain *= np.clip(1 - np.log2(np.maximum(f, 1) / high), 0, 1)
    return np.fft.irfft(spectrum * gain, len(signal))


def sweep(t, f0, f1, harmonics=(1.0,)):
    """Sine (plus harmonics) gliding exponentially from f0 to f1 Hz over t."""
    freq = f0 * (f1 / f0) ** (t / t[-1])
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return sum(a * np.sin(phase * (i + 1)) for i, a in enumerate(harmonics))


def env(t, attack, release, decay=None):
    """Linear attack, optional exponential decay (time constant), smooth release at the end."""
    e = np.minimum(1, t / max(attack, 1e-4))
    if decay:
        e *= np.exp(-np.maximum(0, t - attack) / decay)
    tail = np.clip((t[-1] - t) / release, 0, 1)
    return e * (0.5 - 0.5 * np.cos(np.pi * tail))


def at(total, start, clip):
    """Places clip in a silent buffer of total seconds, starting at start seconds."""
    out = np.zeros(int(SR * total))
    i = int(SR * start)
    n = min(len(clip), len(out) - i)
    out[i:i + n] += clip[:n]
    return out


def thump(seconds, f0, f1, decay, seed):
    t = time(seconds)
    body = sweep(t, f0, f1, (1.0, 0.25)) * np.exp(-t / decay)
    knock = band(noise(seconds, seed), 60, 500) * np.exp(-t / (decay * 0.4)) * 0.3
    return body + knock


# ------------------------------------------------------------------------------------------------------------ clips

def start():
    """Motor spinning up: a low glide with a breath of air."""
    t = time(0.6)
    motor = sweep(t, 70, 140, (1.0, 0.5, 0.25, 0.1))
    air = band(noise(0.6, 1), 300, 1500) * (t / t[-1]) * 0.25
    return (motor + air) * env(t, 0.08, 0.25)


def stop():
    """Motor winding down."""
    t = time(0.7)
    motor = sweep(t, 140, 55, (1.0, 0.5, 0.25, 0.1)) * np.exp(-t / 0.35)
    air = band(noise(0.7, 2), 250, 1200) * np.exp(-t / 0.2) * 0.25
    return (motor + air) * env(t, 0.02, 0.3)


def press():
    """Muffled hydraulic thump, then the valve lets out a short hiss."""
    total = 0.6
    hit = thump(0.3, 150, 55, 0.09, 3)
    t = time(0.45)
    hiss = band(noise(0.45, 4), 1800, 5000) * env(t, 0.03, 0.2, 0.12) * 0.12
    return at(total, 0, hit) + at(total, 0.1, hiss)


def grind():
    """Rough low rumble of stone on steel."""
    t = time(0.7)
    rough = band(np.cumsum(noise(0.7, 5)) * 0.02, 120, 1200)
    rough *= 1 - 0.45 * (0.5 + 0.5 * np.sin(2 * np.pi * 23 * t))
    return rough * env(t, 0.06, 0.3)


def smelt():
    """Soft roar of a hot chamber, a few faint crackles."""
    total = 0.9
    t = time(total)
    roar = band(noise(total, 6), 40, 500) * env(t, 0.25, 0.4)
    rng = np.random.default_rng(7)
    sparks = np.zeros_like(t)
    for start_at in rng.uniform(0.1, 0.6, 4):
        c = time(0.03)
        sparks += at(total, start_at, band(noise(0.03, int(start_at * 1000)), 1500, 6000) * np.exp(-c / 0.006))
    return roar + sparks * 0.15


def centrifuge():
    """Rotor whir with a steady wobble."""
    t = time(1.0)
    wobble = 0.75 + 0.25 * np.sin(2 * np.pi * 11 * t)
    rotor = sweep(t, 175, 185, (1.0, 0.35, 0.1)) * wobble
    air = band(noise(1.0, 8), 700, 2500) * wobble * 0.2
    return (rotor + air) * env(t, 0.2, 0.3)


def assemble():
    """Servo glide, then two soft clicks as a part seats."""
    total = 0.6
    t = time(0.28)
    servo = band(sweep(t, 300, 480, (1.0, 0.5, 0.3, 0.2)), 0, 1500) * env(t, 0.04, 0.08) * 0.6
    c = time(0.08)
    click = (np.sin(2 * np.pi * 850 * c) * 0.5 + band(noise(0.08, 9), 200, 2000)) * np.exp(-c / 0.012)
    return at(total, 0, servo) + at(total, 0.3, click * 0.6) + at(total, 0.42, click * 0.45)


def hum():
    """Electric hum with a slow beat (pitched up for chargers, down for the RTG in sounds.json)."""
    t = time(1.6)
    tone = np.sin(2 * np.pi * 100 * t) + 0.35 * np.sin(2 * np.pi * 200.6 * t) + 0.12 * np.sin(2 * np.pi * 300 * t)
    beat = 0.85 + 0.15 * np.sin(2 * np.pi * 1.6 * t)
    return tone * beat * env(t, 0.35, 0.5)


def chime():
    """Two soft bell notes, rising."""
    total = 0.9

    def bell(f, seconds):
        t = time(seconds)
        return (np.sin(2 * np.pi * f * t) + 0.2 * np.sin(2 * np.pi * f * 2.76 * t)) * np.exp(-t / 0.18) * env(t, 0.005, 0.05)

    return at(total, 0, bell(1046.5, 0.6)) + at(total, 0.11, bell(1568.0, 0.75) * 0.8)


CLIPS = {
    'start': start, 'stop': stop, 'press': press, 'grind': grind, 'smelt': smelt,
    'centrifuge': centrifuge, 'assemble': assemble, 'hum': hum, 'chime': chime,
}


def write(name, signal):
    signal = signal / np.max(np.abs(signal)) * PEAK
    pcm = (signal * 32767).astype('<i2')
    with tempfile.TemporaryDirectory() as tmp:
        wav = pathlib.Path(tmp) / 'clip.wav'
        with wave.open(str(wav), 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(wav), '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4',
                        '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', str(OUT / (name + '.ogg'))],
                       check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in CLIPS.items():
        write(name, make())
        print('wrote', (OUT / (name + '.ogg')).relative_to(OUT.parents[6]))


if __name__ == '__main__':
    main()
