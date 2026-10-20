"""Machine sounds: soft synthesized clips for the processing machines, the RTG, chargers and the Impact Ram.
Run: python3 scripts/sounds/machines.py   (writes assets/robotica/sounds/machine/*.ogg; needs numpy and ffmpeg)

Every clip is mono 44.1 kHz, built from sines and filtered noise with a seeded generator, faded in and out and
normalized to the same peak, so machines sound alike in loudness. Low and muffled on purpose: they repeat a lot.
No sustained low hums: start/stop, the press, the Assembler and the charger hum use vanilla sounds. Volumes and
pitches per event live in sounds.json.
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


# ------------------------------------------------------------------------------------------------------------ clips

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
    """Air swishing past a spinning drum."""
    t = time(1.0)
    swish = (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t)) ** 2
    return band(noise(1.0, 8), 500, 3000) * (0.25 + 0.75 * swish) * env(t, 0.2, 0.3)


def geiger():
    """Irregular ticks of a radiation counter (RTG)."""
    total = 1.4
    rng = np.random.default_rng(11)
    c = time(0.01)
    out = np.zeros(int(SR * total))
    for i, start_at in enumerate(np.cumsum(rng.exponential(0.08, 30))):
        if start_at > total - 0.02:
            break
        out += at(total, start_at, band(noise(0.01, 20 + i), 1200, 5000) * np.exp(-c / 0.0015) * rng.uniform(0.5, 1.0))
    return out


def chime():
    """Two soft bell notes, rising."""
    total = 0.9

    def bell(f, seconds):
        t = time(seconds)
        return (np.sin(2 * np.pi * f * t) + 0.2 * np.sin(2 * np.pi * f * 2.76 * t)) * np.exp(-t / 0.18) * env(t, 0.005, 0.05)

    return at(total, 0, bell(1046.5, 0.6)) + at(total, 0.11, bell(1568.0, 0.75) * 0.8)


def ram():
    """Impact Ram strike: a heavy low thump with a steel clank on top, then the hiss of the ram pulling back. The hit
    starts the clip, as the sound plays on the tick the head lands."""
    total = 0.6
    t = time(total)
    k = time(0.45)
    freq = 55 + 85 * np.exp(-k / 0.03)                                    # thump falling from 140 to 55 Hz
    thump = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-k / 0.085)
    body = band(noise(0.45, 32), 60, 400) * np.exp(-k / 0.05) * 0.5
    crack = band(noise(0.45, 33), 300, 2500) * np.exp(-k / 0.012) * 0.45
    partials = ((310, 0.22), (737, 0.16), (1290, 0.08))                  # steel clank, inharmonic
    clank = sum(a * np.sin(2 * np.pi * f * k) for f, a in partials) * np.exp(-k / 0.07)
    hit = np.tanh(1.6 * (thump + body + crack + clank)) / np.tanh(1.6) * env(k, 0.002, 0.08)  # soft clip
    vent = band(noise(0.35, 34), 1200, 4500) * np.exp(-time(0.35) / 0.09) * env(time(0.35), 0.03, 0.12) * 0.09
    return (at(total, 0.005, hit) + at(total, 0.12, vent)) * env(t, 0.001, 0.1)


CLIPS = {
    'grind': grind, 'smelt': smelt, 'centrifuge': centrifuge, 'geiger': geiger,
    'chime': chime, 'ram': ram,
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
