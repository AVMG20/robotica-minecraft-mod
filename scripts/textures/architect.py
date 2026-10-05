"""Architect module textures: 4 styles x 6 roles (with random variants), the Architect Table and the builder drone.
Run: python3 scripts/textures/architect.py   (writes textures/block/*.png and textures/entity/builder_drone.png;
models, blockstates, recipes and lang are written by scripts/data/architect_*.py)

Walls, floors and roofs tile seamlessly: no frame around a block, seams are staggered or continuous lines, and
low-frequency noise wraps at 16 px. Each has 3 variants (<name>, <name>_2, <name>_3) that the blockstate mixes with
weights, so large surfaces never show a repeating stamp. Pillars have no joint at their ends, so stacked posts and
trim lines read as one beam. Light panels are a frame plus a diffuser; <style>_light_glow is the diffuser alone,
drawn as a full-bright overlay layer.

Style identities
  Timberframe  white plaster, oak boards and beams, clay tile roof, warm paper panels
  Copper Works copper plate courses with rivets, tread plate floor, standing seam roof, amber panels
  Steel Lab    white panels with staggered seams, grated floor, ribbed roof, cyan panels
  Null Spire   dark glossy panels with a teal seam line, polished tiles, scale roof, teal panels
"""
import math
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
from pixelart import ASSETS, Canvas, write_block, write_png  # noqa: E402

TIMBER = {
    '0': '#FAF7EC', '1': '#F2EEDF', '2': '#E8E2CF', '3': '#D9D1B9', '4': '#BFB59A',  # plaster
    '5': '#C9985C', '6': '#A97A44', '7': '#7C5430', '8': '#4E3219', '9': '#B88A52',  # oak
    'r': '#A5543A', 'R': '#8C4430', 's': '#5E2B1E', 't': '#BC6A4B', 'u': '#9A4C34',  # clay tiles
    'b': '#9C4A33', 'B': '#7E3A28',  # brick
    'm': '#6E7D3A', 'M': '#8A9A4A',  # moss
    'k': '#2A2623', 'f': '#F4B24A', 'g': '#FFD98A', 'h': '#FFF4D0', 'G': '#F8E3B0',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
COPPER = {
    '0': '#F8C894', '1': '#F0AE72', '2': '#E19A5C', '3': '#C87533', '4': '#A65E28', '5': '#7A421C', '9': '#5A2F14',
    '6': '#F2D27A', '7': '#C49A34', '8': '#7A5E18',  # brass
    'Z': '#5FB89A', 'z': '#3F8F74', 'Y': '#88CDB0',  # patina
    'f': '#FF9A1E', 'g': '#FFC24A', 'h': '#FFE9A8', 'k': '#2A1A10',
    'v': '#E4F6FB', 'V': '#A8D8E8',
}
STEEL = {
    '0': '#FFFFFF', '1': '#F3F7F8', '2': '#E4EBEE', '3': '#CDD5D9', '4': '#A3ACB1', '5': '#6E777C',
    'k': '#2A3033', 'K': '#171B1D', 'y': '#E8C440',
    'f': '#2FB8CC', 'g': '#7FEAF4', 'h': '#E2FDFF',
    'v': '#E4F6FB', 'V': '#9FDCEA',
}
NULL = {
    '0': '#5A5288', '1': '#443C6C', '2': '#2F2850', '3': '#231D3D', '4': '#18132B', '5': '#0E0B1A',
    'f': '#1E8F7F', 'g': '#2FD0B4', 'h': '#B8FFF0', 'k': '#0A0812',
    'v': '#2FD0B444', 'V': '#7FF0DA99', 'w': '#2FD0B422',
}


# ---------------------------------------------------------------- helpers

def n(x, y, s=1):
    """Deterministic hash noise 0..65535."""
    h = (x * 374761393 + y * 668265263 + s * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


def vnoise(x, y, seed, cell=4):
    """Smooth value noise that wraps at 16 px, 0..1."""
    g = 16 // cell

    def r(i, j):
        return n(i % g, j % g, seed) / 65535.0

    fx, fy = x / cell, y / cell
    i, j = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - i, fy - j
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = r(i, j) + (r(i + 1, j) - r(i, j)) * tx
    b = r(i, j + 1) + (r(i + 1, j + 1) - r(i, j + 1)) * tx
    return a + (b - a) * ty


def field(seed, octaves=((4, 0.6), (8, 0.4))):
    return lambda x, y: sum(vnoise(x, y, seed + k, cell) * w for k, (cell, w) in enumerate(octaves))


def tone(c, ramp, f, x0=0, y0=0, w=16, h=16, jitter=0.08, seed=5):
    """Fills a box from a 0..1 field mapped onto a ramp of chars (dark to light), with a little per-pixel jitter."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = f(x, y) + (n(x, y, seed) / 65535.0 - 0.5) * jitter * 2
            c.set(x, y, ramp[max(0, min(len(ramp) - 1, int(v * len(ramp))))])


def speckle(c, chars, every, seed=1, x0=0, y0=0, w=16, h=16):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            v = n(x, y, seed)
            if v % every == 0:
                c.set(x, y, chars[(v >> 4) % len(chars)])


def rot(rows):
    """Rotates 90 degrees clockwise (for previews of horizontal trims)."""
    size = len(rows)
    return [''.join(rows[size - 1 - x][y] for x in range(size)) for y in range(size)]


def glow_only(rows, glow_chars):
    """Keeps only the glowing pixels (the overlay layer of a light panel)."""
    return [''.join(ch if ch in glow_chars else '.' for ch in row) for row in rows]


def panel_light(c, frame, inner, ramp, x0=2, y0=2, size=12, fn=None):
    """Light panel: frame chars (light, mid, dark) 2 px wide, diffuser from ramp (edge to centre)."""
    light, mid, dark = frame
    c.rect(0, 0, 16, 16, mid)
    c.rect(0, 0, 16, 1, light).rect(0, 0, 1, 16, light)
    c.rect(0, 15, 16, 1, dark).rect(15, 0, 1, 16, dark)
    c.frame(1, 1, 14, 14, inner)
    for y in range(y0, y0 + size):
        for x in range(x0, x0 + size):
            if fn:
                v = fn(x, y)
            else:
                d = max(abs(x - 7.5), abs(y - 7.5)) / (size / 2)
                v = 1 - d
            c.set(x, y, ramp[max(0, min(len(ramp) - 1, int(v * len(ramp))))])


# ---------------------------------------------------------------- Timberframe

def timber_wall(v):
    c = Canvas()
    tone(c, '4332211', field(10 + v), jitter=0.07, seed=3 + v)
    speckle(c, '4', 41, 7 + v)
    speckle(c, '0', 53, 9 + v)
    if v == 2:  # hairline crack
        x = 9
        for y in range(3, 11):
            c.set(x, y, '4')
            if n(x, y, 4) % 3 == 0:
                x += 1 if n(x, y, 5) % 2 else -1
                c.set(x, y, '4')
    if v == 3:  # a patch where the plaster came off, bricks behind it
        for y in range(8, 15):
            for x in range(3, 13):
                edge = (y in (8, 14) and n(x, y, 2) % 3 == 0) or (x in (3, 12) and n(x, y, 3) % 2 == 0)
                if edge:
                    continue
                if y in (8, 14) or x in (3, 12):
                    c.set(x, y, '4')
                    continue
                mortar = y in (11,) or (y < 11 and x in (6, 10)) or (y > 11 and x in (4, 8))
                c.set(x, y, '4' if mortar else ('b' if n(x, y, 2) % 4 else 'B'))
    return c.rows()


def timber_floor(v):
    c = Canvas()
    seams = {1: (3, 11, 6, 14), 2: (9, 1, 13, 5), 3: (12, 6, 2, 9)}[v]
    for row in range(4):
        y = row * 4
        base = '6' if (row + v) % 2 else '9'
        tone(c, base * 5 + '5', field(20 + row + 4 * v, ((8, 1.0),)), 0, y, 16, 3, jitter=0.05, seed=row + 7 * v)
        c.rect(0, y + 3, 16, 1, '7')
        sx = seams[row]
        c.rect(sx, y, 1, 3, '8')
        c.set(sx, y + 3, '8')
        gx = n(row, 1, 30 + v) % 16  # one grain dash per board
        for k in range(4):
            c.set((gx + k) % 16, y + 1, '7' if base == '6' else '6')
    return c.rows()


def timber_roof(v):
    c = Canvas()
    for ri, y0 in enumerate(range(0, 16, 4)):
        shift = 4 if ri % 2 else 0
        for tx in range(-8, 16, 8):
            x0 = tx + shift
            k = n((x0 % 16) // 4, ri, 40 + v) % 7
            base = 'R' if (v >= 2 and k < 2) else 'u' if k == 3 else 'r'
            c.rect(x0, y0, 8, 4, base)
            c.rect(x0, y0, 8, 1, 't')
            c.rect(x0 + 1, y0 + 1, 2, 1, 't')
            c.rect(x0, y0 + 3, 8, 1, 's')
            c.rect(x0 + 7, y0, 1, 3, 'R')
            c.set(x0 + 7, y0 + 3, 's')
    if v == 3:  # moss on a few tiles
        for x, y in ((2, 6), (3, 6), (3, 5), (10, 14), (11, 14), (12, 13), (11, 13)):
            c.set(x, y, 'm' if (x + y) % 2 else 'M')
    return c.rows()


def timber_pillar_side():
    c = Canvas()
    tone(c, '7669955', lambda x, y: vnoise(x * 4, y, 50, 4) * 0.7 + 0.15, jitter=0.1, seed=51)
    for x in (4, 9, 12):
        for y in range(16):
            if n(x, y, 52) % 5:
                c.set(x, y, '7')
    c.rect(0, 0, 1, 16, '5').rect(1, 0, 1, 16, '9').rect(14, 0, 1, 16, '7').rect(15, 0, 1, 16, '8')
    c.set(6, 5, '8').set(6, 6, '7').set(10, 12, '8')  # knots
    return c.rows()


def timber_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '6')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, '5' if int(d * 1.3) % 3 == 0 else '9' if int(d * 1.3) % 3 == 1 else '6')
    c.frame(0, 0, 16, 16, '7')
    c.rect(7, 7, 2, 2, '7')
    return c.rows()


def timber_window():
    c = Canvas()
    c.frame(0, 0, 16, 16, '6')
    c.rect(0, 0, 16, 1, '9').rect(0, 15, 16, 1, '7')
    c.set(1, 1, 'V').set(2, 1, 'v')
    for ox, oy in ((3, 9), (9, 3)):
        c.set(ox, oy + 2, 'v').set(ox + 1, oy + 1, 'v').set(ox + 2, oy, 'v')
    c.set(12, 4, 'V').set(11, 5, 'V')
    return c.rows()


def timber_light():
    c = Canvas()
    panel_light(c, ('9', '6', '7'), '7', 'fGgGhh')
    # paper ribs: the panel reads as a shoji style lamp, not a picture of a lantern
    for i in (6, 10):
        c.rect(i - 1, 2, 1, 12, 'G').rect(2, i - 1, 12, 1, 'G')
    return c.rows()


# ---------------------------------------------------------------- Copper Works

def copper_wall(v):
    c = Canvas()
    tone(c, '43332', field(60 + v), jitter=0.12, seed=61 + v)
    for course, (y0, seam_x) in enumerate(((0, 3), (8, 11))):
        c.rect(0, y0, 16, 1, '1')          # lit top edge of the plate course
        c.rect(0, y0 + 7, 16, 1, '5')      # shadowed seam under it
        c.rect(seam_x, y0, 1, 7, '5').rect(seam_x + 1, y0, 1, 7, '2')
        for rx in range(seam_x + 3, seam_x + 16, 4):
            x = rx % 16
            if x in (seam_x, seam_x + 1):
                continue
            c.set(x, y0 + 2, '6').set(x, y0 + 3, '5')
    if v == 2:  # verdigris running down from a seam
        for x, top in ((5, 8), (6, 8), (13, 0), (14, 0)):
            for y in range(top, top + 3 + n(x, 1, 63) % 4):
                c.set(x, y, 'Z' if (x + y) % 3 else 'z')
            c.set(x, top + 1, 'Y')
    if v == 3:  # a darker heat-tinted plate with a brass hatch
        tone(c, '544', field(66), 4, 9, 6, 5, jitter=0.1, seed=67)
        c.frame(5, 10, 4, 4, '7').set(6, 11, '6')
    return c.rows()


def copper_floor(v):
    c = Canvas()
    tone(c, '443', field(70 + v), jitter=0.05, seed=71 + v)
    for t0 in (0, 8):  # 8x8 floor plates: a lit edge, a dark joint, a bolt in one corner
        c.rect(0, t0, 16, 1, '2').rect(t0, 0, 1, 16, '2')
        c.rect(0, t0 + 7, 16, 1, '5').rect(t0 + 7, 0, 1, 16, '5')
    for x0 in (0, 8):
        for y0 in (0, 8):
            c.set(x0 + 1, y0 + 1, '7').set(x0 + 2, y0 + 2, '5')
            if v == 2 and (x0, y0) == (8, 0):
                for k in range(2, 6):  # tread lugs on one plate
                    c.set(x0 + k, y0 + 7 - k, '2').set(x0 + k + 1, y0 + 7 - k, '5')
    if v == 3:
        for y in range(16):
            for x in range(16):
                if (x - 11) ** 2 + (y - 11) ** 2 < 6 and n(x, y, 74) % 3 and c.get(x, y) in '34':
                    c.set(x, y, 'z' if (x + y) % 2 else 'Z')
    return c.rows()


def copper_roof(v):
    c = Canvas()
    tone(c, '43332', lambda x, y: vnoise(x, y * 0.25, 80 + v, 4), jitter=0.06, seed=81 + v)
    streaks = {1: (), 2: (6, 7), 3: (2, 3, 10, 11, 14)}[v]
    for x in streaks:
        length = 6 + n(x, 0, 82 + v) % 10
        start = n(x, 1, 83 + v) % 16
        for k in range(length):
            y = (start + k) % 16
            c.set(x, y, 'Z' if k < length - 2 else 'z')
    for x in range(0, 16, 4):  # standing seams
        for y in range(16):
            patina = c.get(x + 1, y) in 'zZ'
            c.set(x, y, 'Y' if patina else '1')
            c.set(x + 1, y, 'z' if patina else '5')
    return c.rows()


def copper_pillar_side():
    c = Canvas()
    tone(c, '43322', lambda x, y: vnoise(x * 3, y, 90, 4) * 0.5 + 0.3, jitter=0.08, seed=91)
    c.rect(0, 0, 1, 16, '1').rect(1, 0, 2, 16, '2').rect(13, 0, 2, 16, '4').rect(15, 0, 1, 16, '5')
    c.rect(3, 0, 1, 16, '7').rect(12, 0, 1, 16, '8')
    for y in range(1, 16, 4):  # brass rivets along both edges
        c.set(1, y, '6').set(1, y + 1, '7').set(14, y, '6').set(14, y + 1, '8')
    return c.rows()


def copper_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, '7' if 6.2 < d < 7.4 else '3' if d < 6.2 else '5')
    c.rect(6, 6, 4, 4, '4').rect(7, 7, 2, 2, '6')
    return c.rows()


def copper_window():
    c = Canvas()
    c.frame(0, 0, 16, 16, '3')
    c.rect(0, 0, 16, 1, '2').rect(0, 15, 16, 1, '5')
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(x, y, '7')
    for ox, oy in ((3, 9), (9, 3)):
        c.set(ox, oy + 2, 'v').set(ox + 1, oy + 1, 'v').set(ox + 2, oy, 'v')
    c.set(12, 4, 'V').set(11, 5, 'V')
    return c.rows()


def copper_light():
    c = Canvas()
    panel_light(c, ('6', '7', '8'), '5', 'fggghh')
    for y in (5, 8, 11):  # fine grill bars across the amber glass
        c.rect(2, y, 12, 1, 'f')
    return c.rows()


# ---------------------------------------------------------------- Steel Lab

def steel_wall(v):
    c = Canvas()
    tone(c, '2221', field(100 + v), jitter=0.06, seed=101 + v)
    c.rect(0, 10, 16, 1, '3').rect(0, 11, 16, 1, '0')   # one horizontal seam per block, continuous
    c.rect(4, 0, 1, 10, '3').rect(5, 0, 1, 10, '0')      # staggered vertical seams
    c.rect(12, 11, 1, 5, '3').rect(13, 11, 1, 5, '0')
    c.set(2, 8, '4').set(7, 8, '4').set(10, 13, '4').set(15, 13, '4')  # fasteners
    if v == 2:  # vent grille
        for y in (2, 4, 6):
            c.rect(8, y, 6, 1, '4').rect(8, y + 1, 6, 1, '1')
    if v == 3:  # status light and a label strip
        c.rect(7, 13, 3, 1, '4').rect(1, 13, 3, 1, '3')
        c.rect(8, 3, 2, 2, 'k').set(8, 3, 'g').set(9, 3, 'f')
    return c.rows()


def steel_floor(v):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            bx, by = x % 4 < 2, y % 4 < 2
            if bx or by:
                c.set(x, y, '2' if (x % 4 == 0 or y % 4 == 0) else '4' if (x % 4 == 1 and y % 4 == 1) else '3')
            else:
                c.set(x, y, 'K' if n(x, y, 110) % 3 else 'k')
    if v == 2:  # a solid access plate
        c.rect(4, 4, 8, 8, '3').rect(4, 4, 8, 1, '2').rect(4, 4, 1, 8, '2').rect(4, 11, 8, 1, '4').rect(11, 4, 1, 8, '4')
        c.set(5, 5, '5').set(10, 5, '5').set(5, 10, '5').set(10, 10, '5')
    if v == 3:  # round drain
        for y in range(16):
            for x in range(16):
                d = (x - 7.5) ** 2 + (y - 7.5) ** 2
                if d < 13:
                    c.set(x, y, '4' if d > 8 else 'K' if (x + y) % 2 else 'k')
    return c.rows()


def steel_roof(v):
    c = Canvas()
    tone(c, '332', field(120 + v), jitter=0.06, seed=121 + v)
    for x in range(0, 16, 4):
        c.rect(x, 0, 1, 16, '1').rect(x + 1, 0, 1, 16, '4')
    if v >= 2:
        for x in range(2, 16, 4):
            c.set(x, 3 if v == 2 else 11, '5')
    return c.rows()


def steel_pillar_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, '2')
    c.rect(0, 0, 1, 16, '1').rect(1, 0, 3, 16, '1').rect(12, 0, 3, 16, '3').rect(15, 0, 1, 16, '4')
    c.rect(6, 0, 4, 16, 'k').rect(7, 0, 2, 16, 'f').rect(7, 0, 1, 16, 'g')
    return c.rows()


def steel_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '3')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, '1' if d > 6.6 else '2' if d > 3.0 else 'k' if d > 1.6 else 'g')
    c.frame(0, 0, 16, 16, '4')
    return c.rows()


def steel_window():
    c = Canvas()
    c.frame(0, 0, 16, 16, '1')
    c.rect(0, 15, 16, 1, '3').rect(15, 0, 1, 16, '3')
    for ox, oy in ((3, 9), (9, 3)):
        c.set(ox, oy + 2, 'v').set(ox + 1, oy + 1, 'v').set(ox + 2, oy, 'g')
    c.set(12, 4, 'V').set(11, 5, 'V')
    return c.rows()


def steel_light():
    c = Canvas()
    panel_light(c, ('1', '2', '3'), '4', 'fgggh')
    return c.rows()


# ---------------------------------------------------------------- Null Spire

def null_wall(v):
    c = Canvas()
    tone(c, '43332', field(130 + v), jitter=0.1, seed=131 + v)
    for y in range(16):  # glossy diagonal sheen, wraps around the block
        for x in range(16):
            if (x + y) % 16 in (3, 4) and y < 10:
                c.set(x, y, '2' if c.get(x, y) != '2' else '1')
    c.rect(0, 10, 16, 1, '5').rect(0, 11, 16, 1, 'f').rect(0, 12, 16, 1, '5')  # continuous glow seam
    for x in range(1, 16, 5):
        c.set(x, 11, 'g')
    c.rect(6, 0, 1, 10, '5').rect(13, 13, 1, 3, '5')
    if v == 2:  # glyph
        for x, y in ((9, 3), (10, 3), (11, 3), (10, 4), (10, 5), (9, 6), (11, 6)):
            c.set(x, y, 'f')
    if v == 3:  # node on the seam
        c.rect(2, 10, 3, 3, 'g').set(3, 11, 'h')
    return c.rows()


def null_floor(v):
    c = Canvas()
    tone(c, '4332', field(140 + v), jitter=0.12, seed=141 + v)
    for i in range(16):
        c.set(i, 7, '5').set(7, i, '5')
        c.set(i, 15, '5').set(15, i, '5')
    for x, y in ((7, 7), (15, 15), (7, 15), (15, 7)):
        c.set(x, y, 'f')
    for x0, y0 in ((0, 0), (8, 8)):
        c.set(x0 + 1, y0 + 1, '1').set(x0 + 2, y0 + 1, '1').set(x0 + 1, y0 + 2, '1')
    if v == 2:
        c.rect(10, 2, 3, 3, 'f').set(11, 3, 'g')
    if v == 3:
        for i in range(1, 6):
            c.set(i, 9 + (i % 2), 'f')
    return c.rows()


def null_roof(v):
    c = Canvas()
    for ri, y0 in enumerate(range(0, 16, 4)):
        shift = 2 if ri % 2 else 0
        for tx in range(-4, 16, 4):
            x0 = tx + shift
            c.rect(x0, y0, 4, 4, '3' if n((x0 % 16) // 2, ri, 150 + v) % 4 else '2')
            c.rect(x0, y0 + 3, 4, 1, '5')
            c.set(x0 + 1, y0, '1')
            c.set(x0 + 3, y0 + 1, '4').set(x0 + 3, y0 + 2, '4')
    if v >= 2:
        c.set(5, 6 if v == 2 else 10, 'f').set(13, 2 if v == 2 else 14, 'f')
    return c.rows()


def null_pillar_side():
    c = Canvas()
    tone(c, '433', lambda x, y: vnoise(x * 2, y, 160, 4) * 0.6 + 0.2, jitter=0.08, seed=161)
    c.rect(0, 0, 1, 16, '1').rect(1, 0, 1, 16, '2').rect(14, 0, 1, 16, '4').rect(15, 0, 1, 16, '5')
    c.rect(6, 0, 4, 16, '5').rect(7, 0, 2, 16, 'f').rect(7, 0, 1, 16, 'g')
    return c.rows()


def null_pillar_end():
    c = Canvas()
    c.rect(0, 0, 16, 16, '4')
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            c.set(x, y, '2' if d > 6.4 else '4' if d > 3.2 else 'f' if d > 1.8 else 'h')
    c.frame(0, 0, 16, 16, '5')
    return c.rows()


def null_window():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'w')
    for i in range(16):
        if (i * 3) % 7 == 0:
            c.set(i, (i * 5) % 16, 'v')
    c.frame(0, 0, 16, 16, '3')
    c.rect(0, 0, 16, 1, 'f')
    for ox, oy in ((3, 9), (9, 3)):
        c.set(ox, oy + 2, 'V').set(ox + 1, oy + 1, 'V').set(ox + 2, oy, 'V')
    return c.rows()


def null_light():
    c = Canvas()
    panel_light(c, ('2', '3', '5'), '5', 'fggghh',
                fn=lambda x, y: 1 - (abs(x - 7.5) + abs(y - 7.5)) / 12)
    return c.rows()


# ---------------------------------------------------------------- Architect Table

TABLE = {
    'i': '#8D969B', 'I': '#C2CBCF', 'j': '#5A6367', 'k': '#2A3033',
    'c': '#C87533', 'C': '#E8A060', 'd': '#8A4A22',
    'b': '#2B5C8F', 'B': '#3E78B4', 'w': '#E6F0FF', 'y': '#5FE3F0', 'Y': '#D8FBFF',
    'o': '#A97A44', 'O': '#C9985C',
}


def table_top():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'j').bevel(0, 0, 16, 16, 'I', 'k')
    c.rect(1, 1, 14, 14, 'c').bevel(1, 1, 14, 14, 'C', 'd')
    c.rect(2, 2, 12, 12, 'b')
    for i in range(2, 14, 3):
        c.rect(i, 2, 1, 12, 'B').rect(2, i, 12, 1, 'B')
    # 5x5 plot grid with the table plot in the middle
    for gy in range(5):
        for gx in range(5):
            x, y = 3 + gx * 2, 3 + gy * 2
            c.rect(x, y, 2, 2, 'w' if (gx, gy) != (2, 2) else 'y')
            c.rect(x, y, 1, 1, 'b' if (gx, gy) != (2, 2) else 'Y')
    c.set(2, 2, 'I')
    return c.rows()


def table_side():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'i').bevel(0, 0, 16, 16, 'I', 'j')
    c.rect(0, 0, 16, 3, 'c').bevel(0, 0, 16, 3, 'C', 'd')
    c.rect(2, 5, 12, 9, 'j').frame(2, 5, 12, 9, 'k')
    c.rect(3, 6, 10, 7, 'b')
    c.rect(3, 6, 10, 1, 'O').rect(3, 6, 1, 7, 'O')
    c.rect(6, 8, 5, 3, 'w').frame(6, 8, 5, 3, 'w').rect(7, 9, 3, 1, 'b')
    c.set(12, 12, 'y').set(11, 12, 'B')
    for x in (1, 14):
        c.set(x, 4, 'I').set(x, 14, 'j')
    return c.rows()


def table_bottom():
    c = Canvas()
    c.rect(0, 0, 16, 16, 'j').bevel(0, 0, 16, 16, 'i', 'k')
    c.rect(2, 2, 12, 12, 'i').bevel(2, 2, 12, 12, 'I', 'j')
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(x, y, 'k')
    return c.rows()


# ---------------------------------------------------------------- builder drone (32x32 entity texture)

DRONE = {'a': '#4D5558', 'A': '#8D969B', 'D': '#2A3033', 'e': '#5FE3F0', 'E': '#D8FBFF', 'c': '#C87533', 'C': '#E8A060'}


def drone_texture():
    c = Canvas(32)
    # body 6x6x6 at (0,0): top (6,0), bottom (12,0), left (0,6), front (6,6), right (12,6), back (18,6)
    for (x, y) in ((6, 0), (12, 0), (0, 6), (6, 6), (12, 6), (18, 6)):
        c.rect(x, y, 6, 6, 'a').bevel(x, y, 6, 6, 'A', 'D')
    c.rect(8, 8, 2, 2, 'e').set(8, 8, 'E')  # eye on the front face
    c.rect(7, 11, 4, 1, 'c')
    c.rect(2, 2, 2, 2, 'c')
    # rotor 4x0.5x4 at (0,14)
    c.rect(0, 14, 16, 6, 'D')
    c.rect(4, 14, 4, 4, 'A').rect(0, 18, 16, 1, 'a')
    return c.rows()


STYLES = {
    'timberframe': (TIMBER, timber_wall, timber_floor, timber_roof, timber_pillar_side, timber_pillar_end, timber_window, timber_light, 'fgGh'),
    'copper_works': (COPPER, copper_wall, copper_floor, copper_roof, copper_pillar_side, copper_pillar_end, copper_window, copper_light, 'fgh'),
    'steel_lab': (STEEL, steel_wall, steel_floor, steel_roof, steel_pillar_side, steel_pillar_end, steel_window, steel_light, 'fgh'),
    'null_spire': (NULL, null_wall, null_floor, null_roof, null_pillar_side, null_pillar_end, null_window, null_light, 'fgh'),
}
VARIANTS = 3


def variant_name(base, v):
    return base if v == 1 else f'{base}_{v}'


def main():
    for style, (pal, wall, floor, roof, pside, pend, window, light, glow) in STYLES.items():
        for v in range(1, VARIANTS + 1):
            write_block(variant_name(f'{style}_wall', v), wall(v), pal)
            write_block(variant_name(f'{style}_floor', v), floor(v), pal)
            write_block(variant_name(f'{style}_roof', v), roof(v), pal)
        write_block(f'{style}_pillar', pside(), pal)
        write_block(f'{style}_pillar_top', pend(), pal)
        write_block(f'{style}_window', window(), pal)
        rows = light()
        write_block(f'{style}_light', rows, pal)
        write_block(f'{style}_light_glow', glow_only(rows, glow), pal)
    write_block('architect_table_top', table_top(), TABLE)
    write_block('architect_table_side', table_side(), TABLE)
    write_block('architect_table_bottom', table_bottom(), TABLE)
    write_png(ASSETS / 'textures/entity/builder_drone.png', drone_texture(), DRONE, size=32)


if __name__ == '__main__':
    main()
