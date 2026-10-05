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


# =====================================================================================================================
# Shared material kit. One look for every module: light comes from the top-left, edges are bevelled (light top/left,
# dark bottom/right), metal is brushed, plates carry rivets, glow lives on a separate full-bright overlay layer.
#
# Helpers take a "ramp": a string of palette chars ordered dark -> light, normally 5 long:
#   r[0] deep shadow   r[1] shadow   r[2] mid   r[3] light   r[4] highlight
# MATERIALS holds the shared 5 step ramps; material('01234', 'copper') maps one onto any chars.
# =====================================================================================================================

BAYER4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))

MATERIALS = {
    'copper': ('#4E2812', '#8A4A22', '#C87533', '#E8A060', '#FFD3A2'),
    'brass': ('#5E4512', '#9C7A22', '#D4A73A', '#F2D27A', '#FFF4C8'),
    'gold': ('#6E4A08', '#B07E12', '#E8B530', '#FFE07A', '#FFFBE0'),
    'steel': ('#33393C', '#5A6266', '#8D9599', '#C4CBCE', '#F0F4F5'),
    'white': ('#7C878C', '#A9B3B7', '#D3DADD', '#ECF1F3', '#FFFFFF'),
    'dark': ('#121619', '#1F2427', '#30373B', '#4A5257', '#6E787D'),
    'iron': ('#3E4447', '#6B7376', '#A9B1B4', '#D4DADC', '#F6F9FA'),
    'magma': ('#3E0C04', '#7A2A0A', '#C23A12', '#FF7A2E', '#FFE08A'),
    'null': ('#140A2C', '#2A1560', '#5A33A0', '#A884F0', '#E6DAFF'),
    'teal': ('#0B302C', '#145247', '#2E8F7F', '#5AD6BE', '#D8FBFF'),
    'wood': ('#3A2412', '#5E3E22', '#8B5E34', '#B98550', '#DDB07A'),
    'cyan': ('#145A66', '#2FB8CC', '#5FE3F0', '#B5F6FB', '#F2FFFF'),
    'amber': ('#7A3A06', '#C46A0E', '#FFB21E', '#FFE08A', '#FFFBE6'),
    'red': ('#4A0E0A', '#8A1E18', '#C9302A', '#FF6B5E', '#FFD0C8'),
    'green': ('#0E3A1A', '#1E6B2E', '#3E9E3A', '#7FD068', '#D8FFC0'),
    'purple': ('#1E0F3A', '#3A1F6E', '#6A3FB0', '#A884F0', '#ECE2FF'),
    'slate': ('#151A26', '#232838', '#363D52', '#565D78', '#8890B0'),
}

# Per-face NeoForge data for full-bright overlay faces ("neoforge_data" in block model faces).
GLOW_FACE = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}


def grain(chars, name, amount=0.18):
    """Two subtle streak shades (darker, lighter) of a material's mid tone, for brushed(grain=...)."""
    lo, mid, hi = MATERIALS[name][1:4]
    return {chars[0]: mix(mid, lo, amount * 2), chars[1]: mix(mid, hi, amount * 2)}


def material(chars, name):
    """Maps a 5 char ramp onto MATERIALS[name], e.g. material('01234', 'copper') -> {'0': '#4E2812', ...}."""
    return {ch: col for ch, col in zip(chars, MATERIALS[name])}


def hash01(x, y, seed=0):
    """Deterministic white noise in [0, 1)."""
    h = (x * 374761393 + y * 668265263 + seed * 2246822519 + 0x9E3779B9) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65536.0


def smooth_noise(x, y, seed=0, cell=4, wrap=16):
    """Bilinear value noise in [0, 1) that tiles every `wrap` pixels (so block textures stay seamless)."""
    cells = max(1, wrap // cell)
    gx, gy = x / cell, y / cell
    x0, y0 = int(gx // 1), int(gy // 1)
    fx, fy = gx - x0, gy - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)

    def r(i, j):
        return hash01(i % cells, j % cells, seed)
    a = r(x0, y0) + (r(x0 + 1, y0) - r(x0, y0)) * fx
    b = r(x0, y0 + 1) + (r(x0 + 1, y0 + 1) - r(x0, y0 + 1)) * fx
    return a + (b - a) * fy


def fbm(x, y, seed=0, wrap=16):
    """Two octaves of smooth_noise, in [0, 1)."""
    return 0.62 * smooth_noise(x, y, seed, 8, wrap) + 0.38 * smooth_noise(x, y, seed + 7, 4, wrap)


def _hex(rgb):
    return '#' + ''.join(f'{max(0, min(255, round(v))):02X}' for v in rgb[:3])


def mix(a, b, t):
    """Linear blend of two '#RRGGBB' colours."""
    ra, rb = _rgba(a), _rgba(b)
    return _hex([ra[i] + (rb[i] - ra[i]) * t for i in range(3)])


def shift(color, amount):
    """Hue shifted shading: amount > 0 lightens towards warm cream, < 0 darkens towards cool indigo."""
    return mix(color, '#FFF6DC', amount) if amount >= 0 else mix(color, '#14122A', -amount)


def make_ramp(mid, steps=5, spread=0.62):
    """Ramp of `steps` colours (dark -> light) around `mid`, with hue shifted ends."""
    half = (steps - 1) / 2
    return tuple(shift(mid, spread * (i - half) / half) for i in range(steps))


def _kit_copy(self):
    c = Canvas(self.size)
    c.px = [row[:] for row in self.px]
    return c


def _kit_paste(self, other, ox=0, oy=0, skip='.'):
    for y in range(other.size):
        for x in range(other.size):
            ch = other.px[y][x]
            if ch != skip:
                self.set(x + ox, y + oy, ch)
    return self


def _kit_replace(self, src, dst, x=0, y=0, w=None, h=None):
    w = self.size if w is None else w
    h = self.size if h is None else h
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if self.get(xx, yy) in src:
                self.set(xx, yy, dst)
    return self


def _kit_line(self, x0, y0, x1, y1, ch):
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        self.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), ch)
    return self


def _kit_disc(self, cx, cy, r, ch):
    for y in range(self.size):
        for x in range(self.size):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                self.set(x, y, ch)
    return self


def _kit_ring(self, cx, cy, r0, r1, ch):
    for y in range(self.size):
        for x in range(self.size):
            if r0 <= ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 <= r1:
                self.set(x, y, ch)
    return self


def _kit_dither(self, x, y, w, h, ch, level, only=None):
    """Ordered (Bayer) dither: sets ch on a `level` (0..1) share of the box, optionally only over chars in `only`."""
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if BAYER4[yy % 4][xx % 4] < level * 16 and (only is None or self.get(xx, yy) in only):
                self.set(xx, yy, ch)
    return self


def _kit_vgradient(self, x, y, w, h, chars):
    """Dithered top -> bottom gradient through chars."""
    steps = len(chars) - 1
    for yy in range(h):
        t = yy / max(1, h - 1) * steps
        i = min(int(t), steps - 1) if steps else 0
        f = t - i
        for xx in range(w):
            ch = chars[i + 1] if steps and BAYER4[(y + yy) % 4][(x + xx) % 4] < f * 16 else chars[i]
            self.set(x + xx, y + yy, ch)
    return self


def _kit_speckle(self, x, y, w, h, chars, density=0.1, seed=0, only=None):
    """Sparse deterministic noise pixels picked from chars."""
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if hash01(xx, yy, seed) < density and (only is None or self.get(xx, yy) in only):
                self.set(xx, yy, chars[int(hash01(xx, yy, seed + 99) * len(chars))])
    return self


def _kit_brushed(self, x, y, w, h, r, seed=0, vertical=False, density=0.2, grain=None):
    """Brushed metal: short light and dark streaks along the grain, drawn only over the mid tone r[2].
    grain=(lo, hi) picks subtler streak chars than the ramp's shadow and light."""
    lo, hi = grain or (r[1], r[3])
    along, across = (h, w) if vertical else (w, h)
    for j in range(across):
        i = 0
        while i < along:
            if hash01(i, j, seed) < density:
                length = 2 + int(hash01(i, j, seed + 1) * 4)
                ch = hi if hash01(i, j, seed + 2) < 0.5 else lo
                for k in range(length):
                    if i + k < along:
                        px, py = (x + j, y + i + k) if vertical else (x + i + k, y + j)
                        if self.get(px, py) == r[2]:
                            self.set(px, py, ch)
                i += length + 2
            else:
                i += 1
    return self


def _kit_plate(self, x, y, w, h, r, seed=0, brushed=True, vertical=False, bevel=True, density=0.2, grain=None):
    """Bevelled metal plate: mid fill, brushed grain, light top/left edge, dark bottom/right edge."""
    self.rect(x, y, w, h, r[2])
    if brushed:
        self.brushed(x + 1, y + 1, w - 2, h - 2, r, seed, vertical, density, grain)
    if bevel:
        self.bevel(x, y, w, h, r[3], r[1])
        self.set(x, y, r[4]).set(x + w - 1, y + h - 1, r[0])
    return self


def _kit_recess(self, x, y, w, h, r, fill=None):
    """Sunken pocket: shadowed top/left inner edge, lit bottom/right lip, filled with `fill` (default r[1])."""
    self.rect(x, y, w, h, fill or r[1])
    self.bevel(x, y, w, h, r[0], r[3])
    return self


def _kit_rivet(self, x, y, r):
    """One pixel rivet head (highlight) with a shadow pixel to its lower right."""
    self.set(x, y, r[4])
    if self.get(x + 1, y + 1) != '.':
        self.set(x + 1, y + 1, r[1])
    return self


def _kit_rivets(self, x, y, w, h, r, inset=1):
    for rx, ry in ((x + inset, y + inset), (x + w - 2 - inset, y + inset), (x + inset, y + h - 2 - inset),
                   (x + w - 2 - inset, y + h - 2 - inset)):
        self.rivet(rx, ry, r)
    return self


def _kit_vents(self, x, y, w, h, r, pitch=2, vertical=False):
    """Louvred slots: dark slot r[0] followed by a lit lip r[3], every `pitch` pixels."""
    if vertical:
        for xx in range(x, x + w, pitch):
            self.rect(xx, y, 1, h, r[0])
            if pitch > 1 and xx + 1 < x + w:
                self.rect(xx + 1, y, 1, h, r[3])
    else:
        for yy in range(y, y + h, pitch):
            self.rect(x, yy, w, 1, r[0])
            if pitch > 1 and yy + 1 < y + h:
                self.rect(x, yy + 1, w, 1, r[3])
    return self


def _kit_grille(self, x, y, w, h, hole, lip):
    """Perforated grille: staggered holes with a lit lip below-right."""
    for yy in range(y, y + h, 2):
        for xx in range(x + ((yy - y) // 2) % 2, x + w, 2):
            self.set(xx, yy, hole)
            if xx + 1 < x + w and yy + 1 < y + h:
                self.set(xx + 1, yy + 1, lip)
    return self


def _kit_screen(self, x, y, w, h, r, glass='k', glare=None):
    """Recessed display: shadowed top/left frame, lit bottom/right lip, glass, optional glare pixel."""
    self.rect(x, y, w, h, glass)
    self.bevel(x, y, w, h, r[0], r[3])
    if glare:
        self.set(x + 1, y + 1, glare)
    return self


def _kit_hazard(self, x, y, w, h, a, b, period=4):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            self.set(xx, yy, a if (xx + yy) % period < period // 2 else b)
    return self


def _kit_mask(self, chars):
    """New canvas holding only the pixels whose char is in `chars`: the emissive overlay layer of a texture."""
    c = Canvas(self.size)
    for y in range(self.size):
        for x in range(self.size):
            if self.px[y][x] in chars:
                c.px[y][x] = self.px[y][x]
    return c


def _kit_draw(self, x, y, sprite):
    """Stamps a small sprite (list of strings, '.' = leave the pixel alone) with its top-left at x, y."""
    for dy, row in enumerate(sprite):
        for dx, ch in enumerate(row):
            if ch != '.':
                self.set(x + dx, y + dy, ch)
    return self


def _kit_flip_h(self):
    self.px = [row[::-1] for row in self.px]
    return self


def _kit_auto_shade(self, ramps, outline='k'):
    """Item shading in one pass: for each char with a (dark, light) pair, pixels that touch empty or outline space
    above or to the left turn light, pixels touching it below or to the right turn dark (top-left light)."""
    src = [row[:] for row in self.px]

    def open_(x, y):
        return not (0 <= x < self.size and 0 <= y < self.size) or src[y][x] in ('.', outline)
    for y in range(self.size):
        for x in range(self.size):
            ch = src[y][x]
            if ch in ramps:
                dark, light = ramps[ch]
                if open_(x, y - 1) or open_(x - 1, y):
                    self.px[y][x] = light
                elif open_(x, y + 1) or open_(x + 1, y):
                    self.px[y][x] = dark
    return self


for _name, _fn in list(globals().items()):
    if _name.startswith('_kit_'):
        setattr(Canvas, _name[5:], _fn)


def write_anim(kind, name, frames, palette, frametime=2, interpolate=False):
    """Animated texture: frames (Canvases or row lists) stacked vertically plus a .png.mcmeta.
    kind is 'block', 'item' or 'entity'."""
    rows = []
    for f in frames:
        rows.extend(f.rows() if isinstance(f, Canvas) else f)
    path = ASSETS / 'textures' / kind / f'{name}.png'
    write_png(path, rows, palette, size=None)
    meta = {'animation': {'frametime': frametime}}
    if interpolate:
        meta['animation']['interpolate'] = True
    pathlib.Path(str(path) + '.mcmeta').write_text(json.dumps(meta, indent=2) + '\n')


def write_still(kind, name, rows, palette):
    """Static texture that may replace an animated one (removes a stale .png.mcmeta)."""
    path = ASSETS / 'textures' / kind / f'{name}.png'
    write_png(path, rows.rows() if isinstance(rows, Canvas) else rows, palette)
    meta = pathlib.Path(str(path) + '.mcmeta')
    if meta.exists():
        meta.unlink()


def glow_cube(textures, faces, glow_faces, render_type='minecraft:cutout', parent='minecraft:block/block'):
    """Block model: a full cube (faces: dir -> texture var) plus a coplanar full-bright overlay cube
    (glow_faces: dir -> texture var). Both layers cull against neighbours like a normal cube."""
    model = {'parent': parent, 'render_type': render_type, 'textures': textures, 'elements': [
        {'from': [0, 0, 0], 'to': [16, 16, 16],
         'faces': {d: {'texture': t, 'cullface': d} for d, t in faces.items()}}]}
    if glow_faces:
        model['elements'].append({'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False,
                                  'faces': {d: {'texture': t, 'cullface': d, 'neoforge_data': dict(GLOW_FACE)}
                                            for d, t in glow_faces.items()}})
    return model
