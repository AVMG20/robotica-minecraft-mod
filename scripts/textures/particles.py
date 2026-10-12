"""Robotica particle sprites (white or grey, the particle tints them) and their particle definitions.
Run: python3 scripts/textures/particles.py
Writes textures/particle/*.png and particles/<type>.json for the types in core/particle/CoreParticles.java.
"""
import json
import math
import pathlib
import random
import struct
import sys
import zlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS  # noqa: E402

TEX = ASSETS / 'textures/particle'
DEFS = ASSETS / 'particles'


def write_rgba(name, pixels):
    """pixels: rows of (r, g, b, a) tuples, 0..255."""
    h, w = len(pixels), len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))), max(0, min(255, int(a)))))

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    TEX.mkdir(parents=True, exist_ok=True)
    (TEX / f'{name}.png').write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(bytes(raw), 9))
                                      + chunk(b'IEND', b''))


def blank(size):
    return [[(255, 255, 255, 0) for _ in range(size)] for _ in range(size)]


def radial(size, falloff):
    """White sprite whose alpha is falloff(distance from the centre in pixels)."""
    c = (size - 1) / 2
    return [[(255, 255, 255, 255 * falloff(math.hypot(x - c, y - c))) for x in range(size)] for y in range(size)]


def spark():
    return radial(8, lambda d: max(0.0, 1.0 - (d / 3.6) ** 2))


def mote():
    return radial(16, lambda d: math.exp(-d * d / (2 * 2.9 ** 2)) * (1.0 if d > 1.2 else 1.0))


def flash():
    size, c = 16, 7.5
    px = blank(size)
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            d = math.hypot(dx, dy)
            glow = math.exp(-d * d / (2 * 3.4 ** 2))
            ang = math.atan2(dy, dx)
            rays = max(0.0, math.cos(ang * 4)) ** 8 * max(0.0, 1 - d / 8.2) * 0.55
            px[y][x] = (255, 255, 255, 255 * min(1.0, glow + rays))
    return px


def warp():
    """Four-point star with a soft centre."""
    size, c = 8, 3.5
    px = blank(size)
    for y in range(size):
        for x in range(size):
            dx, dy = abs(x - c), abs(y - c)
            d = math.hypot(dx, dy)
            star = max(0.0, 1.0 - (dx * dy) * 0.9 - d / 4.4)
            core = max(0.0, 1.0 - d / 2.2)
            px[y][x] = (255, 255, 255, 255 * min(1.0, star + core))
    return px


def zap(seed):
    """A jagged 1 px bolt across the sprite with a faint halo."""
    rnd = random.Random(seed)
    size = 8
    px = blank(size)
    # walk from the left edge to the right edge with kinks
    x, y = 0, rnd.randint(2, 5)
    points = [(x, y)]
    while x < size - 1:
        x += 1
        if rnd.random() < 0.55:
            y = max(1, min(size - 2, y + rnd.choice((-1, 1))))
        points.append((x, y))
    # a short branch
    bx, by = points[rnd.randint(2, 5)]
    for _ in range(2):
        bx, by = bx + 1, by + rnd.choice((-1, 1))
        if 0 <= bx < size and 0 <= by < size:
            points.append((bx, by))
    halo = set()
    for (px_, py_) in points:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            halo.add((px_ + dx, py_ + dy))
    for (hx, hy) in halo:
        if 0 <= hx < size and 0 <= hy < size:
            px[hy][hx] = (255, 255, 255, 70)
    for (lx, ly) in points:
        px[ly][lx] = (255, 255, 255, 255)
    if seed % 2:
        px = [list(reversed(row)) for row in px]
    if seed >= 2:
        px = [list(r) for r in zip(*px)]
    return px


def puff(frame):
    """Soft cloud blob, thinner and more broken up each frame."""
    rnd = random.Random(40 + frame)
    size, c = 8, 3.5
    radius = 2.6 + frame * 0.35
    density = 1.0 - frame * 0.18
    px = blank(size)
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - c, y - c)
            base = max(0.0, 1.0 - (d / radius) ** 2)
            noise = 0.75 + 0.5 * rnd.random()
            a = base * noise * density
            shade = 235 + 20 * rnd.random()
            px[y][x] = (shade, shade, shade, 255 * min(1.0, a))
    return px


SHARDS = (
    ['........', '...##...', '..####..', '..#####.', '...####.', '....##..', '........', '........'],
    ['........', '.##.....', '.####...', '..#####.', '...###..', '....#...', '........', '........'],
    ['........', '....#...', '...###..', '..####..', '.#####..', '..###...', '...#....', '........'],
)


def shard(i):
    rows = SHARDS[i]
    px = blank(8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '#':
                continue
            lit_edge = (y == 0 or rows[y - 1][x] != '#') or (x == 0 or row[x - 1] != '#')
            dark_edge = (y == 7 or rows[y + 1][x] != '#') or (x == 7 or row[x + 1] != '#')
            v = 255 if lit_edge and not dark_edge else 150 if dark_edge and not lit_edge else 205
            px[y][x] = (v, v, v, 255)
    return px


def main():
    write_rgba('spark', spark())
    write_rgba('mote', mote())
    write_rgba('flash', flash())
    write_rgba('warp', warp())
    for i in range(4):
        write_rgba(f'zap_{i}', zap(i))
        write_rgba(f'puff_{i}', puff(i))
    for i in range(len(SHARDS)):
        write_rgba(f'shard_{i}', shard(i))

    sprites = {
        'spark': ['spark'],
        'zap': [f'zap_{i}' for i in range(4)],
        'mote': ['mote'],
        'puff': [f'puff_{i}' for i in range(4)],
        'warp': ['warp'],
        'shard': [f'shard_{i}' for i in range(len(SHARDS))],
        'flash': ['flash'],
    }
    DEFS.mkdir(parents=True, exist_ok=True)
    for name, textures in sprites.items():
        (DEFS / f'{name}.json').write_text(json.dumps({'textures': [f'robotica:{t}' for t in textures]}, indent=2) + '\n')
    print('particle sprites written')


if __name__ == '__main__':
    main()
