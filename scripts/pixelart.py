"""
Tiny pixel-art texture toolkit (no dependencies).

Sprites are lists of strings, one char per pixel, '.' = transparent. Colors come from a palette dict
(char -> '#RRGGBB' or '#RRGGBBAA'). Rows shorter than the widest row are padded with '.'.

    from pixelart import PALETTE, sprite, write_item
    write_item('copper_gear', ROWS, PALETTE | {'m': '#C87533'})

write_item() writes textures/item/<name>.png and, if missing, models/item/<name>.json (item/generated).
write_block() writes textures/block/<name>.png only; block models/blockstates are written by hand.
"""
import json
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src/main/resources/assets/robotica'

# Shared base palette. Override per sprite with dict union: PALETTE | {...}
PALETTE = {
    'k': '#1E1A1A',  # outline
    'c': '#C87533', 'C': '#E8A060', 'd': '#8A4A22',  # copper
    'b': '#D4A73A', 'B': '#F2D27A',  # brass
    'g': '#8D9599', 'G': '#C4CBCE', 's': '#4D5558',  # steel
    'y': '#5FE3F0', 'Y': '#D8FBFF',  # energy cyan
    'r': '#C9302A', 'R': '#FF6B5E',  # redstone
    'w': '#8B5E34', 'W': '#B98550',  # wood
    'l': '#3E8E3A', 'L': '#67B54F',  # leaf
    'o': '#FFB21E', 'O': '#FFE08A',  # gold
    'e': '#2E8F7F', 'E': '#5AD6BE',  # ender
    'p': '#6A3FB0', 'P': '#A884F0',  # purple
}


def _rgba(hex_color):
    h = hex_color.lstrip('#')
    if len(h) == 6:
        h += 'FF'
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4, 6))


def png_bytes(rows, palette, size=None):
    width = max(len(r) for r in rows)
    height = len(rows)
    if size:
        width = height = size
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        row = rows[y] if y < len(rows) else ''
        for x in range(width):
            ch = row[x] if x < len(row) else '.'
            raw.extend(_rgba(palette[ch]) if ch in palette and ch != '.' else (0, 0, 0, 0))

    def chunk(tag, data):
        c = struct.pack('>I', len(data)) + tag + data
        return c + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', ihdr) + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')


def write_png(path, rows, palette, size=16):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png_bytes(rows, palette, size))


def write_item(name, rows, palette=PALETTE, model=True, handheld=False):
    write_png(ASSETS / 'textures/item' / f'{name}.png', rows, palette)
    model_path = ASSETS / 'models/item' / f'{name}.json'
    if model and not model_path.exists():
        model_path.parent.mkdir(parents=True, exist_ok=True)
        parent = 'minecraft:item/handheld' if handheld else 'minecraft:item/generated'
        model_path.write_text(json.dumps({'parent': parent, 'textures': {'layer0': f'robotica:item/{name}'}}, indent=2) + '\n')


def write_block(name, rows, palette=PALETTE):
    write_png(ASSETS / 'textures/block' / f'{name}.png', rows, palette)


def diamond(radius=7, outline='k', light='M', mid='m', dark='n', glow='Y', glow_r=2):
    """16x16 symmetric gem, shaded top-left light / bottom-right dark."""
    rows = []
    for y in range(16):
        row = ''
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d > radius:
                row += '.'
            elif d == radius:
                row += outline
            elif d <= glow_r:
                row += glow
            elif x < 8 and y < 8:
                row += light
            elif x >= 8 and y >= 8:
                row += dark
            else:
                row += mid
        rows.append(row)
    return rows


def noise_fill(base_rows, chars, seed=1):
    """Deterministic speckle: replaces '?' in rows with one of chars."""
    out = []
    for y, row in enumerate(base_rows):
        s = ''
        for x, ch in enumerate(row):
            if ch == '?':
                h = (x * 73856093 ^ y * 19349663 ^ seed * 83492791) & 0xFFFF
                s += chars[h % len(chars)]
            else:
                s += ch
        out.append(s)
    return out


class Canvas:
    """Programmatic drawing, avoids hand-counting pixel strings. c = Canvas(); c.rect(...); c.rows()"""

    def __init__(self, size=16, fill='.'):
        self.size = size
        self.px = [[fill] * size for _ in range(size)]

    def set(self, x, y, ch):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = ch
        return self

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.size and 0 <= y < self.size else '.'

    def rect(self, x, y, w, h, ch):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, ch)
        return self

    def frame(self, x, y, w, h, ch):
        for xx in range(x, x + w):
            self.set(xx, y, ch).set(xx, y + h - 1, ch)
        for yy in range(y, y + h):
            self.set(x, yy, ch).set(x + w - 1, yy, ch)
        return self

    def bevel(self, x, y, w, h, light, dark):
        """Light top/left edge and dark bottom/right edge inside a box."""
        for xx in range(x, x + w):
            self.set(xx, y, light).set(xx, y + h - 1, dark)
        for yy in range(y, y + h):
            self.set(x, yy, light).set(x + w - 1, yy, dark)
        return self

    def shape(self, inside, fill):
        """inside(x, y) -> bool; fill(x, y) -> char."""
        for y in range(self.size):
            for x in range(self.size):
                if inside(x, y):
                    self.set(x, y, fill(x, y))
        return self

    def outline(self, ch='k'):
        """Adds an outline around every non-transparent pixel group (on transparent neighbours)."""
        solid = {(x, y) for y in range(self.size) for x in range(self.size) if self.px[y][x] != '.'}
        for (x, y) in list(solid):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if (nx, ny) not in solid and 0 <= nx < self.size and 0 <= ny < self.size:
                    self.px[ny][nx] = ch
        return self

    def edge(self, ch='k'):
        """Turns the outermost pixels of each solid group into the outline char (keeps size)."""
        solid = {(x, y) for y in range(self.size) for x in range(self.size) if self.px[y][x] != '.'}
        for (x, y) in solid:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in solid:
                    self.px[y][x] = ch
                    break
        return self

    def rows(self):
        return [''.join(r) for r in self.px]
